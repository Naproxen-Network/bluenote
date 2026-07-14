<template>
  <div class="admin">
    <header class="abar">
      <div class="container inner">
        <div class="brand serif"><span class="logo">L</span> Little Blue Note · Admin</div>
        <div class="right">
          <span class="muted">Admin: {{ auth.adminName || 'admin' }}</span>
          <button class="btn ghost sm" @click="logout">Sign Out</button>
        </div>
      </div>
    </header>

    <div class="container body">
      <div class="stats">
        <div class="card stat">
          <div class="num serif">{{ stats.totalUsers ?? '—' }}</div>
          <div class="muted">Registered Users</div>
        </div>
        <div class="card stat">
          <div class="num serif">{{ stats.onlineUsers ?? 0 }}</div>
          <div class="muted">Online Now</div>
        </div>
        <div class="card stat">
          <div class="num serif">{{ stats.totalFollows ?? 0 }}</div>
          <div class="muted">Follows</div>
        </div>
      </div>

      <div class="card panel">
        <div class="phead">
          <h3 class="serif">All Users</h3>
          <input class="input" v-model="kw" placeholder="Search name / role / field" @keyup.enter="load(1)" />
        </div>
        <table>
          <thead>
            <tr><th>ID</th><th>User</th><th>Party</th><th>Position</th></tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id">
              <td>{{ u.id }}</td>
              <td class="ucell">
                <img :src="u.avatar || fb" @error="onErr" />
                <div>
                  <div class="name">{{ u.displayName }}</div>
                  <div class="muted small">{{ (u.interests || []).join(' · ') }}</div>
                </div>
              </td>
              <td>{{ u.party }}</td>
              <td class="pos">{{ u.position }}</td>
            </tr>
          </tbody>
        </table>
        <div class="pager">
          <button class="btn ghost sm" :disabled="page <= 1" @click="load(page - 1)">Previous</button>
          <span class="muted">Page {{ page }} of {{ Math.ceil(total / size) }}</span>
          <button class="btn ghost sm" :disabled="page * size >= total" @click="load(page + 1)">Next</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import api from "../api";
import { useAuth } from "../store/auth";

const auth = useAuth();
const router = useRouter();
const users = ref([]);
const stats = reactive({});
const kw = ref("");
const page = ref(1);
const size = 15;
const total = ref(0);
const fb = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='36' height='36'%3E%3Crect width='36' height='36' fill='%232a6fb5'/%3E%3C/svg%3E";

function onErr(e) { e.target.src = fb; }

async function load(p) {
  page.value = p;
  const r = await api.get("/api/admin/users", { params: { page: p, size, keyword: kw.value } });
  if (r.code === 0) { users.value = r.data.records; total.value = r.data.total; }
}
async function loadStats() {
  const r = await api.get("/api/admin/stats");
  if (r.code === 0) Object.assign(stats, r.data);
}
function logout() { auth.logout(); router.push("/login"); }

onMounted(() => { load(1); loadStats(); });
</script>

<style scoped>
.abar { background: var(--blue-900); color: #fff; position: sticky; top: 0; z-index: 20; }
.inner { display: flex; align-items: center; height: 60px; }
.brand { font-size: 18px; letter-spacing: 1px; display: flex; align-items: center; gap: 10px; }
.logo { background: #fff; color: var(--blue-900); width: 32px; height: 32px; border-radius: 9px; display: inline-flex; align-items: center; justify-content: center; font-weight: 700; }
.right { margin-left: auto; display: flex; align-items: center; gap: 14px; }
.right .muted { color: #cfe0f0; }
.body { padding: 26px 24px 60px; }
.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; margin-bottom: 22px; }
.stat { padding: 20px 22px; }
.num { font-size: 34px; color: var(--blue-700); line-height: 1; }
.stat .muted { margin-top: 8px; font-size: 13px; }
.panel { padding: 20px 22px; }
.phead { display: flex; justify-content: space-between; align-items: center; gap: 14px; margin-bottom: 14px; }
.phead h3 { margin: 0; color: var(--blue-900); }
.phead .input { width: 260px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; color: var(--muted); font-weight: 600; padding: 8px; border-bottom: 2px solid var(--line); }
td { padding: 8px; border-bottom: 1px solid var(--line); vertical-align: middle; }
.ucell { display: flex; align-items: center; gap: 10px; }
.ucell img { width: 34px; height: 34px; border-radius: 50%; object-fit: cover; }
.name { font-weight: 600; }
.small { font-size: 11px; }
.pos { max-width: 280px; color: var(--muted); }
.pager { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 16px; font-size: 13px; }
@media (max-width: 900px) { .stats { grid-template-columns: 1fr; } .phead { flex-direction: column; align-items: stretch; } }
</style>
