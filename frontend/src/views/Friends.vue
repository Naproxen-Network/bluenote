<template>
  <section class="friends-page">
    <header class="page-head">
      <div>
        <h1 class="serif">Friends</h1>
        <p class="muted">Only accepted friends can start a private conversation.</p>
      </div>
      <form class="add-form card" @submit.prevent="lookupUser">
        <input class="input" v-model.trim="targetIdentity" maxlength="64" placeholder="User ID or username" required />
        <input class="input" v-model.trim="requestMessage" maxlength="200" placeholder="Add a short note (optional)" />
        <button class="btn" :disabled="busy">{{ busy ? 'Searching…' : 'Find User' }}</button>
      </form>
    </header>

    <div v-if="notice" class="notice" :class="{ error: noticeError }">{{ notice }}</div>

    <article v-if="searchResult" class="card lookup-result">
      <img :src="searchResult.user?.avatar || fallback" @error="onImageError" />
      <div class="summary">
        <router-link class="name" :to="`/profile/${searchResult.user.id}`">{{ searchResult.user.displayName }}</router-link>
        <div class="muted meta">ID {{ searchResult.user.id }} · @{{ searchResult.user.username }}</div>
        <div class="muted meta">{{ searchResult.user.position || 'No position provided' }}</div>
      </div>
      <div class="lookup-actions">
        <span class="relation-status">{{ relationshipLabel(searchResult.status) }}</span>
        <button v-if="searchResult.canAdd" class="btn sm" :disabled="busy" @click="sendRequest">Add Friend</button>
        <button v-else-if="searchResult.canMessage" class="btn sm" @click="message(searchResult.user.id)">Message</button>
      </div>
    </article>

    <div class="tabs">
      <button v-for="item in tabs" :key="item.key" :class="{ on: tab === item.key }" @click="tab = item.key">
        {{ item.label }} <span v-if="item.count">{{ item.count }}</span>
      </button>
    </div>

    <div v-if="friends.loading" class="card empty muted">Loading…</div>
    <div v-else-if="!currentRows.length" class="card empty muted">Nothing here yet.</div>
    <div v-else class="people-grid">
      <article v-for="row in currentRows" :key="rowKey(row)" class="card person">
        <img :src="peer(row)?.avatar || fallback" @error="onImageError" />
        <div class="summary">
          <router-link class="name" :to="`/profile/${peer(row)?.id}`">{{ peer(row)?.displayName || `User ${peer(row)?.id}` }}</router-link>
          <div class="muted meta">{{ peer(row)?.position || 'No position provided' }}</div>
          <div v-if="row.requestMessage" class="request-note">“{{ row.requestMessage }}”</div>
          <div v-if="row.requestedAt" class="muted time">{{ formatTime(row.requestedAt) }}</div>
        </div>
        <div class="actions">
          <template v-if="tab === 'incoming'">
            <button class="btn sm" @click="accept(row)">Accept</button>
            <button class="btn ghost sm" @click="run(() => friends.reject(row.id), 'Request rejected')">Reject</button>
          </template>
          <template v-else-if="tab === 'outgoing'">
            <button class="btn ghost sm" @click="run(() => friends.cancel(row.id), 'Request cancelled')">Cancel</button>
          </template>
          <template v-else-if="tab === 'friends'">
            <button class="btn sm" @click="message(peer(row).id)">Message</button>
            <button class="btn ghost sm danger" @click="remove(peer(row).id)">Remove</button>
          </template>
          <template v-else>
            <button class="btn ghost sm" @click="run(() => friends.unblock(peer(row).id), 'User unblocked')">Unblock</button>
          </template>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useFriendStore } from "../store/friend";
import { useChatStore } from "../store/chat";

const router = useRouter();
const friends = useFriendStore();
const chat = useChatStore();
const tab = ref("friends");
const targetIdentity = ref("");
const requestMessage = ref("");
const searchResult = ref(null);
const busy = ref(false);
const notice = ref("");
const noticeError = ref(false);
const fallback = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='72' height='72'%3E%3Crect width='72' height='72' rx='18' fill='%232a6fb5'/%3E%3C/svg%3E";

const tabs = computed(() => [
  { key: "friends", label: "Friends", count: friends.friends.length },
  { key: "incoming", label: "Incoming", count: friends.incoming.length },
  { key: "outgoing", label: "Sent", count: friends.outgoing.length },
  { key: "blocked", label: "Blocked", count: friends.blocked.length },
]);
const currentRows = computed(() => friends[tab.value] || []);

function peer(row) { return tab.value === "blocked" ? row : row.peer; }
function rowKey(row) { return tab.value === "blocked" ? `blocked-${row.id}` : `${tab.value}-${row.id}`; }
function onImageError(event) { event.target.src = fallback; }
function formatTime(value) { return new Date(value).toLocaleString(); }
function show(message, error = false) { notice.value = message; noticeError.value = error; }
function relationshipLabel(status) {
  return ({
    NONE: "Not connected",
    DELETED: "Friendship removed",
    REJECTED: "Previous request rejected",
    CANCELLED: "Previous request cancelled",
    PENDING: "Request pending",
    ACCEPTED: "Already friends",
    BLOCKED: "Blocked by you",
    BLOCKED_BY_PEER: "Interaction unavailable",
    SELF: "This is your account",
  })[status] || status;
}

async function run(operation, success) {
  busy.value = true;
  try {
    await operation();
    show(success);
  } catch (error) {
    show(error.response?.data?.message || error.message || "Operation failed", true);
  } finally {
    busy.value = false;
  }
}

async function lookupUser() {
  searchResult.value = null;
  await run(async () => {
    searchResult.value = await friends.lookup(targetIdentity.value);
  }, "User found");
}
async function sendRequest() {
  await run(() => friends.request(targetIdentity.value, requestMessage.value), "Friend request sent");
  if (!noticeError.value) {
    searchResult.value = await friends.lookup(targetIdentity.value);
    requestMessage.value = "";
    tab.value = "outgoing";
  }
}
async function accept(row) {
  let result;
  await run(async () => { result = await friends.accept(row.id); }, "Friend request accepted");
  if (result?.conversationId) await router.push(`/messages/${result.conversationId}`);
}
async function message(userId) {
  await run(async () => {
    const conversation = await chat.openFriend(userId);
    await router.push(`/messages/${conversation.id}`);
  }, "Conversation opened");
}
async function remove(userId) {
  if (!window.confirm("Remove this friend? The existing conversation will be closed.")) return;
  await run(() => friends.remove(userId), "Friend removed");
}

onMounted(() => friends.loadAll().catch((error) => show(error.message, true)));
</script>

<style scoped>
.friends-page { max-width: 1040px; margin: 0 auto; }
.page-head { display: grid; grid-template-columns: 1fr minmax(420px, 1.4fr); align-items: center; gap: 24px; margin-bottom: 22px; }
.page-head h1 { margin: 0; color: var(--blue-900); }
.page-head p { margin: 7px 0 0; font-size: 13px; }
.add-form { display: grid; grid-template-columns: minmax(150px, .8fr) 1fr auto; gap: 8px; padding: 12px; }
.lookup-result { display: grid; grid-template-columns: 64px 1fr auto; align-items: center; gap: 14px; padding: 16px 18px; margin-bottom: 16px; border-color: #bdd6ea; }
.lookup-result > img { width: 64px; height: 64px; border-radius: 18px; object-fit: cover; }
.lookup-actions { display: flex; align-items: center; gap: 12px; }
.relation-status { color: var(--muted); font-size: 12px; white-space: nowrap; }
.tabs { display: flex; gap: 8px; margin-bottom: 16px; }
.tabs button { border: 1px solid var(--line); background: #fff; color: var(--muted); border-radius: 999px; padding: 8px 14px; }
.tabs button.on { color: #fff; background: var(--blue-700); border-color: var(--blue-700); }
.tabs span { margin-left: 5px; font-size: 11px; opacity: .8; }
.people-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.person { display: grid; grid-template-columns: 64px 1fr auto; gap: 14px; align-items: center; padding: 18px; }
.person > img { width: 64px; height: 64px; border-radius: 18px; object-fit: cover; }
.summary { min-width: 0; }
.name { font-weight: 700; color: var(--blue-900); }
.meta, .time { font-size: 12px; margin-top: 4px; }
.request-note { margin-top: 7px; font-family: var(--serif); font-size: 13px; overflow-wrap: anywhere; }
.actions { display: flex; flex-direction: column; gap: 7px; }
.danger { color: #b23b48 !important; border-color: #efc7cc !important; }
.empty { padding: 44px; text-align: center; }
.notice { margin-bottom: 14px; padding: 10px 14px; color: #24683f; background: #edf8f1; border: 1px solid #cce8d5; border-radius: 12px; }
.notice.error { color: #9d3340; background: #fff0f2; border-color: #f0c7cc; }
@media (max-width: 840px) { .page-head { grid-template-columns: 1fr; } .people-grid { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .add-form, .lookup-result { grid-template-columns: 1fr; } .lookup-result > img { width: 52px; height: 52px; } .lookup-actions { justify-content: space-between; } .person { grid-template-columns: 52px 1fr; } .person > img { width: 52px; height: 52px; } .actions { grid-column: 1 / -1; flex-direction: row; } }
</style>
