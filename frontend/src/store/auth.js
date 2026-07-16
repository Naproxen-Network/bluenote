import { defineStore } from "pinia";
import api from "../api";
import { clearAuthSession, readAuthSession, writeAuthSession } from "../authSession";

const initialSession = readAuthSession();

export const useAuth = defineStore("auth", {
  state: () => ({
    token: initialSession.token,
    role: initialSession.role,
    user: initialSession.user,
    adminName: initialSession.adminName,
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.role === "ADMIN",
  },
  actions: {
    persist() {
      writeAuthSession(this);
    },
    async login(username, password) {
      const r = await api.post("/api/auth/login", { username, password });
      if (r.code !== 0) throw new Error(r.message);
      this.token = r.data.token;
      this.role = r.data.role;
      this.user = r.data.user;
      this.persist();
    },
    async register(username, displayName, password) {
      const response = await api.post("/api/auth/register", { username, displayName, password });
      if (response.code !== 0) throw new Error(response.message);
      await this.login(username, password);
      return response.data;
    },
    async adminLogin(username, password) {
      const r = await api.post("/api/auth/admin/login", { username, password });
      if (r.code !== 0) throw new Error(r.message);
      this.token = r.data.token;
      this.role = r.data.role;
      this.adminName = r.data.name;
      this.persist();
    },
    logout() {
      this.token = "";
      this.role = "";
      this.user = null;
      this.adminName = "";
      clearAuthSession();
    },
  },
});
