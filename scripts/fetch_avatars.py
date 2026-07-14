#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Wikipedia enhancement: fetch a real portrait thumbnail for each 小蓝书 user via the
Wikipedia REST summary API, download it locally, and fall back to a generated
initials-on-blue SVG avatar when no portrait is available.

Updates data/generated/users.json (avatar field) and writes images to
frontend/public/avatars/. Safe to re-run: it skips users that already have a file.
"""
import json, os, time, urllib.parse, urllib.request, hashlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(ROOT, "data", "generated")
AV_DIR = os.path.join(ROOT, "frontend", "public", "avatars")
os.makedirs(AV_DIR, exist_ok=True)

users = json.load(open(os.path.join(GEN, "users.json"), encoding="utf-8"))
UA = "LittleBlueNote/1.0 (academic demo; contact: demo@example.com)"

BLUES = ["#1e5fa8", "#2a6fb5", "#3a7fc0", "#4a86b8", "#2f6ea3", "#356aa0"]


def initials(name):
    parts = [p for p in name.replace(",", " ").split() if p and p[0].isalpha()]
    if not parts:
        return "LB"
    if len(parts) == 1:
        return parts[0][:2].upper()
    return (parts[0][0] + parts[-1][0]).upper()


def write_svg(uid, name):
    c = BLUES[int(hashlib.md5(name.encode()).hexdigest(), 16) % len(BLUES)]
    ini = initials(name)
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
<stop offset="0" stop-color="{c}"/><stop offset="1" stop-color="#8fb8dd"/></linearGradient></defs>
<rect width="200" height="200" fill="url(#g)"/>
<text x="100" y="100" font-family="Georgia,serif" font-size="80" fill="#ffffff"
 text-anchor="middle" dominant-baseline="central" opacity="0.95">{ini}</text></svg>'''
    path = os.path.join(AV_DIR, f"{uid}.svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write(svg)
    return f"/avatars/{uid}.svg"


def fetch_summary(name):
    title = urllib.parse.quote(name.replace(" ", "_"))
    url = f"https://en.wikipedia.org/api/rest_v1/page/summary/{title}"
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=8) as r:
        return json.loads(r.read().decode("utf-8"))


BROWSER_UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
              "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")


def download(url, dest):
    # upload.wikimedia.org rejects generic agents; use browser-like headers + referer
    req = urllib.request.Request(url, headers={
        "User-Agent": BROWSER_UA,
        "Accept": "image/avif,image/webp,image/png,image/*,*/*",
        "Referer": "https://en.wikipedia.org/",
    })
    with urllib.request.urlopen(req, timeout=10) as r:
        data = r.read()
    with open(dest, "wb") as f:
        f.write(data)


ok, fell = 0, 0
for i, u in enumerate(users):
    uid = u["id"]
    jpg = os.path.join(AV_DIR, f"{uid}.jpg")
    svg = os.path.join(AV_DIR, f"{uid}.svg")
    if os.path.exists(jpg):
        u["avatar"] = f"/avatars/{uid}.jpg"
        ok += 1
        continue
    if os.path.exists(svg):
        u["avatar"] = f"/avatars/{uid}.svg"
        continue
    avatar = None
    try:
        s = fetch_summary(u["displayName"])
        thumb = (s.get("thumbnail") or {}).get("source")
        # only accept real person pages (avoid disambiguation)
        if thumb and s.get("type") != "disambiguation":
            download(thumb, jpg)
            avatar = f"/avatars/{uid}.jpg"
            ok += 1
            # enrich short bio if original missing
            if not u.get("bio") and s.get("extract"):
                u["bio"] = s["extract"]
    except Exception:
        avatar = None
    if not avatar:
        avatar = write_svg(uid, u["displayName"])
        fell += 1
    u["avatar"] = avatar
    if i % 20 == 0:
        print(f"[{i}/{len(users)}] wiki_ok={ok} fallback={fell}", flush=True)
    time.sleep(0.15)

with open(os.path.join(GEN, "users.json"), "w", encoding="utf-8") as f:
    json.dump(users, f, ensure_ascii=False, indent=2)
print(f"DONE avatars: wiki={ok} fallback={fell} total={len(users)}")
