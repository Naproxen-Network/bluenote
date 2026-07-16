<template>
  <div class="card post" @click="$router.push(`/post/${post.id}`)">
    <TopicImage :field="post.field" />
    <div class="body">
      <p class="content">{{ post.content }}</p>
      <div class="tags">
        <span class="tag">{{ post.field }}</span>
      </div>
      <div class="author">
        <img :src="post.authorAvatar || fallback" class="avatar" @error="onErr" />
        <div class="meta">
          <div class="name">{{ post.authorName || 'User #' + post.authorId }}</div>
          <div class="muted pos">{{ post.authorPosition }}</div>
        </div>
      </div>
      <div class="stats muted">
        <span>♡ {{ post.likeCount }}</span>
        <span>❏ {{ post.favoriteCount }}</span>
        <span>💬 {{ post.commentCount }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ post: Object });
const fallback =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='60' height='60'%3E%3Crect width='60' height='60' fill='%232a6fb5'/%3E%3C/svg%3E";
function onErr(e) {
  e.target.src = fallback;
}
</script>

<style scoped>
.post {
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  display: flex;
  flex-direction: column;
}
.post:hover { transform: translateY(-4px); box-shadow: var(--shadow-lg); }
.body { padding: 14px 16px 16px; display: flex; flex-direction: column; gap: 10px; }
.content {
  margin: 0;
  font-size: 14.5px;
  line-height: 1.55;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tags { display: flex; gap: 6px; flex-wrap: wrap; }
.author { display: flex; align-items: center; gap: 10px; margin-top: 2px; }
.avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; border: 1px solid var(--line); }
.name { font-size: 13px; font-weight: 600; }
.pos { font-size: 11px; max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.stats { display: flex; gap: 14px; font-size: 12px; border-top: 1px solid var(--line); padding-top: 10px; }
</style>
