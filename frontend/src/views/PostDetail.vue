<template>
  <div v-if="post" class="detail">
    <div class="main card">
      <TopicImage :field="post.field" height="240px" />
      <div class="body">
        <div class="author" @click="$router.push(`/profile/${post.authorId}`)">
          <img :src="author.avatar || fb" @error="onErr" />
          <div>
            <div class="name">{{ author.displayName || ('User #' + post.authorId) }}</div>
            <div class="muted pos">{{ author.position }}</div>
          </div>
        </div>

        <p class="content">{{ post.content }}</p>
        <div class="tags">
          <span class="tag">{{ post.field }}</span>
          <span class="tag" v-for="t in tagList" :key="t">{{ t }}</span>
        </div>

        <div class="actions">
          <button class="act" :class="{ on: liked }" @click="toggleLike">♡ Like {{ post.likeCount }}</button>
          <button class="act" :class="{ on: favorited }" @click="toggleFav">❏ Save {{ post.favoriteCount }}</button>
          <span class="act muted">💬 Comments {{ post.commentCount }}</span>
          <span class="act muted">👁 Views {{ post.viewCount }}</span>
        </div>
      </div>
    </div>

    <div class="comments card">
      <h3 class="serif">Comments · {{ comments.length }}</h3>
      <div class="composer">
        <input class="input" v-model="draft" placeholder="Share your thoughts…" @keyup.enter="send" />
        <button class="btn sm" :disabled="!draft.trim()" @click="send">Post</button>
      </div>
      <div class="clist">
        <div class="c" v-for="c in comments" :key="c.id">
          <div class="cavatar">{{ (c.userId || '?') }}</div>
          <div>
            <div class="cuser muted">User #{{ c.userId }}</div>
            <div class="ctext">{{ c.content }}</div>
          </div>
        </div>
        <div v-if="!comments.length" class="muted empty">No comments yet. Be the first.</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import api from "../api";
import TopicImage from "../components/TopicImage.vue";

const route = useRoute();
const post = ref(null);
const author = ref({});
const comments = ref([]);
const liked = ref(false);
const favorited = ref(false);
const draft = ref("");
const fb = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='52' height='52'%3E%3Crect width='52' height='52' fill='%232a6fb5'/%3E%3C/svg%3E";

const tagList = computed(() => (post.value?.tags || "").split(/\s+/).filter(Boolean));
function onErr(e) { e.target.src = fb; }

async function load() {
  const id = route.params.id;
  const r = await api.get(`/api/post/${id}`);
  if (r.code === 0) {
    post.value = r.data.post;
    comments.value = r.data.comments || [];
    liked.value = !!r.data.liked;
    favorited.value = !!r.data.favorited;
    const u = await api.get(`/api/user/${post.value.authorId}`);
    if (u.code === 0) author.value = u.data || {};
  }
}
async function toggleLike() {
  const r = await api.post(`/api/post/${post.value.id}/like`);
  if (r.code === 0) { liked.value = r.data; post.value.likeCount += r.data ? 1 : -1; }
}
async function toggleFav() {
  const r = await api.post(`/api/post/${post.value.id}/favorite`);
  if (r.code === 0) { favorited.value = r.data; post.value.favoriteCount += r.data ? 1 : -1; }
}
async function send() {
  const r = await api.post(`/api/post/${post.value.id}/comment`, { content: draft.value.trim() });
  if (r.code === 0) { comments.value.unshift(r.data); post.value.commentCount++; draft.value = ""; }
}
onMounted(load);
</script>

<style scoped>
.detail { display: grid; grid-template-columns: 1.4fr 1fr; gap: 24px; align-items: start; }
.main { overflow: hidden; }
.body { padding: 22px 26px 26px; }
.author { display: flex; align-items: center; gap: 12px; cursor: pointer; margin-bottom: 16px; }
.author img { width: 52px; height: 52px; border-radius: 50%; object-fit: cover; border: 1px solid var(--line); }
.name { font-weight: 700; font-size: 16px; }
.pos { font-size: 12px; }
.content { font-size: 17px; line-height: 1.8; font-family: var(--serif); color: var(--ink); }
.tags { display: flex; gap: 8px; flex-wrap: wrap; margin: 16px 0 20px; }
.actions { display: flex; gap: 10px; border-top: 1px solid var(--line); padding-top: 16px; flex-wrap: wrap; }
.act {
  border: 1px solid var(--line); background: #fff; border-radius: 999px; padding: 8px 16px;
  font-size: 13px; font-weight: 600; color: var(--ink);
}
.act.on { color: var(--blue-700); border-color: var(--blue-200); background: var(--blue-50); }
.comments { padding: 22px 24px; }
.comments h3 { margin: 0 0 16px; color: var(--blue-900); }
.composer { display: flex; gap: 8px; margin-bottom: 18px; }
.clist { display: flex; flex-direction: column; gap: 14px; }
.c { display: flex; gap: 10px; }
.cavatar {
  width: 34px; height: 34px; border-radius: 50%; background: var(--blue-50); color: var(--blue-700);
  display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; flex-shrink: 0;
}
.cuser { font-size: 11px; }
.ctext { font-size: 14px; line-height: 1.5; }
.empty { text-align: center; padding: 20px; }
@media (max-width: 860px) { .detail { grid-template-columns: 1fr; } }
</style>
