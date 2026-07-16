const KEYS = {
  token: "lbn_token",
  role: "lbn_role",
  user: "lbn_user",
  adminName: "lbn_admin_name",
};

// Remove credentials written by older builds. Authentication is deliberately
// tab-scoped so separate tabs can sign in as different users.
Object.values(KEYS).forEach((key) => localStorage.removeItem(key));

export function readAuthSession() {
  let user = null;
  try {
    user = JSON.parse(sessionStorage.getItem(KEYS.user) || "null");
  } catch {
    sessionStorage.removeItem(KEYS.user);
  }
  return {
    token: sessionStorage.getItem(KEYS.token) || "",
    role: sessionStorage.getItem(KEYS.role) || "",
    user,
    adminName: sessionStorage.getItem(KEYS.adminName) || "",
  };
}

export function writeAuthSession({ token, role, user, adminName }) {
  sessionStorage.setItem(KEYS.token, token || "");
  sessionStorage.setItem(KEYS.role, role || "");
  sessionStorage.setItem(KEYS.user, JSON.stringify(user || null));
  sessionStorage.setItem(KEYS.adminName, adminName || "");
}

export function clearAuthSession() {
  Object.values(KEYS).forEach((key) => sessionStorage.removeItem(key));
}

export function getSessionToken() {
  return sessionStorage.getItem(KEYS.token) || "";
}

export function getSessionUser() {
  try {
    return JSON.parse(sessionStorage.getItem(KEYS.user) || "null");
  } catch {
    return null;
  }
}
