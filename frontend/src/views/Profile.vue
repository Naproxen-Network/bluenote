<template>
  <div v-if="user" class="profile">
    <div class="card hero">
      <div class="cover"></div>
      <div class="headline">
        <img :src="user.avatar || fallback" class="pfp" :alt="`${user.displayName || user.username} avatar`" @error="onImageError" />
        <div class="idblock">
          <h2 class="serif">{{ user.displayName }}</h2>
          <div class="sub muted">{{ user.position || 'No position provided' }}</div>
          <div class="chips">
            <span class="tag" v-if="user.party">{{ user.party }}</span>
            <span class="tag" v-if="user.state">{{ user.state }}</span>
          </div>
        </div>
        <div class="ops">
          <template v-if="!isMe">
            <button class="btn" :class="{ ghost: following }" @click="toggleFollow">
              {{ following ? 'Following' : 'Follow' }}
            </button>
            <button v-if="friendStatus.status === 'NONE'" class="btn ghost" :disabled="friendBusy" @click="sendFriendRequest">Add Friend</button>
            <button v-else-if="friendStatus.status === 'PENDING' && friendStatus.direction === 'INCOMING'" class="btn" :disabled="friendBusy" @click="acceptFriend">Accept Friend</button>
            <button v-else-if="friendStatus.status === 'PENDING'" class="btn ghost" :disabled="friendBusy" @click="cancelFriend">Request Sent</button>
            <button v-else-if="friendStatus.status === 'ACCEPTED'" class="btn" :disabled="friendBusy" @click="openChat">Message</button>
            <button v-else-if="friendStatus.status === 'BLOCKED'" class="btn ghost" :disabled="friendBusy" @click="unblock">Unblock</button>
            <button v-else-if="friendStatus.status === 'BLOCKED_BY_PEER'" class="btn ghost" disabled>Unavailable</button>
            <button v-if="friendStatus.status !== 'BLOCKED' && friendStatus.status !== 'BLOCKED_BY_PEER'" class="text-danger" :disabled="friendBusy" @click="block">Block</button>
          </template>
          <button v-else class="btn ghost" @click="editing = !editing">
            {{ editing ? 'Done' : 'Edit Profile' }}
          </button>
        </div>
      </div>

      <div v-if="friendError" class="friend-error">{{ friendError }}</div>
      <div class="about">
        <template v-if="editing">
          <label class="muted">Bio</label>
          <textarea class="input" v-model="bioDraft" rows="4"></textarea>
          <label class="muted">Interests (space-separated)</label>
          <input class="input" v-model="interestDraft" />
          <button class="btn sm" @click="save">Save</button>
        </template>
        <template v-else>
          <p class="bio">{{ user.bio || 'This user has not written a bio yet.' }}</p>
          <div class="interests">
            <span class="tag" v-for="interest in user.interests" :key="interest">{{ interest }}</span>
          </div>
          <div class="edu muted" v-if="user.almaMater">Education: {{ user.almaMater }}</div>
        </template>
      </div>
    </div>

    <h3 class="serif sechead">Posts: {{ posts.length }}</h3>
    <div class="masonry" v-if="posts.length">
      <PostCard v-for="post in posts" :key="post.id" :post="post" />
    </div>
    <div v-else class="muted empty card">No posts yet.</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../api";
import PostCard from "../components/PostCard.vue";
import { useAuth } from "../store/auth";
import { useFriendStore } from "../store/friend";
import { useChatStore } from "../store/chat";

const route = useRoute();
const router = useRouter();
const auth = useAuth();
const friends = useFriendStore();
const chat = useChatStore();
const user = ref(null);
const posts = ref([]);
const following = ref(false);
const editing = ref(false);
const bioDraft = ref("");
const interestDraft = ref("");
const friendStatus = ref({ status: "NONE", canMessage: false });
const friendBusy = ref(false);
const friendError = ref("");
const fallback = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='96' height='96'%3E%3Crect width='96' height='96' fill='%232a6fb5'/%3E%3C/svg%3E";

const targetId = computed(() => Number(route.params.id) || Number(auth.user?.id));
const isMe = computed(() => targetId.value === Number(auth.user?.id));

function onImageError(event) { event.target.src = fallback; }
function errorMessage(exception) { return exception.response?.data?.message || exception.message || "Operation failed"; }

async function load() {
  const id = targetId.value;
  friendError.value = "";
  const profileResponse = await api.get(`/api/user/${id}`);
  if (profileResponse.code === 0) {
    user.value = profileResponse.data;
    bioDraft.value = profileResponse.data.bio || "";
    interestDraft.value = (profileResponse.data.interests || []).join(" ");
  }
  const postResponse = await api.get("/api/post/page", { params: { authorId: id, size: 30 } });
  if (postResponse.code === 0) posts.value = postResponse.data.records || [];
  if (!isMe.value) {
    const [followResponse, status] = await Promise.all([
      api.get(`/api/user/${id}/isFollowing`),
      friends.status(id),
    ]);
    if (followResponse.code === 0) following.value = followResponse.data;
    friendStatus.value = status;
  }
}

async function runFriend(operation) {
  friendBusy.value = true;
  friendError.value = "";
  try {
    await operation();
    if (!isMe.value) friendStatus.value = await friends.status(targetId.value);
  } catch (exception) {
    friendError.value = errorMessage(exception);
  } finally {
    friendBusy.value = false;
  }
}

async function toggleFollow() {
  if (following.value) {
    await api.delete(`/api/user/follow/${targetId.value}`);
    following.value = false;
  } else {
    await api.post(`/api/user/follow/${targetId.value}`);
    following.value = true;
  }
}
async function sendFriendRequest() {
  const note = window.prompt("Add a short note to your friend request (optional):", "");
  if (note === null) return;
  await runFriend(() => friends.request(targetId.value, note));
}
async function acceptFriend() {
  await runFriend(() => friends.accept(friendStatus.value.id));
}
async function cancelFriend() {
  if (!window.confirm("Cancel this friend request?")) return;
  await runFriend(() => friends.cancel(friendStatus.value.id));
}
async function openChat() {
  await runFriend(async () => {
    const conversation = await chat.openFriend(targetId.value);
    await router.push(`/messages/${conversation.id}`);
  });
}
async function block() {
  if (!window.confirm("Block this user? Any friendship and active conversation will be closed.")) return;
  await runFriend(() => friends.block(targetId.value, "Blocked from profile"));
}
async function unblock() {
  await runFriend(() => friends.unblock(targetId.value));
}
async function save() {
  const response = await api.put("/api/user/me", {
    bio: bioDraft.value,
    interests: interestDraft.value.split(/\s+/).filter(Boolean).join("|"),
  });
  if (response.code === 0) { editing.value = false; await load(); }
}

onMounted(() => load().catch((exception) => { friendError.value = errorMessage(exception); }));
watch(() => route.params.id, () => load().catch((exception) => { friendError.value = errorMessage(exception); }));
</script>

<style scoped>
.profile { max-width: 1040px; margin: 0 auto; min-width: 0; }
.hero { overflow: hidden; margin-bottom: 26px; isolation: isolate; }
.cover {
  position: relative;
  height: 140px;
  background: linear-gradient(120deg, #1e5fa8, #6ba3d6 60%, #bcd8ef);
}
.cover::after {
  content: "";
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 82% 20%, rgba(255, 255, 255, 0.26), transparent 34%);
  pointer-events: none;
}
.headline {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 104px minmax(180px, 1fr) auto;
  align-items: start;
  gap: 18px;
  padding: 0 28px 22px;
  margin-top: -48px;
}
.pfp { width: 96px; height: 96px; border-radius: 24px; object-fit: cover; border: 4px solid #fff; box-shadow: var(--shadow); background: #fff; }
.idblock { min-width: 0; padding-top: 56px; }
.idblock h2 { margin: 0; color: var(--blue-900); font-size: 26px; line-height: 1.2; overflow-wrap: anywhere; }
.sub { max-width: 62ch; font-size: 13px; line-height: 1.45; margin-top: 4px; overflow-wrap: anywhere; }
.chips { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.chips .tag, .interests .tag { max-width: 100%; overflow-wrap: anywhere; }
.ops { min-width: 0; padding-top: 56px; display: flex; align-items: center; justify-content: flex-end; gap: 8px; flex-wrap: wrap; max-width: 500px; }
.ops .btn { white-space: nowrap; }
.text-danger { border: none; background: transparent; color: #af3c49; font-size: 12px; padding: 5px; }
.friend-error { margin: 16px 28px 0 142px; color: #a63643; background: #fff0f2; border-radius: 10px; padding: 8px 12px; font-size: 12px; }
.about { padding: 20px 28px 26px; border-top: 1px solid var(--line); display: flex; flex-direction: column; gap: 10px; }
.about label { font-size: 12px; margin-top: 6px; }
.bio { font-family: var(--serif); font-size: 15px; line-height: 1.8; margin: 0; color: var(--ink); overflow-wrap: anywhere; }
.interests { display: flex; gap: 8px; flex-wrap: wrap; }
.edu { font-size: 13px; line-height: 1.55; overflow-wrap: anywhere; }
.sechead { color: var(--blue-900); margin: 0 0 16px; }
.masonry { columns: 3 220px; column-gap: 18px; }
.masonry > * { break-inside: avoid; margin-bottom: 18px; display: inline-block; width: 100%; }
.empty { padding: 34px; text-align: center; }
@media (max-width: 820px) {
  .masonry { columns: 2 150px; }
  .headline { grid-template-columns: 104px minmax(0, 1fr); }
  .ops { grid-column: 1 / -1; max-width: none; padding-top: 0; justify-content: flex-start; }
  .friend-error { margin: 0 28px 16px; }
}
@media (max-width: 560px) {
  .cover { height: 112px; }
  .headline { grid-template-columns: 84px minmax(0, 1fr); gap: 12px; margin-top: -40px; padding: 0 16px 18px; }
  .pfp { width: 80px; height: 80px; border-radius: 20px; }
  .idblock { padding-top: 46px; }
  .idblock h2 { font-size: 22px; }
  .ops { gap: 7px; }
  .ops .btn { flex: 1 1 auto; }
  .friend-error { margin: 0 16px 14px; }
  .about { padding: 18px 16px 22px; }
}
</style>
