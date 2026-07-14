<template>
  <div class="topic" :style="{ background: grad, height: height }">
    <div class="glyph serif">{{ glyph }}</div>
    <div class="label">{{ field }}</div>
  </div>
</template>

<script setup>
import { computed } from "vue";
const props = defineProps({
  field: { type: String, default: "Governance & Reform" },
  height: { type: String, default: "170px" },
});

const MAP = {
  "Law & Justice": ["#1e5fa8", "#5b8fc7", "§"],
  "Medicine & Health": ["#2a7fb0", "#7cc0d8", "✚"],
  "Defense & Security": ["#26456e", "#5a7fa8", "◈"],
  "Economics & Finance": ["#1f6f6a", "#68b3a6", "$"],
  "Environment & Energy": ["#2c7a5a", "#7cc79b", "❦"],
  "Education & Science": ["#3a5aa8", "#8aa7de", "∴"],
  "Foreign Relations": ["#3c4f8a", "#8b9bd0", "✺"],
  "Agriculture & Rural": ["#6a7a2c", "#b6c479", "✿"],
  "Civil Rights & Society": ["#8a4a6a", "#c990ac", "☙"],
  "Governance & Reform": ["#1e5fa8", "#6ba3d6", "⚖"],
};

const conf = computed(() => MAP[props.field] || MAP["Governance & Reform"]);
const grad = computed(
  () => `linear-gradient(135deg, ${conf.value[0]} 0%, ${conf.value[1]} 100%)`
);
const glyph = computed(() => conf.value[2]);
</script>

<style scoped>
.topic {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 14px;
  color: #fff;
  overflow: hidden;
}
.glyph {
  position: absolute;
  top: -14px;
  right: 6px;
  font-size: 96px;
  opacity: 0.22;
  line-height: 1;
}
.label {
  font-size: 12px;
  letter-spacing: 0.4px;
  font-weight: 600;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.25);
}
</style>
