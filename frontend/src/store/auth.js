import { defineStore } from "pinia";
import api from "../api";

export const useAuth = defineStore("auth", {
  state: () => ({
    token: localStorage.getItem("lbn_token") || "",
    role: localStorage.getItem("lbn_role") || "",
    user: JSON.parse(localStorage.getItem("lbn_user") || "null"),
    adminName: localStorage.getItem("lbn_admin_name") || "",
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.role === "ADMIN",
  },
  actions: {
    persist() {
      localStorage.setItem("lbn_token", this.token);
      localStorage.setItem("lbn_role", this.role);
      localStorage.setItem("lbn_user", JSON.stringify(this.user));
      localStorage.setItem("lbn_admin_name", this.adminName || "");
    },
    async login(username, password) {
      const r = await api.post("/api/auth/login", { username, password });
      if (r.code !== 0) throw new Error(r.message);
      this.token = r.data.token;
      this.role = r.data.role;
      this.user = r.data.user;
      this.persist();
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
      localStorage.clear();
    },
  },
});
