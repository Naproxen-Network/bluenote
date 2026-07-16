<template>
  <div class="publish">
    <div class="card panel">
      <h2 class="serif">New Post</h2>
      <p class="muted">Share a thought or observation.</p>

      <label>Topic</label>
      <div class="fields">
        <button v-for="f in fields" :key="f" class="fchip" :class="{ on: field === f }" @click="field = f">
          {{ f }}
        </button>
      </div>

      <label>Content</label>
      <textarea class="input area" v-model="content" rows="4" maxlength="280"
                placeholder="A sentence or two is enough — e.g. Preventive care is the highest-return investment we keep underfunding."></textarea>
      <div class="count muted">{{ content.length }}/280</div>

      <label>Tags (space-separated, optional)</label>
      <input class="input" v-model="tags" placeholder="#Research #Policy" />

      <div class="preview">
        <span class="muted">Preview</span>
        <TopicImage :field="field" height="120px" />
      </div>

      <button class="btn" :disabled="!content.trim() || loading" @click="publish">
        {{ loading ? 'Publishing…' : 'Publish' }}
      </button>
      <p v-if="msg" class="ok">{{ msg }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import api from "../api";
import TopicImage from "../components/TopicImage.vue";

const fields = [
  "Law & Justice", "Medicine & Health", "Defense & Security", "Economics & Finance",
  "Environment & Energy", "Education & Science", "Foreign Relations",
  "Agriculture & Rural", "Civil Rights & Society", "Governance & Reform",
];
const field = ref(fields[5]);
const content = ref("");
const tags = ref("");
const loading = ref(false);
const msg = ref("");
const router = useRouter();

async function publish() {
  loading.value = true;
  msg.value = "";
  try {
    const r = await api.post("/api/post/publish", {
      field: field.value, content: content.value.trim(), tags: tags.value.trim(),
    });
    if (r.code === 0) {
      msg.value = "Published successfully";
      setTimeout(() => router.push(`/post/${r.data.id}`), 700);
    } else msg.value = r.message;
  } finally { loading.value = false; }
}
</script>

<style scoped>
.publish { display: flex; justify-content: center; }
.panel { width: 640px; max-width: 100%; padding: 30px; display: flex; flex-direction: column; gap: 8px; }
h2 { margin: 0; color: var(--blue-900); }
label { font-size: 13px; color: var(--muted); margin-top: 14px; font-weight: 600; }
.fields { display: flex; flex-wrap: wrap; gap: 8px; }
.fchip {
  border: 1px solid var(--line); background: #fff; border-radius: 999px; padding: 7px 13px;
  font-size: 12.5px; color: var(--muted);
}
.fchip.on { background: var(--blue-50); color: var(--blue-700); border-color: var(--blue-200); font-weight: 600; }
.area { resize: vertical; line-height: 1.6; font-family: var(--serif); }
.count { font-size: 12px; text-align: right; }
.preview { margin: 12px 0; border-radius: 12px; overflow: hidden; border: 1px solid var(--line); }
.preview .muted { display: block; padding: 8px 12px; font-size: 12px; }
.btn { margin-top: 18px; }
.ok { color: var(--blue-700); font-size: 13px; }
</style>
