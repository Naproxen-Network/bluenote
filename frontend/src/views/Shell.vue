<template>
  <div>
    <header class="nav">
      <div class="container inner">
        <div class="left" @click="$router.push('/discover')">
          <div class="logo serif">L</div>
          <span class="wordmark serif">Little Blue Note</span>
        </div>

        <nav class="links">
          <router-link to="/discover" active-class="on">Discover</router-link>
          <router-link to="/publish" active-class="on">Publish</router-link>
          <router-link to="/me" active-class="on">Profile</router-link>
        </nav>

        <div class="right">
          <div class="searchbox">
            <input class="input" v-model="q" placeholder="Search posts, people, topics…" @keyup.enter="doSearch" />
            <button class="go" @click="doSearch">Search</button>
          </div>
          <img :src="avatar" class="me" @click="$router.push('/me')" @error="onErr" />
          <button class="btn ghost sm" @click="logout">Sign Out</button>
        </div>
      </div>
    </header>

    <main class="container page">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../store/auth";

const router = useRouter();
const auth = useAuth();
const q = ref("");

const avatar = computed(() => auth.user?.avatar || fallback);
const fallback =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='40' height='40'%3E%3Crect width='40' height='40' fill='%232a6fb5'/%3E%3C/svg%3E";

function onErr(e) { e.target.src = fallback; }
function doSearch() {
  router.push({ path: "/search", query: { q: q.value } });
}
function logout() {
  auth.logout();
  router.push("/login");
}
</script>

<style scoped>
.nav {
  position: sticky; top: 0; z-index: 50;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: saturate(140%) blur(12px);
  border-bottom: 1px solid var(--line);
}
.inner { display: flex; align-items: center; gap: 22px; height: 66px; }
.left { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.logo {
  width: 38px; height: 38px; border-radius: 11px; color: #fff; font-size: 22px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, var(--blue-700), var(--blue-400));
}
.wordmark { font-size: 20px; color: var(--blue-900); letter-spacing: 0.5px; }
.links { display: flex; gap: 6px; }
.links a {
  padding: 8px 14px; border-radius: 999px; font-weight: 600; color: var(--muted); font-size: 14px;
}
.links a.on { color: var(--blue-700); background: var(--blue-50); }
.right { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.searchbox { position: relative; }
.searchbox .input { width: 260px; padding-right: 72px; border-radius: 999px; }
.go {
  position: absolute; right: 4px; top: 4px; bottom: 4px; border: none; border-radius: 999px;
  padding: 0 16px; background: var(--blue-700); color: #fff; font-size: 13px; font-weight: 600;
}
.me { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; cursor: pointer; border: 1px solid var(--line); }
.page { padding: 26px 24px 60px; }
@media (max-width: 720px) { .searchbox .input { width: 150px; } .wordmark { display: none; } }
</style>
