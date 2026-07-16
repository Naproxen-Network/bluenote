import { defineStore } from "pinia";
import api from "../api";
import { connectChatSocket, disconnectChatSocket, publishTyping } from "../realtime/chatSocket";
import { useFriendStore } from "./friend";
import { getSessionUser } from "../authSession";

function requireOk(response) {
  if (response.code !== 0) throw new Error(response.message || "操作失败");
  return response.data;
}

function byLatest(a, b) {
  return new Date(b.lastMessageAt || 0) - new Date(a.lastMessageAt || 0);
}

function clientMessageId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `web-${Date.now()}-${Math.random().toString(36).slice(2, 14)}`;
}

export const useChatStore = defineStore("chat", {
  state: () => ({
    conversations: [],
    messagesByConversation: {},
    activeId: null,
    connected: false,
    typingByConversation: {},
    realtimeError: "",
  }),
  getters: {
    unreadTotal: (state) => state.conversations.reduce((sum, item) => sum + Number(item.unreadCount || 0), 0),
    activeConversation: (state) => state.conversations.find((item) => item.id === state.activeId) || null,
    activeMessages: (state) => state.messagesByConversation[state.activeId] || [],
  },
  actions: {
    connect() {
      connectChatSocket({
        connected: (value) => { this.connected = value; },
        messages: (event) => this.handleMessageEvent(event),
        "friend-events": () => {
          useFriendStore().handleRealtimeEvent();
          this.loadConversations().catch(() => {});
        },
        "read-events": (event) => this.handleReadEvent(event),
        typing: (event) => this.handleTypingEvent(event),
        error: (message) => { this.realtimeError = message; },
      });
    },
    disconnect() {
      disconnectChatSocket();
      this.connected = false;
    },
    async loadConversations() {
      this.conversations = (requireOk(await api.get("/api/chat/conversations")) || []).sort(byLatest);
      return this.conversations;
    },
    async openFriend(friendId) {
      const conversation = requireOk(await api.post(`/api/chat/conversations/private/${friendId}`));
      const index = this.conversations.findIndex((item) => item.id === conversation.id);
      if (index >= 0) this.conversations[index] = conversation;
      else this.conversations.unshift(conversation);
      this.activeId = conversation.id;
      return conversation;
    },
    async selectConversation(conversationId) {
      const id = Number(conversationId);
      if (!Number.isSafeInteger(id) || id <= 0) throw new Error("无效的会话编号");
      this.activeId = id;
      await this.loadMessages(id);
      await this.markRead(id);
    },
    async loadMessages(conversationId, beforeId = null) {
      const params = { limit: 50 };
      if (beforeId) params.beforeId = beforeId;
      const page = requireOk(await api.get(`/api/chat/conversations/${conversationId}/messages`, { params })) || [];
      const current = this.messagesByConversation[conversationId] || [];
      const merged = beforeId ? [...page, ...current] : page;
      this.messagesByConversation[conversationId] = Array.from(
        new Map(merged.map((message) => [message.id, message])).values(),
      ).sort((a, b) => a.id - b.id);
      return page;
    },
    async send(content, replyToId = null) {
      const conversationId = this.activeId;
      if (!conversationId) throw new Error("请先选择会话");
      const text = content.trim();
      if (!text) throw new Error("消息不能为空");
      const message = requireOk(await api.post(`/api/chat/conversations/${conversationId}/messages`, {
        clientMessageId: clientMessageId(),
        content: text,
        replyToId,
      }));
      this.upsertMessage(conversationId, message);
      this.updateConversationForMessage(message);
      return message;
    },
    async markRead(conversationId = this.activeId) {
      if (!conversationId) return;
      const messages = this.messagesByConversation[conversationId] || [];
      const lastMessageId = messages.at(-1)?.id || null;
      requireOk(await api.put(`/api/chat/conversations/${conversationId}/read`, { lastMessageId }));
      const conversation = this.conversations.find((item) => item.id === conversationId);
      if (conversation) conversation.unreadCount = 0;
    },
    async recall(messageId) {
      const message = requireOk(await api.post(`/api/chat/messages/${messageId}/recall`));
      this.upsertMessage(message.conversationId, message);
      return message;
    },
    async report(messageId, type, description) {
      return requireOk(await api.post(`/api/chat/messages/${messageId}/report`, { type, description }));
    },
    sendTyping(typing) {
      const conversation = this.activeConversation;
      if (conversation) publishTyping(conversation.id, conversation.peer.id, typing);
    },
    handleMessageEvent(event) {
      const message = event?.message;
      const conversationId = Number(event?.conversationId || message?.conversationId);
      if (!message || !conversationId) return;
      this.upsertMessage(conversationId, message);
      this.updateConversationForMessage(message);
      if (conversationId === this.activeId && document.visibilityState === "visible") {
        this.markRead(conversationId).catch(() => {});
      } else {
        const me = getSessionUser()?.id;
        if (Number(message.senderId) !== Number(me)) {
          const conversation = this.conversations.find((item) => item.id === conversationId);
          if (conversation) conversation.unreadCount = Number(conversation.unreadCount || 0) + 1;
          else this.loadConversations().catch(() => {});
        }
      }
    },
    handleReadEvent(event) {
      const conversation = this.conversations.find((item) => item.id === Number(event?.conversationId));
      if (conversation) conversation.peerLastReadMessageId = event.lastReadMessageId;
    },
    handleTypingEvent(event) {
      const id = Number(event?.conversationId);
      if (!id) return;
      this.typingByConversation[id] = Boolean(event.typing);
      if (event.typing) {
        window.clearTimeout(this.typingByConversation[`${id}:timer`]);
        this.typingByConversation[`${id}:timer`] = window.setTimeout(() => {
          this.typingByConversation[id] = false;
        }, 3000);
      }
    },
    upsertMessage(conversationId, message) {
      const list = this.messagesByConversation[conversationId] || [];
      const index = list.findIndex((item) => item.id === message.id);
      if (index >= 0) list[index] = message;
      else list.push(message);
      list.sort((a, b) => a.id - b.id);
      this.messagesByConversation[conversationId] = [...list];
    },
    updateConversationForMessage(message) {
      const conversation = this.conversations.find((item) => item.id === Number(message.conversationId));
      if (!conversation) {
        this.loadConversations().catch(() => {});
        return;
      }
      conversation.lastMessage = message;
      conversation.lastMessageAt = message.createdAt;
      this.conversations.sort(byLatest);
    },
    clear() {
      this.disconnect();
      Object.values(this.typingByConversation)
        .filter((value) => typeof value === "number")
        .forEach((timer) => window.clearTimeout(timer));
      this.$reset();
    },
  },
});
