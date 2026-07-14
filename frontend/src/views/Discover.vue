<template>
  <div class="discover">
    <div class="feed">
      <div class="head">
        <h2 class="serif">Discover</h2>
        <button class="btn ghost sm" :disabled="loading" @click="refresh">
          {{ loading ? 'Refreshing…' : 'Refresh' }}
        </button>
      </div>

      <p v-if="error" class="error card">{{ error }}</p>

      <div class="masonry" v-if="items.length">
        <PostCard v-for="it in items" :key="it.post.id" :post="it.post" />
      </div>
      <div v-else-if="!loading && !error" class="empty muted card">Nothing here yet. Tap Refresh to try again.</div>

      <div class="more" v-if="items.length && items.length < total">
        <button class="btn ghost" :disabled="loading" @click="loadMore">
          {{ loading ? 'Loading…' : 'Load More' }}
        </button>
      </div>
    </div>

    <aside class="side">
      <div class="card block">
        <h3 class="serif">People You May Like</h3>
        <div class="person" v-for="p in people" :key="p.id" @click="$router.push(`/profile/${p.id}`)">
          <img :src="p.avatar || fb" @error="onErr" />
          <div class="pinfo">
            <div class="name">{{ p.displayName }}</div>
            <div class="muted pos">{{ p.position }}</div>
          </div>
        </div>
        <div v-if="!people.length && !loading" class="muted small">Tap Refresh for suggestions</div>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import api from "../api";
import PostCard from "../components/PostCard.vue";

const items = ref([]);
const people = ref([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const error = ref("");
const fb = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='44' height='44'%3E%3Crect width='44' height='44' fill='%232a6fb5'/%3E%3C/svg%3E";

function onErr(e) { e.target.src = fb; }

function errMsg(e) {
  if (e?.response?.status === 401) return "Session expired. Please sign in again.";
  if (e?.response?.status === 503) return "Service temporarily unavailable. Please try again later.";
  if (e?.code === "ECONNABORTED") return "Request timed out. Check your connection and retry.";
  return e?.response?.data?.message || e?.message || "Failed to load. Please try again.";
}

async function reload(reset) {
  if (reset) { page.value = 1; items.value = []; }
  loading.value = true;
  error.value = "";
  try {
    const r = await api.get("/api/recommend/feed", { params: { page: page.value, size: 12 } });
    if (r.code === 0) {
      total.value = r.data.total;
      const recs = r.data.records || [];
      items.value = page.value === 1 ? recs : items.value.concat(recs);
    } else {
      error.value = r.message || "Failed to load";
    }
  } catch (e) {
    error.value = errMsg(e);
  } finally {
    loading.value = false;
  }
}

function loadMore() { page.value++; reload(false); }

async function loadPeople() {
  try {
    const r = await api.get("/api/recommend/people", { params: { n: 6 } });
    if (r.code === 0) {
      people.value = (r.data || []).map((x) => x.user).filter(Boolean);
    }
  } catch (_) { /* sidebar failure should not block the feed */ }
}

async function refresh() {
  await reload(true);
  await loadPeople();
}

onMounted(refresh);
</script>

<style scoped>
.discover { display: grid; grid-template-columns: 1fr 320px; gap: 26px; align-items: start; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
h2 { margin: 0; font-size: 30px; color: var(--blue-900); letter-spacing: 1px; }
.error { padding: 14px 18px; margin-bottom: 16px; color: #c0392b; font-size: 14px; background: #fff5f5; border-color: #f5c6c6; }
.masonry { columns: 3 220px; column-gap: 18px; }
.masonry > * { break-inside: avoid; margin-bottom: 18px; display: inline-block; width: 100%; }
.empty { padding: 40px; text-align: center; }
.more { text-align: center; margin-top: 10px; }
.side { position: sticky; top: 90px; }
.block { padding: 18px; }
.block h3 { margin: 0 0 14px; font-size: 17px; color: var(--blue-900); }
.person { display: flex; align-items: center; gap: 10px; padding: 8px 0; cursor: pointer; border-top: 1px solid var(--line); }
.person:first-of-type { border-top: none; }
.person img { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; border: 1px solid var(--line); }
.pinfo { flex: 1; min-width: 0; }
.pinfo .name { font-size: 13.5px; font-weight: 600; }
.pos { font-size: 11px; max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.small { font-size: 12px; text-align: center; padding: 12px 0; }
@media (max-width: 900px) { .discover { grid-template-columns: 1fr; } .side { position: static; } .masonry { columns: 2 160px; } }
</style>
