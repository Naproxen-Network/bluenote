import { createRouter, createWebHashHistory } from "vue-router";
import { useAuth } from "../store/auth";

const routes = [
  { path: "/login", component: () => import("../views/Login.vue") },
  {
    path: "/",
    component: () => import("../views/Shell.vue"),
    children: [
      { path: "", redirect: "/discover" },
      { path: "discover", component: () => import("../views/Discover.vue") },
      { path: "search", component: () => import("../views/Search.vue") },
      { path: "publish", component: () => import("../views/Publish.vue") },
      { path: "post/:id", component: () => import("../views/PostDetail.vue") },
      { path: "profile/:id", component: () => import("../views/Profile.vue") },
      { path: "me", component: () => import("../views/Profile.vue") },
    ],
  },
  { path: "/admin", component: () => import("../views/Admin.vue") },
  { path: "/:pathMatch(.*)*", redirect: "/discover" },
];

const router = createRouter({ history: createWebHashHistory(), routes });

router.beforeEach((to) => {
  const auth = useAuth();
  if (to.path === "/login") return true;
  if (!auth.isLoggedIn) return "/login";
  if (to.path.startsWith("/admin") && !auth.isAdmin) return "/discover";
  if (!to.path.startsWith("/admin") && auth.isAdmin && to.path !== "/login") return "/admin";
  return true;
});

export default router;
