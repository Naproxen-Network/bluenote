<template>
  <div class="wrap">
    <div class="brand">
      <div class="logo serif">L</div>
      <h1 class="serif">Little Blue Note</h1>
      <p class="sub">Capture ideas, share insight, connect with peers.</p>
      <p class="session-note">Each browser tab has its own account session, so multiple users can test the system at the same time.</p>
    </div>

    <div class="card panel">
      <div class="tabs">
        <button type="button" :class="{ on: tab === 'user' }" @click="setTab('user')">Sign In</button>
        <button type="button" :class="{ on: tab === 'register' }" @click="setTab('register')">Register</button>
        <button type="button" :class="{ on: tab === 'admin' }" @click="setTab('admin')">Admin</button>
      </div>

      <form class="form" @submit.prevent="submit">
        <template v-if="tab === 'register'">
          <label>Display name</label>
          <input
            class="input"
            v-model.trim="displayName"
            minlength="2"
            maxlength="64"
            autocomplete="name"
            placeholder="How others will see you"
            required
          />
        </template>

        <label>Username</label>
        <div class="username-row">
          <input
            class="input"
            :class="{ invalid: usernameHasInvalidCharacters }"
            v-model.trim="username"
            minlength="3"
            maxlength="32"
            :pattern="tab === 'register' ? '[A-Za-z0-9_]+' : undefined"
            autocomplete="username"
            autocapitalize="none"
            spellcheck="false"
            :aria-invalid="usernameHasInvalidCharacters"
            :aria-describedby="tab === 'register' ? 'username-help' : undefined"
            :placeholder="tab === 'admin' ? 'admin' : 'e.g. ervin'"
            required
          />
          <span v-if="tab === 'register'" class="initials-preview" :title="`Avatar initials: ${usernameInitials}`">
            {{ usernameInitials }}
          </span>
        </div>
        <p v-if="tab === 'register'" id="username-help" class="field-help" :class="{ invalid: usernameHasInvalidCharacters }">
          {{ usernameHasInvalidCharacters
            ? 'Chinese characters, spaces and symbols are not allowed in usernames.'
            : `3-32 ASCII letters, numbers or underscores. Your avatar will display ${usernameInitials}.` }}
        </p>

        <label>Password</label>
        <input
          class="input"
          type="password"
          v-model="password"
          :minlength="tab === 'register' ? 8 : undefined"
          maxlength="72"
          :autocomplete="tab === 'register' ? 'new-password' : 'current-password'"
          placeholder="Enter password"
          required
        />

        <template v-if="tab === 'register'">
          <label>Confirm password</label>
          <input
            class="input"
            type="password"
            v-model="confirmPassword"
            minlength="8"
            maxlength="72"
            autocomplete="new-password"
            placeholder="Enter the password again"
            required
          />
        </template>

        <button class="btn" type="submit" :disabled="loading || usernameHasInvalidCharacters">
          {{ buttonText }}
        </button>
        <p v-if="error" class="err" role="alert">{{ error }}</p>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../store/auth";

const tab = ref("user");
const username = ref("");
const displayName = ref("");
const password = ref("");
const confirmPassword = ref("");
const loading = ref(false);
const error = ref("");
const auth = useAuth();
const router = useRouter();

const usernameHasInvalidCharacters = computed(() =>
  tab.value === "register" && /[^A-Za-z0-9_]/.test(username.value)
);
const usernameInitials = computed(() => {
  const ascii = username.value.replace(/[^A-Za-z0-9_]/g, "");
  return ascii.slice(0, 2).toUpperCase().padEnd(2, "-");
});

const buttonText = computed(() => {
  if (loading.value) return tab.value === "register" ? "Creating account..." : "Signing in...";
  if (tab.value === "register") return "Create Account";
  if (tab.value === "admin") return "Enter Admin Console";
  return "Enter Little Blue Note";
});

function setTab(nextTab) {
  tab.value = nextTab;
  error.value = "";
  password.value = "";
  confirmPassword.value = "";
}

async function submit() {
  error.value = "";
  const cleanUsername = username.value.trim();
  if (tab.value === "register") {
    if (!/^[A-Za-z0-9_]{3,32}$/.test(cleanUsername)) {
      error.value = "Username must be 3-32 ASCII letters, numbers or underscores; Chinese characters are not allowed.";
      return;
    }
    if (displayName.value.trim().length < 2) {
      error.value = "Display name must contain at least 2 characters.";
      return;
    }
    if (password.value.length < 8) {
      error.value = "Password must contain at least 8 characters.";
      return;
    }
    if (password.value !== confirmPassword.value) {
      error.value = "The two passwords do not match.";
      return;
    }
  }

  loading.value = true;
  try {
    if (tab.value === "admin") {
      await auth.adminLogin(cleanUsername, password.value);
      await router.replace("/admin");
    } else if (tab.value === "register") {
      await auth.register(cleanUsername, displayName.value.trim(), password.value);
      await router.replace("/discover");
    } else {
      await auth.login(cleanUsername, password.value);
      await router.replace("/discover");
    }
  } catch (exception) {
    error.value = exception.response?.data?.message || exception.message || "Authentication failed";
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
.session-note { max-width: 520px; margin: 18px 0 0; color: var(--muted); font-size: 13px; line-height: 1.7; }
.panel { padding: 30px; }
.tabs { display: flex; gap: 8px; background: var(--blue-50); padding: 5px; border-radius: 12px; margin-bottom: 22px; }
.tabs button {
  flex: 1; border: none; background: transparent; padding: 10px 6px; border-radius: 9px;
  font-weight: 600; color: var(--muted);
}
.tabs button.on { background: #fff; color: var(--blue-700); box-shadow: var(--shadow); }
.form { display: flex; flex-direction: column; gap: 8px; }
.form label { font-size: 13px; color: var(--muted); margin-top: 6px; }
.form .btn { margin-top: 18px; }
.username-row { display: flex; align-items: center; gap: 10px; }
.username-row .input { flex: 1; min-width: 0; }
.input.invalid { border-color: #c94c5a; box-shadow: 0 0 0 3px rgba(201, 76, 90, 0.1); }
.initials-preview {
  width: 42px; height: 42px; flex: 0 0 42px; border-radius: 12px;
  display: inline-flex; align-items: center; justify-content: center;
  color: #fff; font-family: var(--serif); font-size: 16px; font-weight: 700;
  background: linear-gradient(135deg, #2f6ea3, #8fb8dd);
  border: 2px solid #fff; box-shadow: var(--shadow);
}
.field-help { color: var(--muted); font-size: 11px; line-height: 1.45; margin: -2px 0 2px; }
.field-help.invalid { color: #a63643; }
.err { color: #c0392b; background: #fff2f1; border: 1px solid #f0d0cd; border-radius: 9px; padding: 9px 11px; font-size: 13px; margin: 5px 0 0; }
@media (max-width: 820px) {
  .wrap { grid-template-columns: 1fr; gap: 14px; padding-top: 24px; }
  .brand { text-align: center; }
  .logo { margin-left: auto; margin-right: auto; }
  .session-note { margin-left: auto; margin-right: auto; }
}
</style>
