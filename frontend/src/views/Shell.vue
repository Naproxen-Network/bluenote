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
          <router-link to="/friends" active-class="on" class="navitem">
            Friends <span v-if="friends.incomingCount" class="badge">{{ cap(friends.incomingCount) }}</span>
          </router-link>
          <router-link to="/messages" active-class="on" class="navitem">
            Messages <span v-if="chat.unreadTotal" class="badge">{{ cap(chat.unreadTotal) }}</span>
          </router-link>
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
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../store/auth";
import { useFriendStore } from "../store/friend";
import { useChatStore } from "../store/chat";

const router = useRouter();
const auth = useAuth();
const friends = useFriendStore();
const chat = useChatStore();
const q = ref("");
const fallback = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='40' height='40'%3E%3Crect width='40' height='40' fill='%232a6fb5'/%3E%3C/svg%3E";
const avatar = computed(() => auth.user?.avatar || fallback);

function onErr(event) { event.target.src = fallback; }
function cap(value) { return value > 99 ? "99+" : value; }
function doSearch() { router.push({ path: "/search", query: { q: q.value } }); }
function logout() {
  chat.clear();
  friends.clear();
  auth.logout();
  router.push("/login");
}

onMounted(() => {
  chat.connect();
  friends.loadAll().catch(() => {});
  chat.loadConversations().catch(() => {});
});
onUnmounted(() => chat.disconnect());
</script>

<style scoped>
.nav { position: sticky; top: 0; z-index: 50; background: rgba(255, 255, 255, 0.82); backdrop-filter: saturate(140%) blur(12px); border-bottom: 1px solid var(--line); }
.inner { display: flex; align-items: center; gap: 22px; height: 66px; }
.left { display: flex; align-items: center; gap: 10px; cursor: pointer; flex: 0 0 auto; }
.logo { width: 38px; height: 38px; border-radius: 11px; color: #fff; font-size: 22px; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, var(--blue-700), var(--blue-400)); }
.wordmark { font-size: 20px; color: var(--blue-900); letter-spacing: 0.5px; }
.links { display: flex; gap: 4px; }
.links a { padding: 8px 11px; border-radius: 999px; font-weight: 600; color: var(--muted); font-size: 13px; }
.links a.on { color: var(--blue-700); background: var(--blue-50); }
.navitem { display: inline-flex; align-items: center; gap: 6px; }
.badge { min-width: 18px; height: 18px; padding: 0 5px; border-radius: 999px; display: inline-flex; align-items: center; justify-content: center; background: #d94d5c; color: #fff; font-size: 10px; line-height: 1; }
.right { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.searchbox { position: relative; }
.searchbox .input { width: 220px; padding-right: 72px; border-radius: 999px; }
.go { position: absolute; right: 4px; top: 4px; bottom: 4px; border: none; border-radius: 999px; padding: 0 16px; background: var(--blue-700); color: #fff; font-size: 13px; font-weight: 600; }
.me { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; cursor: pointer; border: 1px solid var(--line); }
.page { padding: 26px 24px 60px; }
@media (max-width: 1100px) { .searchbox { display: none; } .wordmark { display: none; } .inner { gap: 8px; } .links a { padding: 8px 9px; } }
@media (max-width: 680px) { .links { overflow-x: auto; } .links a { white-space: nowrap; } .me, .right .btn { display: none; } }
</style>
