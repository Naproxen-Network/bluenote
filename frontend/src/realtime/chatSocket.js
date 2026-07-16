import { Client } from "@stomp/stompjs";
import { getSessionToken } from "../authSession";

const destinations = ["messages", "friend-events", "read-events", "typing"];

let client = null;
let callbacks = {};

function websocketUrl() {
  const scheme = window.location.protocol === "https:" ? "wss" : "ws";
  return `${scheme}://${window.location.host}/ws/chat`;
}

function decode(frame) {
  try {
    return JSON.parse(frame.body);
  } catch {
    return null;
  }
}

export function connectChatSocket(handlers = {}) {
  callbacks = handlers;
  const token = getSessionToken();
  if (!token) return;
  if (client?.active) return;

  client = new Client({
    brokerURL: websocketUrl(),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    connectionTimeout: 10000,
    debug: () => {},
    onConnect: () => {
      destinations.forEach((name) => {
        client.subscribe(`/user/queue/${name}`, (frame) => {
          const payload = decode(frame);
          if (payload) callbacks[name]?.(payload);
        });
      });
      callbacks.connected?.(true);
    },
    onDisconnect: () => callbacks.connected?.(false),
    onWebSocketClose: () => callbacks.connected?.(false),
    onStompError: (frame) => callbacks.error?.(frame.headers?.message || "实时连接发生错误"),
  });
  client.activate();
}

export function disconnectChatSocket() {
  const activeClient = client;
  client = null;
  callbacks = {};
  if (activeClient?.active) activeClient.deactivate();
}

export function publishTyping(conversationId, targetUserId, typing) {
  if (!client?.connected) return false;
  client.publish({
    destination: "/app/chat/typing",
    body: JSON.stringify({ conversationId, targetUserId, typing }),
  });
  return true;
}
