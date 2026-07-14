import axios from "axios";

// All API traffic is routed through the Spring Cloud Gateway (proxied by Vite in dev).
const api = axios.create({ baseURL: "/", timeout: 15000 });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("lbn_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (resp) => resp.data,
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem("lbn_token");
      if (!location.hash.startsWith("#/login")) location.hash = "#/login";
    }
    return Promise.reject(err);
  }
);

export default api;

// Direct channel to the Node.js layer-sync service (WebSocket + REST, bypasses gateway)
export const LAYER_SYNC_HTTP = "http://127.0.0.1:9099";
export const LAYER_SYNC_WS = "ws://127.0.0.1:9099/ws";
