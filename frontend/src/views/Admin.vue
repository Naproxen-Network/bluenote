<template>
  <div class="admin">
    <header class="admin-bar">
      <div class="container inner">
        <div class="brand serif"><span class="logo">L</span> Little Blue Note · Admin</div>
        <div class="right">
          <span>Admin: {{ auth.adminName || 'admin' }}</span>
          <button class="btn ghost sm" @click="logout">Sign Out</button>
        </div>
      </div>
    </header>

    <div class="container body">
      <div v-if="error" class="alert">{{ error }}</div>
      <div class="stats">
        <div v-for="item in statCards" :key="item.key" class="card stat">
          <div class="num serif">{{ item.value ?? 0 }}</div>
          <div class="muted">{{ item.label }}</div>
        </div>
      </div>

      <div class="tabs">
        <button :class="{ on: tab === 'users' }" @click="tab = 'users'">Users</button>
        <button :class="{ on: tab === 'reports' }" @click="tab = 'reports'">Message Reports</button>
        <button :class="{ on: tab === 'restrictions' }" @click="tab = 'restrictions'">Restrictions</button>
        <button :class="{ on: tab === 'delivery' }" @click="tab = 'delivery'">Delivery Queue</button>
      </div>

      <section v-if="tab === 'users'" class="card panel">
        <div class="panel-head">
          <h3 class="serif">All Users</h3>
          <input class="input" v-model="keyword" placeholder="Search name / role / field" @keyup.enter="loadUsers(1)" />
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>ID</th><th>User</th><th>Party</th><th>Position</th><th>Moderation</th></tr></thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td>{{ user.id }}</td>
                <td class="user-cell">
                  <img :src="user.avatar || fallback" @error="onImageError" />
                  <div><div class="name">{{ user.displayName }}</div><div class="muted small">{{ (user.interests || []).join(' · ') }}</div></div>
                </td>
                <td>{{ user.party }}</td>
                <td class="position">{{ user.position }}</td>
                <td><button class="link-button" @click="prepareRestriction(user)">Restrict</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pager :page="userPage" :total="userTotal" :size="pageSize" @change="loadUsers" />
      </section>

      <section v-else-if="tab === 'reports'" class="card panel">
        <div class="panel-head">
          <div><h3 class="serif">Message Reports</h3><p class="muted">Review user reports without exposing messages to ordinary users.</p></div>
          <select class="input select" v-model="reportStatus" @change="loadReports(1)"><option value="">All statuses</option><option value="OPEN">Open</option><option value="RESOLVED">Resolved</option></select>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>ID</th><th>Reporter</th><th>Reported User</th><th>Message</th><th>Type / Description</th><th>Status</th><th>Action</th></tr></thead>
            <tbody>
              <tr v-for="report in reports" :key="report.id">
                <td>{{ report.id }}</td><td>{{ report.reporterId }}</td><td>{{ report.reportedUserId }}</td><td>#{{ report.messageId }}</td>
                <td><strong>{{ report.reportType }}</strong><div class="muted small">{{ report.description || 'No description' }}</div></td>
                <td><span class="status" :class="report.status.toLowerCase()">{{ report.status }}</span></td>
                <td><button v-if="report.status === 'OPEN'" class="link-button" @click="resolveReport(report)">Resolve</button><span v-else class="muted small">{{ report.resolution }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pager :page="reportPage" :total="reportTotal" :size="pageSize" @change="loadReports" />
      </section>

      <section v-else-if="tab === 'restrictions'" class="restriction-layout">
        <form class="card restrict-form" @submit.prevent="createRestriction">
          <h3 class="serif">Add Restriction</h3>
          <label>User ID</label><input class="input" v-model.number="restrictionForm.userId" type="number" min="1" required />
          <label>Type</label><select class="input" v-model="restrictionForm.type"><option value="CHAT_BAN">Chat ban</option><option value="FRIEND_BAN">Friend-request ban</option></select>
          <label>End time <span class="muted">(blank = indefinite)</span></label><input class="input" v-model="restrictionForm.endsAt" type="datetime-local" />
          <label>Reason</label><textarea class="input" v-model.trim="restrictionForm.reason" rows="4" maxlength="500" required></textarea>
          <button class="btn" :disabled="busy">Apply Restriction</button>
        </form>
        <div class="card panel restrictions-panel">
          <div class="panel-head"><h3 class="serif">Restriction History</h3><button class="btn ghost sm" @click="loadRestrictions(1)">Refresh</button></div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>ID</th><th>User</th><th>Type</th><th>Period</th><th>Reason</th><th>Status</th></tr></thead>
              <tbody>
                <tr v-for="restriction in restrictions" :key="restriction.id">
                  <td>{{ restriction.id }}</td><td>{{ restriction.userId }}</td><td>{{ restriction.restrictionType }}</td>
                  <td class="small">{{ formatTime(restriction.startsAt) }}<br />{{ restriction.endsAt ? `to ${formatTime(restriction.endsAt)}` : 'indefinite' }}</td>
                  <td>{{ restriction.reason }}</td>
                  <td><button v-if="restriction.active" class="link-button danger" @click="liftRestriction(restriction.id)">Lift</button><span v-else class="muted">Lifted</span></td>
                </tr>
              </tbody>
            </table>
          </div>
          <Pager :page="restrictionPage" :total="restrictionTotal" :size="pageSize" @change="loadRestrictions" />
        </div>
      </section>

      <section v-else class="card panel">
        <div class="panel-head">
          <div><h3 class="serif">Real-time Delivery Queue</h3><p class="muted">Database outbox events remain durable until RabbitMQ confirms them.</p></div>
          <select class="input select" v-model="outboxStatus" @change="loadOutbox(1)"><option value="">All statuses</option><option value="PENDING">Pending</option><option value="PROCESSING">Processing</option><option value="FAILED">Failed</option><option value="PUBLISHED">Published</option></select>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>ID</th><th>Event</th><th>Aggregate</th><th>Status</th><th>Retries</th><th>Next Attempt / Published</th><th>Last Error</th><th>Action</th></tr></thead>
            <tbody>
              <tr v-for="event in outboxEvents" :key="event.id">
                <td>{{ event.id }}</td><td><strong>{{ event.eventType }}</strong><div class="muted small event-id">{{ event.eventId }}</div></td>
                <td>{{ event.aggregateId || '—' }}</td><td><span class="status" :class="event.status.toLowerCase()">{{ event.status }}</span></td><td>{{ event.retryCount }}</td>
                <td class="small">{{ formatTime(event.publishedAt || event.nextRetryAt) }}</td><td class="error-cell">{{ event.lastError || '—' }}</td>
                <td><button v-if="event.status === 'FAILED'" class="link-button" @click="retryOutbox(event.id)">Retry now</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pager :page="outboxPage" :total="outboxTotal" :size="pageSize" @change="loadOutbox" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import api from "../api";
import { useAuth } from "../store/auth";

const Pager = defineComponent({
  props: { page: Number, total: Number, size: Number },
  emits: ["change"],
  setup(props, { emit }) {
    return () => h("div", { class: "pager" }, [
      h("button", { class: "btn ghost sm", disabled: props.page <= 1, onClick: () => emit("change", props.page - 1) }, "Previous"),
      h("span", { class: "muted" }, `Page ${props.page} of ${Math.max(1, Math.ceil(props.total / props.size))}`),
      h("button", { class: "btn ghost sm", disabled: props.page * props.size >= props.total, onClick: () => emit("change", props.page + 1) }, "Next"),
    ]);
  },
});

const auth = useAuth();
const router = useRouter();
const tab = ref("users");
const users = ref([]);
const reports = ref([]);
const restrictions = ref([]);
const outboxEvents = ref([]);
const userStats = reactive({});
const chatStats = reactive({});
const keyword = ref("");
const reportStatus = ref("OPEN");
const outboxStatus = ref("");
const userPage = ref(1);
const reportPage = ref(1);
const restrictionPage = ref(1);
const outboxPage = ref(1);
const userTotal = ref(0);
const reportTotal = ref(0);
const restrictionTotal = ref(0);
const outboxTotal = ref(0);
const pageSize = 15;
const error = ref("");
const busy = ref(false);
const restrictionForm = reactive({ userId: null, type: "CHAT_BAN", reason: "", endsAt: "" });
const fallback = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='36' height='36'%3E%3Crect width='36' height='36' fill='%232a6fb5'/%3E%3C/svg%3E";

const statCards = computed(() => [
  { key: "users", label: "Registered Users", value: userStats.totalUsers },
  { key: "online", label: "Online Now", value: userStats.onlineUsers },
  { key: "friends", label: "Active Friendships", value: chatStats.activeFriendships },
  { key: "pending", label: "Pending Requests", value: chatStats.pendingFriendRequests },
  { key: "conversations", label: "Active Conversations", value: chatStats.activeConversations },
  { key: "messages", label: "Messages Today", value: chatStats.messagesToday },
  { key: "reports", label: "Open Reports", value: chatStats.openReports },
  { key: "outbox", label: "Delivery Backlog", value: chatStats.outboxBacklog },
]);

function onImageError(event) { event.target.src = fallback; }
function formatTime(value) { return value ? new Date(value).toLocaleString() : ""; }
function errorMessage(exception) { return exception.response?.data?.message || exception.message || "Operation failed"; }
function logout() { auth.logout(); router.push("/login"); }

async function loadUsers(page) {
  userPage.value = page;
  try {
    const response = await api.get("/api/admin/users", { params: { page, size: pageSize, keyword: keyword.value } });
    if (response.code !== 0) throw new Error(response.message);
    users.value = response.data.records || [];
    userTotal.value = response.data.total || 0;
  } catch (exception) { error.value = errorMessage(exception); }
}
async function loadStats() {
  try {
    const [usersResponse, chatResponse] = await Promise.all([api.get("/api/admin/stats"), api.get("/api/chat-admin/stats")]);
    if (usersResponse.code === 0) Object.assign(userStats, usersResponse.data);
    if (chatResponse.code === 0) Object.assign(chatStats, chatResponse.data);
  } catch (exception) { error.value = errorMessage(exception); }
}
async function loadReports(page) {
  reportPage.value = page;
  try {
    const response = await api.get("/api/chat-admin/reports", { params: { page, size: pageSize, status: reportStatus.value || undefined } });
    if (response.code !== 0) throw new Error(response.message);
    reports.value = response.data.records || [];
    reportTotal.value = response.data.total || 0;
  } catch (exception) { error.value = errorMessage(exception); }
}
async function resolveReport(report) {
  const resolution = window.prompt("Resolution notes:", "Reviewed and handled");
  if (!resolution) return;
  try {
    const response = await api.post(`/api/chat-admin/reports/${report.id}/resolve`, { resolution });
    if (response.code !== 0) throw new Error(response.message);
    await Promise.all([loadReports(reportPage.value), loadStats()]);
  } catch (exception) { error.value = errorMessage(exception); }
}
function prepareRestriction(user) {
  restrictionForm.userId = user.id;
  restrictionForm.reason = `Administrative restriction for ${user.displayName}`;
  tab.value = "restrictions";
}
async function loadRestrictions(page) {
  restrictionPage.value = page;
  try {
    const response = await api.get("/api/chat-admin/restrictions", { params: { page, size: pageSize } });
    if (response.code !== 0) throw new Error(response.message);
    restrictions.value = response.data.records || [];
    restrictionTotal.value = response.data.total || 0;
  } catch (exception) { error.value = errorMessage(exception); }
}
async function createRestriction() {
  busy.value = true;
  error.value = "";
  try {
    const response = await api.post("/api/chat-admin/restrictions", {
      userId: Number(restrictionForm.userId),
      type: restrictionForm.type,
      reason: restrictionForm.reason,
      endsAt: restrictionForm.endsAt || null,
    });
    if (response.code !== 0) throw new Error(response.message);
    restrictionForm.reason = "";
    restrictionForm.endsAt = "";
    await Promise.all([loadRestrictions(1), loadStats()]);
  } catch (exception) { error.value = errorMessage(exception); }
  finally { busy.value = false; }
}
async function liftRestriction(id) {
  if (!window.confirm("Lift this restriction?")) return;
  try {
    const response = await api.delete(`/api/chat-admin/restrictions/${id}`);
    if (response.code !== 0) throw new Error(response.message);
    await Promise.all([loadRestrictions(restrictionPage.value), loadStats()]);
  } catch (exception) { error.value = errorMessage(exception); }
}

async function loadOutbox(page) {
  outboxPage.value = page;
  try {
    const response = await api.get("/api/chat-admin/outbox", { params: { page, size: pageSize, status: outboxStatus.value || undefined } });
    if (response.code !== 0) throw new Error(response.message);
    outboxEvents.value = response.data.records || [];
    outboxTotal.value = response.data.total || 0;
  } catch (exception) { error.value = errorMessage(exception); }
}
async function retryOutbox(id) {
  try {
    const response = await api.post(`/api/chat-admin/outbox/${id}/retry`);
    if (response.code !== 0) throw new Error(response.message);
    await Promise.all([loadOutbox(outboxPage.value), loadStats()]);
  } catch (exception) { error.value = errorMessage(exception); }
}

onMounted(() => Promise.all([loadUsers(1), loadStats(), loadReports(1), loadRestrictions(1), loadOutbox(1)]));
</script>

<style scoped>
.admin-bar { background: var(--blue-900); color: #fff; position: sticky; top: 0; z-index: 20; }
.inner { display: flex; align-items: center; height: 60px; }
.brand { font-size: 18px; letter-spacing: 1px; display: flex; align-items: center; gap: 10px; }
.logo { background: #fff; color: var(--blue-900); width: 32px; height: 32px; border-radius: 9px; display: inline-flex; align-items: center; justify-content: center; font-weight: 700; }
.right { margin-left: auto; display: flex; align-items: center; gap: 14px; color: #cfe0f0; }
.body { padding: 26px 24px 60px; }
.alert { padding: 11px 14px; margin-bottom: 14px; color: #a63643; background: #fff0f2; border: 1px solid #f0c7cc; border-radius: 10px; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 22px; }
.stat { padding: 17px 19px; }
.num { font-size: 29px; color: var(--blue-700); line-height: 1; }
.stat .muted { margin-top: 8px; font-size: 12px; }
.tabs { display: flex; gap: 8px; margin-bottom: 14px; }
.tabs button { border: 1px solid var(--line); border-radius: 999px; padding: 8px 16px; background: #fff; color: var(--muted); }
.tabs button.on { color: #fff; background: var(--blue-700); border-color: var(--blue-700); }
.panel { padding: 20px 22px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; gap: 14px; margin-bottom: 14px; }
.panel-head h3 { margin: 0; color: var(--blue-900); }
.panel-head p { margin: 4px 0 0; font-size: 12px; }
.panel-head .input { width: 280px; }
.panel-head .select { width: 160px; }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; color: var(--muted); font-weight: 600; padding: 9px 8px; border-bottom: 2px solid var(--line); white-space: nowrap; }
td { padding: 9px 8px; border-bottom: 1px solid var(--line); vertical-align: middle; }
.user-cell { display: flex; align-items: center; gap: 10px; }
.user-cell img { width: 34px; height: 34px; border-radius: 50%; object-fit: cover; }
.name { font-weight: 600; }.small { font-size: 11px; }.position { max-width: 260px; color: var(--muted); }
.pager { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 16px; font-size: 13px; }
.link-button { border: none; background: transparent; color: var(--blue-600); padding: 4px; font-weight: 600; }
.link-button.danger { color: #ae3c49; }
.status { border-radius: 999px; padding: 3px 9px; font-size: 10px; background: #eef1f4; }
.status.open { color: #a35e19; background: #fff3df; }.status.resolved { color: #2e7449; background: #e9f7ef; }
.status.failed { color: #a63643; background: #fff0f2; }.status.pending, .status.processing { color: #865c20; background: #fff4df; }.status.published { color: #2e7449; background: #e9f7ef; }
.event-id { max-width: 190px; overflow: hidden; text-overflow: ellipsis; }.error-cell { max-width: 260px; color: #a63643; font-size: 11px; }
.restriction-layout { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 16px; align-items: start; }
.restrict-form { display: flex; flex-direction: column; gap: 9px; padding: 20px; }
.restrict-form h3 { margin: 0 0 5px; color: var(--blue-900); }.restrict-form label { color: var(--muted); font-size: 12px; }.restrict-form textarea { resize: vertical; }
@media (max-width: 1000px) { .stats { grid-template-columns: repeat(2, 1fr); } .restriction-layout { grid-template-columns: 1fr; } }
@media (max-width: 620px) { .stats { grid-template-columns: 1fr; } .panel-head { flex-direction: column; align-items: stretch; } .panel-head .input { width: 100%; } }
</style>
