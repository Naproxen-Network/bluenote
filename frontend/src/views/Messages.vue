<template>
  <section class="chat-page card">
    <aside class="conversation-pane">
      <header class="pane-head">
        <div>
          <h2 class="serif">Messages</h2>
          <span class="connection" :class="{ online: chat.connected }">{{ chat.connected ? 'Live' : 'Reconnecting' }}</span>
        </div>
        <button class="refresh" title="Refresh" @click="refresh">↻</button>
      </header>

      <div v-if="loadingConversations" class="list-state muted">Loading…</div>
      <div v-else-if="!chat.conversations.length" class="list-state muted">
        Accept a friend request, then start a conversation from the Friends page.
      </div>
      <button
        v-for="conversation in chat.conversations"
        :key="conversation.id"
        class="conversation"
        :class="{ active: conversation.id === chat.activeId }"
        @click="choose(conversation.id)"
      >
        <div class="avatar-wrap">
          <img :src="conversation.peer?.avatar || fallback" @error="onImageError" />
          <span v-if="presence[conversation.peer?.id]" class="presence"></span>
        </div>
        <div class="conversation-copy">
          <div class="line-one">
            <strong>{{ conversation.peer?.displayName || `User ${conversation.peer?.id}` }}</strong>
            <time>{{ shortTime(conversation.lastMessageAt) }}</time>
          </div>
          <div class="line-two">
            <span>{{ preview(conversation.lastMessage) }}</span>
            <b v-if="conversation.unreadCount">{{ cap(conversation.unreadCount) }}</b>
          </div>
        </div>
      </button>
    </aside>

    <main v-if="active" class="message-pane">
      <header class="chat-head">
        <div class="person-title">
          <img :src="active.peer?.avatar || fallback" @error="onImageError" />
          <div>
            <router-link :to="`/profile/${active.peer?.id}`">{{ active.peer?.displayName }}</router-link>
            <div class="muted">{{ presence[active.peer?.id] ? 'Online' : 'Offline' }}</div>
          </div>
        </div>
        <div class="head-actions">
          <span v-if="chat.typingByConversation[active.id]" class="typing">typing…</span>
          <button class="btn ghost sm" @click="$router.push('/friends')">Friends</button>
        </div>
      </header>

      <div ref="messageArea" class="message-area">
        <button v-if="canLoadEarlier" class="load-earlier" :disabled="loadingMessages" @click="loadEarlier">
          {{ loadingMessages ? 'Loading…' : 'Load earlier messages' }}
        </button>
        <div v-if="loadingMessages && !chat.activeMessages.length" class="message-state muted">Loading messages…</div>
        <div v-else-if="!chat.activeMessages.length" class="message-state muted">Say hello to start this conversation.</div>

        <article
          v-for="message in chat.activeMessages"
          :key="message.id"
          class="message-row"
          :class="{ mine: isMine(message) }"
        >
          <img v-if="!isMine(message)" :src="active.peer?.avatar || fallback" @error="onImageError" />
          <div class="bubble-block">
            <div class="bubble" :class="{ recalled: message.status === 'RECALLED' }">
              {{ message.status === 'RECALLED' ? 'This message was recalled.' : message.content }}
            </div>
            <div class="message-meta">
              <time>{{ fullTime(message.createdAt) }}</time>
              <button v-if="isMine(message) && canRecall(message)" @click="recall(message)">Recall</button>
              <button v-if="!isMine(message) && message.status !== 'RECALLED'" @click="report(message)">Report</button>
            </div>
          </div>
        </article>
      </div>

      <form class="composer" @submit.prevent="send">
        <div v-if="error" class="send-error">{{ error }}</div>
        <textarea
          v-model="draft"
          class="input"
          rows="3"
          maxlength="2000"
          placeholder="Write a message. Enter to send, Shift+Enter for a new line."
          @input="onTyping"
          @keydown.enter.exact.prevent="send"
        ></textarea>
        <div class="composer-foot">
          <span class="muted">{{ draft.length }}/2000</span>
          <button class="btn" :disabled="sending || !draft.trim()">{{ sending ? 'Sending…' : 'Send' }}</button>
        </div>
      </form>
    </main>

    <main v-else class="no-selection">
      <div class="empty-mark">✉</div>
      <h2 class="serif">Your conversations</h2>
      <p class="muted">Select a friend on the left to view messages.</p>
    </main>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../api";
import { useAuth } from "../store/auth";
import { useChatStore } from "../store/chat";

const route = useRoute();
const router = useRouter();
const auth = useAuth();
const chat = useChatStore();
const draft = ref("");
const sending = ref(false);
const loadingConversations = ref(false);
const loadingMessages = ref(false);
const canLoadEarlier = ref(true);
const error = ref("");
const messageArea = ref(null);
const presence = reactive({});
const fallback = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='72' height='72'%3E%3Crect width='72' height='72' rx='18' fill='%232a6fb5'/%3E%3C/svg%3E";
let typingTimer = 0;
let presenceTimer = 0;

const active = computed(() => chat.activeConversation);

function isMine(message) { return Number(message.senderId) === Number(auth.user?.id); }
function cap(value) { return value > 99 ? "99+" : value; }
function onImageError(event) { event.target.src = fallback; }
function fullTime(value) { return value ? new Date(value).toLocaleString() : ""; }
function shortTime(value) {
  if (!value) return "";
  const date = new Date(value);
  const today = new Date();
  return date.toDateString() === today.toDateString()
    ? date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString();
}
function preview(message) {
  if (!message) return "No messages yet";
  if (message.status === "RECALLED") return "Message recalled";
  return message.content || "Message";
}
function canRecall(message) {
  if (message.status !== "NORMAL" || !message.createdAt) return false;
  return Date.now() - new Date(message.createdAt).getTime() < 120000;
}
function errorMessage(exception) {
  return exception.response?.data?.message || exception.message || "Operation failed";
}

async function scrollBottom() {
  await nextTick();
  if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight;
}

async function refresh() {
  loadingConversations.value = true;
  error.value = "";
  try {
    await chat.loadConversations();
    await loadPresence();
  } catch (exception) {
    error.value = errorMessage(exception);
  } finally {
    loadingConversations.value = false;
  }
}

async function choose(id, updateRoute = true) {
  if (chat.activeId === Number(id) && chat.activeMessages.length) return;
  loadingMessages.value = true;
  canLoadEarlier.value = true;
  error.value = "";
  try {
    await chat.selectConversation(id);
    if (updateRoute && String(route.params.id || "") !== String(id)) await router.replace(`/messages/${id}`);
    await scrollBottom();
  } catch (exception) {
    error.value = errorMessage(exception);
  } finally {
    loadingMessages.value = false;
  }
}

async function loadEarlier() {
  const firstId = chat.activeMessages[0]?.id;
  if (!firstId) return;
  const oldHeight = messageArea.value?.scrollHeight || 0;
  loadingMessages.value = true;
  try {
    const rows = await chat.loadMessages(active.value.id, firstId);
    canLoadEarlier.value = rows.length === 50;
    await nextTick();
    if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight - oldHeight;
  } catch (exception) {
    error.value = errorMessage(exception);
  } finally {
    loadingMessages.value = false;
  }
}

async function send() {
  if (sending.value || !draft.value.trim()) return;
  sending.value = true;
  error.value = "";
  chat.sendTyping(false);
  window.clearTimeout(typingTimer);
  try {
    await chat.send(draft.value);
    draft.value = "";
    await scrollBottom();
  } catch (exception) {
    error.value = errorMessage(exception);
  } finally {
    sending.value = false;
  }
}

function onTyping() {
  chat.sendTyping(true);
  window.clearTimeout(typingTimer);
  typingTimer = window.setTimeout(() => chat.sendTyping(false), 1200);
}

async function recall(message) {
  if (!window.confirm("Recall this message?")) return;
  try { await chat.recall(message.id); } catch (exception) { error.value = errorMessage(exception); }
}

async function report(message) {
  const description = window.prompt("Briefly describe the problem with this message:");
  if (description === null) return;
  try {
    await chat.report(message.id, "INAPPROPRIATE", description || "Inappropriate content");
    window.alert("Report submitted for administrator review.");
  } catch (exception) {
    error.value = errorMessage(exception);
  }
}

async function loadPresence() {
  const ids = chat.conversations.map((item) => item.peer?.id).filter(Boolean);
  if (!ids.length) return;
  try {
    const response = await api.get("/api/chat/presence", { params: { ids: ids.join(",") } });
    if (response.code === 0) Object.assign(presence, response.data);
  } catch { /* Presence is informational and must not block messaging. */ }
}

watch(() => route.params.id, (id) => {
  if (id) choose(id, false);
}, { immediate: false });
watch(() => chat.activeMessages.length, () => {
  if (!loadingMessages.value) scrollBottom();
});

onMounted(async () => {
  chat.connect();
  await refresh();
  const routeId = Number(route.params.id);
  if (routeId) await choose(routeId, false);
  else if (chat.conversations.length) await choose(chat.conversations[0].id);
  presenceTimer = window.setInterval(loadPresence, 30000);
});
onBeforeUnmount(() => {
  chat.sendTyping(false);
  window.clearTimeout(typingTimer);
  window.clearInterval(presenceTimer);
});
</script>

<style scoped>
.chat-page { height: calc(100vh - 126px); min-height: 560px; display: grid; grid-template-columns: 330px minmax(0, 1fr); overflow: hidden; }
.conversation-pane { border-right: 1px solid var(--line); overflow-y: auto; background: #fbfdff; }
.pane-head, .chat-head { min-height: 72px; display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; border-bottom: 1px solid var(--line); background: rgba(255,255,255,.95); }
.pane-head h2 { margin: 0; color: var(--blue-900); }
.connection { display: inline-flex; align-items: center; gap: 5px; margin-top: 4px; color: #9a6a1f; font-size: 11px; }
.connection::before { content: ""; width: 7px; height: 7px; border-radius: 50%; background: #d6a24f; }
.connection.online { color: #2f7b4d; }
.connection.online::before { background: #45ad6e; box-shadow: 0 0 0 3px #daf2e3; }
.refresh { border: none; background: var(--blue-50); color: var(--blue-700); width: 32px; height: 32px; border-radius: 50%; font-size: 20px; }
.conversation { width: 100%; border: none; border-bottom: 1px solid var(--line); background: transparent; padding: 13px 14px; display: flex; text-align: left; gap: 11px; }
.conversation:hover, .conversation.active { background: var(--blue-50); }
.avatar-wrap { position: relative; flex: 0 0 auto; }
.avatar-wrap img, .person-title img { width: 48px; height: 48px; object-fit: cover; border-radius: 15px; }
.presence { position: absolute; right: -1px; bottom: -1px; width: 12px; height: 12px; border-radius: 50%; background: #45ad6e; border: 2px solid #fff; }
.conversation-copy { flex: 1; min-width: 0; }
.line-one, .line-two { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
.line-one strong { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: var(--blue-900); font-size: 14px; }
.line-one time { color: var(--muted); font-size: 10px; white-space: nowrap; }
.line-two { margin-top: 7px; }
.line-two span { color: var(--muted); font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.line-two b { min-width: 19px; height: 19px; padding: 0 5px; display: inline-flex; align-items: center; justify-content: center; border-radius: 10px; background: #d94d5c; color: #fff; font-size: 10px; }
.list-state { padding: 30px 20px; text-align: center; line-height: 1.6; font-size: 13px; }
.message-pane { min-width: 0; display: grid; grid-template-rows: auto minmax(0, 1fr) auto; background: #fff; }
.person-title { display: flex; align-items: center; gap: 11px; }
.person-title img { width: 42px; height: 42px; border-radius: 13px; }
.person-title a { color: var(--blue-900); font-weight: 700; }
.person-title .muted { margin-top: 3px; font-size: 11px; }
.head-actions { display: flex; align-items: center; gap: 12px; }
.typing { color: var(--blue-600); font-family: var(--serif); font-size: 13px; }
.message-area { overflow-y: auto; padding: 22px 24px; background: linear-gradient(180deg, #f9fcff, #fff); }
.load-earlier { display: block; margin: 0 auto 20px; border: none; color: var(--blue-600); background: transparent; font-size: 12px; }
.message-state { padding: 60px 20px; text-align: center; }
.message-row { display: flex; align-items: flex-end; gap: 8px; margin: 10px 0; }
.message-row > img { width: 30px; height: 30px; border-radius: 10px; object-fit: cover; }
.message-row.mine { justify-content: flex-end; }
.bubble-block { max-width: min(72%, 620px); }
.bubble { padding: 10px 13px; border-radius: 6px 17px 17px 17px; background: #edf4fa; border: 1px solid #dce9f4; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.55; font-size: 14px; }
.mine .bubble { color: #fff; border: none; border-radius: 17px 6px 17px 17px; background: linear-gradient(135deg, var(--blue-700), var(--blue-600)); }
.bubble.recalled, .mine .bubble.recalled { color: var(--muted); background: #f5f7f9; border: 1px dashed #d7e0e8; font-style: italic; }
.message-meta { display: flex; gap: 8px; align-items: center; margin-top: 4px; color: #8a99a8; font-size: 10px; }
.mine .message-meta { justify-content: flex-end; }
.message-meta button { border: none; background: transparent; color: var(--blue-600); padding: 0; font-size: 10px; }
.composer { padding: 13px 16px; border-top: 1px solid var(--line); background: #fff; }
.composer textarea { resize: none; max-height: 150px; }
.composer-foot { display: flex; align-items: center; justify-content: flex-end; gap: 14px; margin-top: 8px; }
.composer-foot .muted { font-size: 11px; }
.send-error { color: #a63643; background: #fff0f2; border-radius: 8px; padding: 7px 10px; margin-bottom: 8px; font-size: 12px; }
.no-selection { display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; background: #fff; }
.empty-mark { width: 72px; height: 72px; border-radius: 24px; display: grid; place-items: center; background: var(--blue-50); color: var(--blue-600); font-size: 30px; }
.no-selection h2 { margin: 18px 0 4px; color: var(--blue-900); }
@media (max-width: 760px) { .chat-page { grid-template-columns: 1fr; height: auto; min-height: 70vh; } .conversation-pane { max-height: 280px; border-right: none; border-bottom: 1px solid var(--line); } .message-pane { min-height: 620px; } .no-selection { min-height: 380px; } }
</style>
