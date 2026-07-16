import { Client } from "../frontend/node_modules/@stomp/stompjs/esm6/index.js";

const baseUrl = process.env.LBN_ACCEPTANCE_HTTP || "http://127.0.0.1:8080";
const wsUrl = process.env.LBN_ACCEPTANCE_WS || "ws://127.0.0.1:8080/ws/chat";
const password = process.env.LBN_ACCEPTANCE_PASSWORD || "lbn123456";
const accounts = {
  sender: { username: process.env.LBN_ACCEPTANCE_USER_A || "mcclellan", password },
  observer: { username: process.env.LBN_ACCEPTANCE_USER_B || "ervin", password },
  recipient: { username: process.env.LBN_ACCEPTANCE_USER_C || "hruska", password },
  admin: {
    username: process.env.LBN_ACCEPTANCE_ADMIN || "admin",
    password: process.env.LBN_ACCEPTANCE_ADMIN_PASSWORD || "admin123",
  },
};

let checks = 0;

function assert(condition, message) {
  if (!condition) throw new Error(message);
  checks += 1;
}

async function rawRequest(path, { method = "GET", token, body, headers = {} } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  let payload;
  try {
    payload = await response.json();
  } catch {
    payload = { code: response.status, message: await response.text() };
  }
  return { response, payload };
}

async function request(path, options = {}) {
  const { response, payload } = await rawRequest(path, options);
  if (!response.ok || payload.code !== 0) {
    throw new Error(`${options.method || "GET"} ${path}: HTTP ${response.status}, ${payload.message || "request failed"}`);
  }
  return payload.data;
}

async function expectForbidden(path, options = {}) {
  const { response, payload } = await rawRequest(path, options);
  assert(response.status === 403 || payload.code === 403,
    `${options.method || "GET"} ${path} should be forbidden, got HTTP ${response.status}/code ${payload.code}`);
}

async function login(account, admin = false) {
  const data = await request(admin ? "/api/auth/admin/login" : "/api/auth/login", {
    method: "POST",
    body: account,
  });
  assert(Boolean(data?.token), `Login did not return a token for ${account.username}`);
  if (!admin) assert(Boolean(data?.user?.id), `Login did not return a user for ${account.username}`);
  return data;
}

async function normalizeNoFriendship(a, b) {
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const status = await request(`/api/friends/${b.user.id}/status`, { token: a.token });
    if (["NONE", "DELETED", "REJECTED", "CANCELLED"].includes(status.status)) return;
    if (status.status === "BLOCKED") {
      await request(`/api/friends/${b.user.id}/block`, { method: "DELETE", token: a.token });
    } else if (status.status === "BLOCKED_BY_PEER") {
      await request(`/api/friends/${a.user.id}/block`, { method: "DELETE", token: b.token });
    } else if (status.status === "ACCEPTED") {
      await request(`/api/friends/${b.user.id}`, { method: "DELETE", token: a.token });
    } else if (status.status === "PENDING") {
      const requesterIsA = Number(status.requesterId) === Number(a.user.id);
      await request(`/api/friends/requests/${status.id}/${requesterIsA ? "cancel" : "reject"}`, {
        method: "POST",
        token: requesterIsA ? a.token : a.token,
      });
    } else {
      throw new Error(`Unsupported friendship status: ${status.status}`);
    }
  }
  throw new Error("Could not normalize the acceptance-test friendship state");
}

async function establishFriendship(a, b) {
  await request("/api/friends/requests", {
    method: "POST",
    token: a.token,
    body: { targetUserId: b.user.id, message: "Automated acceptance test" },
  });
  const incoming = await request("/api/friends/requests/incoming", { token: b.token });
  const relation = incoming.find((item) => Number(item.peer?.id) === Number(a.user.id));
  assert(Boolean(relation), "The recipient could not see the friend request");
  await request(`/api/friends/requests/${relation.id}/accept`, { method: "POST", token: b.token });
  const status = await request(`/api/friends/${b.user.id}/status`, { token: a.token });
  assert(status.status === "ACCEPTED", "Friendship was not accepted");
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
        const receive = (payload) => {
          if (!predicate(payload)) return;
          clearTimeout(timeout);
          listeners.set(destination, (listeners.get(destination) || []).filter((fn) => fn !== receive));
          resolve(payload);
        };
        const timeout = setTimeout(() => {
          listeners.set(destination, (listeners.get(destination) || []).filter((fn) => fn !== receive));
          reject(new Error(`Timed out waiting for ${destination}`));
        }, timeoutMs);
        listeners.set(destination, [...(listeners.get(destination) || []), receive]);
      });
    },
    close: () => client?.deactivate(),
  };
}

const a = await login(accounts.sender);
const outsider = await login(accounts.observer);
const b = await login(accounts.recipient);
const admin = await login(accounts.admin, true);
assert(new Set([a.user.id, outsider.user.id, b.user.id].map(Number)).size === 3,
  "Acceptance test requires three different ordinary users");

let restriction;
let restrictionLifted = false;
let socketA;
let socketB;

try {
  await expectForbidden("/api/chat-admin/stats", { token: a.token });
  await normalizeNoFriendship(a, b);
  await expectForbidden(`/api/chat/conversations/private/${b.user.id}`, { method: "POST", token: a.token });
  await establishFriendship(a, b);

  const forgedStatus = await request(`/api/friends/${b.user.id}/status`, {
    token: a.token,
    headers: { "X-User-Id": String(outsider.user.id), "X-User-Role": "ADMIN" },
  });
  assert(forgedStatus.status === "ACCEPTED", "Gateway did not replace forged identity headers with the JWT identity");

  const conversation = await request(`/api/chat/conversations/private/${b.user.id}`, {
    method: "POST",
    token: a.token,
  });
  assert(Boolean(conversation?.id), "Opening a private conversation did not return its id");

  socketA = connect(a.token);
  socketB = connect(b.token);
  await Promise.all([socketA.connected, socketB.connected]);

  const clientMessageId = `acceptance-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  const content = `acceptance-message-${new Date().toISOString()}`;
  const receiveMessage = socketB.wait("messages", (event) => event.message?.clientMessageId === clientMessageId);
  const sent = await request(`/api/chat/conversations/${conversation.id}/messages`, {
    method: "POST",
    token: a.token,
    body: { clientMessageId, content, replyToId: null },
  });
  const received = await receiveMessage;
  assert(Number(received.message.id) === Number(sent.id), "WebSocket and HTTP message ids differ");
  assert(received.message.content === content, "WebSocket content differs from persisted content");

  const recipientConversations = await request("/api/chat/conversations", { token: b.token });
  const recipientConversation = recipientConversations.find((item) => Number(item.id) === Number(conversation.id));
  assert(Number(recipientConversation?.unreadCount) >= 1, "Recipient unread count did not increase");

  const history = await request(`/api/chat/conversations/${conversation.id}/messages?limit=100`, { token: b.token });
  assert(history.some((message) => Number(message.id) === Number(sent.id) && message.content === content),
    "Persisted message is missing from history");

  const duplicate = await request(`/api/chat/conversations/${conversation.id}/messages`, {
    method: "POST",
    token: a.token,
    body: { clientMessageId, content, replyToId: null },
  });
  assert(Number(duplicate.id) === Number(sent.id), "clientMessageId idempotency returned a different message");
  const historyAfterDuplicate = await request(`/api/chat/conversations/${conversation.id}/messages?limit=100`, { token: a.token });
  assert(historyAfterDuplicate.filter((message) => message.clientMessageId === clientMessageId).length === 1,
    "Idempotent resend created a duplicate database row");

  await expectForbidden(`/api/chat/conversations/${conversation.id}/messages?limit=20`, { token: outsider.token });

  const report = await request(`/api/chat/messages/${sent.id}/report`, {
    method: "POST",
    token: b.token,
    body: { type: "HARASSMENT", description: "Automated acceptance-test report" },
  });
  assert(Boolean(report?.id), "Reporting a message did not return a report id");
  const openReports = await request("/api/chat-admin/reports?page=1&size=100&status=OPEN", { token: admin.token });
  assert(openReports.records.some((item) => Number(item.id) === Number(report.id)), "Admin cannot see the open report");
  const resolved = await request(`/api/chat-admin/reports/${report.id}/resolve`, {
    method: "POST",
    token: admin.token,
    body: { resolution: "Verified automatically during acceptance testing" },
  });
  assert(resolved.status === "RESOLVED", "Admin could not resolve the report");

  const receiveRead = socketA.wait("read-events", (event) => Number(event.conversationId) === Number(conversation.id));
  await request(`/api/chat/conversations/${conversation.id}/read`, {
    method: "PUT",
    token: b.token,
    body: { lastMessageId: sent.id },
  });
  const readEvent = await receiveRead;
  assert(Number(readEvent.lastReadMessageId) >= Number(sent.id), "Read receipt did not reach the sent message");
  const afterRead = await request("/api/chat/conversations", { token: b.token });
  assert(Number(afterRead.find((item) => Number(item.id) === Number(conversation.id))?.unreadCount) === 0,
    "Marking the conversation as read did not clear unreadCount");

  restriction = await request("/api/chat-admin/restrictions", {
    method: "POST",
    token: admin.token,
    body: {
      userId: b.user.id,
      type: "CHAT_BAN",
      reason: "Temporary automated acceptance-test restriction",
    },
  });
  assert(Boolean(restriction?.id) && restriction.active === true, "Admin could not create a chat restriction");
  await expectForbidden(`/api/chat/conversations/${conversation.id}/messages`, {
    method: "POST",
    token: b.token,
    body: { clientMessageId: `banned-${Date.now()}`, content: "must be rejected", replyToId: null },
  });
  await request(`/api/chat-admin/restrictions/${restriction.id}`, { method: "DELETE", token: admin.token });
  restrictionLifted = true;

  const replyClientId = `after-lift-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const receiveReply = socketA.wait("messages", (event) => event.message?.clientMessageId === replyClientId);
  const reply = await request(`/api/chat/conversations/${conversation.id}/messages`, {
    method: "POST",
    token: b.token,
    body: { clientMessageId: replyClientId, content: "chat works after restriction is lifted", replyToId: sent.id },
  });
  const replyEvent = await receiveReply;
  assert(Number(replyEvent.message.id) === Number(reply.id), "Message was not delivered after lifting the restriction");

  const receiveRecall = socketB.wait("messages", (event) =>
    event.kind === "MESSAGE_RECALLED" && Number(event.message?.id) === Number(sent.id));
  const recalled = await request(`/api/chat/messages/${sent.id}/recall`, { method: "POST", token: a.token });
  await receiveRecall;
  assert(recalled.status === "RECALLED" && !recalled.content, "Recalled message still exposes its content");
  const finalHistory = await request(`/api/chat/conversations/${conversation.id}/messages?limit=100`, { token: b.token });
  const recalledInHistory = finalHistory.find((message) => Number(message.id) === Number(sent.id));
  assert(recalledInHistory?.status === "RECALLED" && !recalledInHistory.content,
    "History did not persist the recalled state");

  const stats = await request("/api/chat-admin/stats", { token: admin.token });
  assert(typeof stats.messagesToday === "number" && typeof stats.outboxBacklog === "number",
    "Admin chat statistics are incomplete");
  const outbox = await request("/api/chat-admin/outbox?page=1&size=20", { token: admin.token });
  assert(Array.isArray(outbox.records), "Admin outbox management endpoint is unavailable");

  console.log(`PASS checks=${checks} conversation=${conversation.id} message=${sent.id} report=${report.id}`);
} finally {
  if (restriction?.id && !restrictionLifted) {
    await rawRequest(`/api/chat-admin/restrictions/${restriction.id}`, { method: "DELETE", token: admin.token });
  }
  await Promise.allSettled([socketA?.close(), socketB?.close()]);
}
