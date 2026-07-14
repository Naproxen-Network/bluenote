<template>
  <div class="wrap">
    <div class="brand">
      <div class="logo serif">L</div>
      <h1 class="serif">Little Blue Note</h1>
      <p class="sub">Capture ideas, share insight, connect with peers.</p>
    </div>

    <div class="card panel">
      <div class="tabs">
        <button :class="{ on: tab === 'user' }" @click="tab = 'user'">Sign In</button>
        <button :class="{ on: tab === 'admin' }" @click="tab = 'admin'">Admin</button>
      </div>

      <div class="form">
        <label>Username</label>
        <input class="input" v-model="username" :placeholder="tab === 'admin' ? 'admin' : 'e.g. ervin'" />
        <label>Password</label>
        <input class="input" type="password" v-model="password"
               placeholder="Enter password" @keyup.enter="submit" />

        <button class="btn" :disabled="loading" @click="submit">
          {{ loading ? 'Signing in…' : (tab === 'admin' ? 'Enter Admin Console' : 'Enter Little Blue Note') }}
        </button>
        <p v-if="error" class="err">{{ error }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../store/auth";

const tab = ref("user");
const username = ref("");
const password = ref("");
const loading = ref(false);
const error = ref("");
const auth = useAuth();
const router = useRouter();

async function submit() {
  error.value = "";
  loading.value = true;
  try {
    if (tab.value === "admin") {
      await auth.adminLogin(username.value.trim(), password.value);
      router.push("/admin");
    } else {
      await auth.login(username.value.trim(), password.value);
      router.push("/discover");
    }
  } catch (e) {
    error.value = e.response?.data?.message || e.message || "Sign in failed";
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.wrap {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  align-items: center;
  gap: 40px;
  max-width: 1080px;
  margin: 0 auto;
  padding: 40px 24px;
}
.brand { padding: 20px; }
.logo {
  width: 84px; height: 84px; border-radius: 22px;
  background: linear-gradient(135deg, var(--blue-700), var(--blue-400));
  color: #fff; font-size: 48px; display: flex; align-items: center; justify-content: center;
  box-shadow: var(--shadow-lg); margin-bottom: 22px;
}
h1 { font-size: 44px; margin: 0 0 6px; letter-spacing: 1px; color: var(--blue-900); }
.sub { color: var(--blue-700); font-size: 15px; margin: 0; letter-spacing: 0.5px; line-height: 1.8; }
.panel { padding: 30px; }
.tabs { display: flex; gap: 8px; background: var(--blue-50); padding: 5px; border-radius: 12px; margin-bottom: 22px; }
.tabs button {
  flex: 1; border: none; background: transparent; padding: 10px; border-radius: 9px;
  font-weight: 600; color: var(--muted);
}
.tabs button.on { background: #fff; color: var(--blue-700); box-shadow: var(--shadow); }
.form { display: flex; flex-direction: column; gap: 8px; }
.form label { font-size: 13px; color: var(--muted); margin-top: 6px; }
.form .btn { margin-top: 18px; }
.err { color: #c0392b; font-size: 13px; margin: 4px 0 0; }
@media (max-width: 820px) { .wrap { grid-template-columns: 1fr; } .brand { text-align: center; } }
</style>
