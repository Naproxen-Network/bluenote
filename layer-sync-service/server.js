// 小蓝书 — Node.js Cross-Layer Sync Service
// -----------------------------------------------------------------------------
// Role in the multi-layer hypergraph design:
//   The 小蓝书 (bills) layer is influenced by another network layer. In the real
//   dataset that other layer is the US-Senate *committee* layer (the analogue of
//   the TikTok / Telegram layers in the spec). This service watches that layer,
//   emits "committee changed" events into RabbitMQ, and pushes the same events to
//   connected browsers over WebSocket so the Discover feed updates in real time.
//
// Technologies exercised here: Node.js, Express (REST), ws (WebSocket),
// amqplib (RabbitMQ producer), nacos (service registration into Spring Cloud).
import express from "express";
import cors from "cors";
import { WebSocketServer } from "ws";
import amqp from "amqplib";
import { createServer } from "http";
import { readFileSync } from "fs";
import { fileURLToPath } from "url";
import { dirname, join } from "path";

const __dirname = dirname(fileURLToPath(import.meta.url));

const PORT = process.env.PORT || 9099;
const RABBIT_URL = process.env.RABBIT_URL || "amqp://guest:guest@127.0.0.1:5672";
const NACOS_ADDR = process.env.NACOS_ADDR || "127.0.0.1:8848";
const EXCHANGE = "lbn.layer.exchange";
const ROUTING_KEY = "committee.changed";
const STREAM_INTERVAL_MS = 8000;

// ---- load the simulated committee-change stream produced by prepare_data.py ----
let EVENTS = [];
try {
  EVENTS = JSON.parse(
    readFileSync(join(__dirname, "..", "data", "generated", "committee_events.json"), "utf-8")
  );
  console.log(`[layer-sync] loaded ${EVENTS.length} committee events`);
} catch (e) {
  console.warn("[layer-sync] committee_events.json not found, using synthetic events");
  EVENTS = Array.from({ length: 20 }, (_, i) => ({
    seq: i + 1,
    billsUserId: 1 + (i % 100),
    action: ["JOIN", "LEAVE", "CHAIR"][i % 3],
    weightDelta: 0.5,
  }));
}

const recent = [];
let cursor = 0;
let channel = null;
let published = 0;

// ---- RabbitMQ producer ----
async function connectRabbit() {
  for (let attempt = 1; attempt <= 30; attempt++) {
    try {
      const conn = await amqp.connect(RABBIT_URL);
      channel = await conn.createChannel();
      await channel.assertExchange(EXCHANGE, "topic", { durable: true });
      conn.on("close", () => {
        console.warn("[layer-sync] rabbit connection closed, reconnecting...");
        channel = null;
        setTimeout(connectRabbit, 3000);
      });
      console.log("[layer-sync] connected to RabbitMQ");
      return;
    } catch (e) {
      console.log(`[layer-sync] rabbit connect retry ${attempt}: ${e.message}`);
      await new Promise((r) => setTimeout(r, 3000));
    }
  }
  console.error("[layer-sync] could not connect to RabbitMQ; REST/WS still available");
}

function emit(event) {
  const enriched = { ...event, ts: Date.now(), source: "committee-layer" };
  if (channel) {
    try {
      channel.publish(EXCHANGE, ROUTING_KEY, Buffer.from(JSON.stringify(enriched)), {
        contentType: "application/json",
      });
      published++;
    } catch (e) {
      console.warn("[layer-sync] publish failed:", e.message);
    }
  }
  recent.unshift(enriched);
  if (recent.length > 50) recent.pop();
  broadcast(enriched);
  return enriched;
}

// ---- periodic streaming of the committee layer ----
setInterval(() => {
  if (EVENTS.length === 0) return;
  const ev = EVENTS[cursor % EVENTS.length];
  cursor++;
  const out = emit(ev);
  console.log(`[layer-sync] streamed committee change user=${out.billsUserId} action=${out.action}`);
}, STREAM_INTERVAL_MS);

// ---- HTTP + WebSocket ----
const app = express();
app.use(cors());
app.use(express.json());

app.get("/api/layer/status", (_req, res) => {
  res.json({
    code: 0,
    message: "ok",
    data: {
      service: "lbn-layer-sync",
      streaming: true,
      intervalMs: STREAM_INTERVAL_MS,
      totalEvents: EVENTS.length,
      published,
      wsClients: wss ? wss.clients.size : 0,
      recent: recent.slice(0, 10),
    },
  });
});

// manual trigger of a layer shift (used by the admin console / demo button)
app.post("/api/layer/trigger", (req, res) => {
  const body = req.body || {};
  const base =
    EVENTS.length > 0 ? EVENTS[Math.floor(Math.random() * EVENTS.length)] : { billsUserId: 1 };
  const ev = {
    seq: -1,
    billsUserId: body.billsUserId ?? base.billsUserId,
    action: body.action ?? "CHAIR",
    weightDelta: body.weightDelta ?? 1.0,
    manual: true,
  };
  const out = emit(ev);
  res.json({ code: 0, message: "ok", data: out });
});

app.get("/api/layer/recent", (_req, res) => {
  res.json({ code: 0, message: "ok", data: recent });
});

const server = createServer(app);
const wss = new WebSocketServer({ server, path: "/ws" });
wss.on("connection", (ws) => {
  ws.send(JSON.stringify({ type: "hello", recent: recent.slice(0, 10) }));
});
function broadcast(event) {
  const msg = JSON.stringify({ type: "committee.changed", event });
  wss.clients.forEach((c) => {
    if (c.readyState === 1) c.send(msg);
  });
}

// ---- register into Nacos so it appears alongside the Spring Cloud services ----
async function registerNacos() {
  try {
    const { NacosNamingClient } = await import("nacos");
    const client = new NacosNamingClient({
      serverList: NACOS_ADDR,
      namespace: "public",
      logger: console,
    });
    await client.ready();
    await client.registerInstance("lbn-layer-sync", { ip: "127.0.0.1", port: Number(PORT) });
    console.log("[layer-sync] registered into Nacos as lbn-layer-sync");
  } catch (e) {
    console.warn("[layer-sync] Nacos registration skipped:", e.message);
  }
}

server.listen(PORT, async () => {
  console.log(`[layer-sync] HTTP+WS listening on http://127.0.0.1:${PORT}`);
  await connectRabbit();
  await registerNacos();
});
