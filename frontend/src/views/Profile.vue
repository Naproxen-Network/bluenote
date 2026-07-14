<template>
  <div v-if="user" class="profile">
    <div class="card hero">
      <div class="cover" :style="coverStyle"></div>
      <div class="headline">
        <img :src="user.avatar || fb" class="pfp" @error="onErr" />
        <div class="idblock">
          <h2 class="serif">{{ user.displayName }}</h2>
          <div class="sub muted">{{ user.position || '—' }}</div>
          <div class="chips">
            <span class="tag" v-if="user.party">{{ user.party }}</span>
            <span class="tag" v-if="user.state">{{ user.state }}</span>
          </div>
        </div>
        <div class="ops">
          <button v-if="!isMe" class="btn" :class="{ ghost: following }" @click="toggleFollow">
            {{ following ? 'Following' : 'Follow' }}
          </button>
          <button v-else class="btn ghost" @click="editing = !editing">
            {{ editing ? 'Done' : 'Edit Profile' }}
          </button>
        </div>
      </div>

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
            <span class="tag" v-for="i in user.interests" :key="i">{{ i }}</span>
          </div>
          <div class="edu muted" v-if="user.almaMater">🎓 {{ user.almaMater }}</div>
        </template>
      </div>
    </div>

    <h3 class="serif sechead">Posts · {{ posts.length }}</h3>
    <div class="masonry" v-if="posts.length">
      <PostCard v-for="p in posts" :key="p.id" :post="p" />
    </div>
    <div v-else class="muted empty card">No posts yet.</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRoute } from "vue-router";
import api from "../api";
import PostCard from "../components/PostCard.vue";
import { useAuth } from "../store/auth";

const route = useRoute();
const auth = useAuth();
const user = ref(null);
const posts = ref([]);
const following = ref(false);
const editing = ref(false);
const bioDraft = ref("");
const interestDraft = ref("");
const fb = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='96' height='96'%3E%3Crect width='96' height='96' fill='%232a6fb5'/%3E%3C/svg%3E";

const targetId = computed(() => Number(route.params.id) || auth.user?.id);
const isMe = computed(() => targetId.value === auth.user?.id);
const coverStyle = computed(() => ({
  background: "linear-gradient(120deg, #1e5fa8, #6ba3d6 60%, #bcd8ef)",
}));
function onErr(e) { e.target.src = fb; }

async function load() {
  const id = targetId.value;
  const r = await api.get(`/api/user/${id}`);
  if (r.code === 0) {
    user.value = r.data;
    bioDraft.value = r.data.bio || "";
    interestDraft.value = (r.data.interests || []).join(" ");
  }
  const p = await api.get("/api/post/page", { params: { authorId: id, size: 30 } });
  if (p.code === 0) posts.value = p.data.records || [];
  if (!isMe.value) {
    const f = await api.get(`/api/user/${id}/isFollowing`);
    if (f.code === 0) following.value = f.data;
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
async function save() {
  const r = await api.put("/api/user/me", {
    bio: bioDraft.value,
    interests: interestDraft.value.split(/\s+/).filter(Boolean).join("|"),
  });
  if (r.code === 0) { editing.value = false; load(); }
}

onMounted(load);
watch(() => route.params.id, load);
</script>

<style scoped>
.profile { max-width: 1040px; margin: 0 auto; }
.hero { overflow: hidden; margin-bottom: 26px; }
.cover { height: 140px; }
.headline { display: flex; align-items: flex-end; gap: 18px; padding: 0 28px; margin-top: -46px; }
.pfp { width: 96px; height: 96px; border-radius: 24px; object-fit: cover; border: 4px solid #fff; box-shadow: var(--shadow); background: #fff; }
.idblock { flex: 1; padding-bottom: 6px; }
.idblock h2 { margin: 0; color: var(--blue-900); font-size: 26px; }
.sub { font-size: 13px; margin-top: 3px; }
.chips { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.ops { padding-bottom: 8px; }
.about { padding: 20px 28px 26px; display: flex; flex-direction: column; gap: 10px; }
.about label { font-size: 12px; margin-top: 6px; }
.bio { font-family: var(--serif); font-size: 15px; line-height: 1.8; margin: 0; color: var(--ink); }
.interests { display: flex; gap: 8px; flex-wrap: wrap; }
.edu { font-size: 13px; }
.sechead { color: var(--blue-900); margin: 0 0 16px; }
.masonry { columns: 3 220px; column-gap: 18px; }
.masonry > * { break-inside: avoid; margin-bottom: 18px; display: inline-block; width: 100%; }
.empty { padding: 34px; text-align: center; }
@media (max-width: 820px) { .masonry { columns: 2 150px; } .headline { flex-wrap: wrap; } }
</style>
