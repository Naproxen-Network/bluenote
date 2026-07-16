<template>
  <div class="search">
    <div class="bar">
      <input class="input" v-model="q" placeholder="Search posts, people, topics…" @keyup.enter="run(true)" />
      <button class="btn" @click="run(true)">Search</button>
    </div>

    <div v-if="loading" class="muted status">Searching…</div>
    <template v-else>
      <p v-if="q && total !== null" class="muted status">{{ total }} results for "{{ lastQ }}"</p>
      <div class="masonry" v-if="items.length">
        <PostCard v-for="it in items" :key="it.post.id" :post="it.post" />
      </div>
      <div v-else-if="lastQ" class="empty muted card">No matching posts. Try a different keyword.</div>
    </template>

    <div class="more" v-if="items.length < total">
      <button class="btn ghost" @click="loadMore">Load More</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from "vue";
import { useRoute } from "vue-router";
import api from "../api";
import PostCard from "../components/PostCard.vue";

const route = useRoute();
const q = ref(route.query.q || "");
const lastQ = ref("");
const items = ref([]);
const total = ref(null);
const page = ref(1);
const loading = ref(false);

async function run(reset) {
  if (!q.value.trim()) return;
  if (reset) { page.value = 1; items.value = []; }
  loading.value = true;
  lastQ.value = q.value;
  try {
    const r = await api.get("/api/search", { params: { q: q.value, page: page.value, size: 12 } });
    if (r.code === 0) {
      total.value = r.data.total;
      const recs = r.data.records || [];
      items.value = page.value === 1 ? recs : items.value.concat(recs);
    }
  } finally { loading.value = false; }
}
function loadMore() { page.value++; run(false); }

watch(() => route.query.q, (nv) => { if (nv !== undefined) { q.value = nv; run(true); } });
onMounted(() => { if (q.value) run(true); });
</script>

<style scoped>
.search { max-width: 1000px; margin: 0 auto; }
.bar { display: flex; gap: 10px; }
.bar .input { flex: 1; border-radius: 999px; }
.status { margin: 14px 2px 4px; font-size: 13px; }
.masonry { columns: 3 220px; column-gap: 18px; margin-top: 8px; }
.masonry > * { break-inside: avoid; margin-bottom: 18px; display: inline-block; width: 100%; }
.empty { padding: 40px; text-align: center; }
.more { text-align: center; margin-top: 12px; }
@media (max-width: 820px) { .masonry { columns: 2 160px; } }
</style>
