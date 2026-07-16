import { Client } from "../frontend/node_modules/@stomp/stompjs/esm6/index.js";

const baseUrl = process.env.LBN_SMOKE_HTTP || "http://127.0.0.1:8080";
const wsUrl = process.env.LBN_SMOKE_WS || "ws://127.0.0.1:8080/ws/chat";
const accountA = {
  username: process.env.LBN_SMOKE_USER_A || "mcclellan",
  password: process.env.LBN_SMOKE_PASSWORD_A || "lbn123456",
};
const accountB = {
  username: process.env.LBN_SMOKE_USER_B || "ervin",
  password: process.env.LBN_SMOKE_PASSWORD_B || "lbn123456",
};

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function request(path, { method = "GET", token, body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const payload = await response.json();
  if (!response.ok || payload.code !== 0) {
    throw new Error(`${method} ${path}: ${payload.message || response.status}`);
  }
  return payload.data;
}

async function login(account) {
  const data = await request("/api/auth/login", { method: "POST", body: account });
  assert(data?.token && data?.user?.id, `Login did not return a token for ${account.username}`);
  return data;
}

async function ensureFriendship(a, b) {
  const status = await request(`/api/friends/${b.user.id}/status`, { token: a.token });
  if (status.status === "ACCEPTED") return;
  if (status.status === "BLOCKED" || status.status === "BLOCKED_BY_PEER") {
    throw new Error("Smoke-test users have blocked each other; unblock them before testing");
  }
  if (status.status === "PENDING") {
    const recipient = Number(status.requesterId) === Number(a.user.id) ? b : a;
    await request(`/api/friends/requests/${status.id}/accept`, { method: "POST", token: recipient.token });
    return;
  }
  await request("/api/friends/requests", {
    method: "POST",
    token: a.token,
    body: { targetUserId: b.user.id, message: "Automated real-time smoke test" },
  });
  const incoming = await request("/api/friends/requests/incoming", { token: b.token });
  const relation = incoming.find((item) => Number(item.peer?.id) === Number(a.user.id));
  assert(relation, "Friend request was not visible to the recipient");
  await request(`/api/friends/requests/${relation.id}/accept`, { method: "POST", token: b.token });
}

function connect(token) {
  const listeners = new Map();
  let client;
  const connected = new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("STOMP connection timed out")), 10000);
    client = new Client({
      webSocketFactory: () => new WebSocket(wsUrl),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 0,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
      onConnect: () => {
        clearTimeout(timeout);
        ["messages", "friend-events", "read-events", "typing"].forEach((destination) => {
          client.subscribe(`/user/queue/${destination}`, (frame) => {
            const payload = JSON.parse(frame.body);
            for (const listener of listeners.get(destination) || []) listener(payload);
          });
        });
        resolve();
      },
      onStompError: (frame) => reject(new Error(frame.headers?.message || "STOMP broker error")),
      onWebSocketError: () => reject(new Error("WebSocket connection failed")),
    });
    client.activate();
  });
  return {
    connected,
    wait(destination, predicate, timeoutMs = 12000) {
      return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => {
          listeners.set(destination, (listeners.get(destination) || []).filter((fn) => fn !== receive));
          reject(new Error(`Timed out waiting for ${destination}`));
        }, timeoutMs);
        const receive = (payload) => {
          if (!predicate(payload)) return;
          clearTimeout(timeout);
          listeners.set(destination, (listeners.get(destination) || []).filter((fn) => fn !== receive));
          resolve(payload);
        };
        listeners.set(destination, [...(listeners.get(destination) || []), receive]);
      });
    },
    close: () => client?.deactivate(),
  };
}

const a = await login(accountA);
const b = await login(accountB);
assert(Number(a.user.id) !== Number(b.user.id), "Smoke test requires two different users");
await ensureFriendship(a, b);
const conversation = await request(`/api/chat/conversations/private/${b.user.id}`, { method: "POST", token: a.token });

const socketA = connect(a.token);
const socketB = connect(b.token);
try {
  await Promise.all([socketA.connected, socketB.connected]);
  const clientMessageId = `smoke-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  const content = `real-time-smoke-${new Date().toISOString()}`;
  const receiveMessage = socketB.wait("messages", (event) => event.message?.clientMessageId === clientMessageId);
  const sent = await request(`/api/chat/conversations/${conversation.id}/messages`, {
    method: "POST",
    token: a.token,
    body: { clientMessageId, content, replyToId: null },
  });
  const messageEvent = await receiveMessage;
  assert(messageEvent.message.id === sent.id, "WebSocket message id differs from persisted message id");
  assert(messageEvent.message.content === content, "WebSocket message content differs from persisted content");

  const receiveRead = socketA.wait("read-events", (event) => Number(event.conversationId) === Number(conversation.id));
  await request(`/api/chat/conversations/${conversation.id}/read`, {
    method: "PUT",
    token: b.token,
    body: { lastMessageId: sent.id },
  });
  const readEvent = await receiveRead;
  assert(Number(readEvent.lastReadMessageId) >= Number(sent.id), "Read cursor did not reach the sent message");
  console.log(`PASS conversation=${conversation.id} message=${sent.id} sender=${a.user.id} recipient=${b.user.id}`);
} finally {
  await Promise.allSettled([socketA.close(), socketB.close()]);
}
