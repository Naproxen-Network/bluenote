import { defineStore } from "pinia";
import api from "../api";

function requireOk(response) {
  if (response.code !== 0) throw new Error(response.message || "操作失败");
  return response.data;
}

export const useFriendStore = defineStore("friend", {
  state: () => ({
    friends: [],
    incoming: [],
    outgoing: [],
    blocked: [],
    loading: false,
    lastEventAt: 0,
  }),
  getters: {
    incomingCount: (state) => state.incoming.length,
  },
  actions: {
    async loadAll() {
      this.loading = true;
      try {
        const [friends, incoming, outgoing, blocked] = await Promise.all([
          api.get("/api/friends"),
          api.get("/api/friends/requests/incoming"),
          api.get("/api/friends/requests/outgoing"),
          api.get("/api/friends/blocked"),
        ]);
        this.friends = requireOk(friends) || [];
        this.incoming = requireOk(incoming) || [];
        this.outgoing = requireOk(outgoing) || [];
        this.blocked = requireOk(blocked) || [];
      } finally {
        this.loading = false;
      }
    },
    async status(userId) {
      return requireOk(await api.get(`/api/friends/${userId}/status`));
    },
    async lookup(identity) {
      const value = String(identity ?? "").trim();
      if (!value) throw new Error("Enter a user id or username");
      return requireOk(await api.get("/api/friends/lookup", { params: { identity: value } }));
    },
    async request(identity, message = "") {
      const value = String(identity ?? "").trim();
      if (!value) throw new Error("Enter a user id or username");
      const target = /^\d+$/.test(value)
        ? { targetUserId: Number(value) }
        : { targetUsername: value };
      const data = requireOk(await api.post("/api/friends/requests", { ...target, message }));
      await this.loadAll();
      return data;
    },
    async accept(relationId) {
      const data = requireOk(await api.post(`/api/friends/requests/${relationId}/accept`));
      await this.loadAll();
      return data;
    },
    async reject(relationId) {
      requireOk(await api.post(`/api/friends/requests/${relationId}/reject`));
      await this.loadAll();
    },
    async cancel(relationId) {
      requireOk(await api.post(`/api/friends/requests/${relationId}/cancel`));
      await this.loadAll();
    },
    async remove(targetUserId) {
      requireOk(await api.delete(`/api/friends/${targetUserId}`));
      await this.loadAll();
    },
    async block(targetUserId, reason = "") {
      requireOk(await api.post(`/api/friends/${targetUserId}/block`, { reason }));
      await this.loadAll();
    },
    async unblock(targetUserId) {
      requireOk(await api.delete(`/api/friends/${targetUserId}/block`));
      await this.loadAll();
    },
    handleRealtimeEvent() {
      const now = Date.now();
      if (now - this.lastEventAt < 250) return;
      this.lastEventAt = now;
      this.loadAll().catch(() => {});
    },
    clear() {
      this.$reset();
    },
  },
});
