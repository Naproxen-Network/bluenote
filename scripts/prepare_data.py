#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
小蓝书 (Little Blue Note) data preparation & recommendation-model precompute.

Reads the two raw senator hypergraph layers:
  * bills layer      -> the 小蓝书 layer (our social-media users)
  * committee layer  -> the "other network" that dynamically influences recommendations
                        (the real-data analogue of the TikTok/Telegram layers in the
                         multi-layer hypergraph described in the spec)

Outputs (into data/generated/):
  * users.json            merged, enriched user profiles for the 小蓝书 layer
  * posts.json            3-5 generated posts per user, based on their interests
  * recommend_model.json  precomputed component signals for the Multi-Layer Hypergraph
                          Recommender (MLHR): intra-layer structure, semantics, and
                          cross-layer committee influence + the learned fusion weights
  * committee_events.json a stream of simulated committee-membership change events used
                          by the Node.js layer-sync service to drive real-time updates
  * schema.sql            MySQL schema
  * seed.sql              INSERTs for users, posts (interactions seeded at runtime)
"""
import json, os, re, math, hashlib, random
from collections import defaultdict, Counter

random.seed(42)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "data", "raw")
GEN = os.path.join(ROOT, "data", "generated")
os.makedirs(GEN, exist_ok=True)

BILLS = os.path.join(RAW, "senate-bills")
COMM = os.path.join(RAW, "senate-committees")


def load_json(p):
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)


def load_hyperedges(p):
    edges = []
    with open(p, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                ids = [int(x) for x in line.split()]
            except ValueError:
                continue
            if len(ids) >= 1:
                edges.append(ids)
    return edges


# ---------------------------------------------------------------------------
# 1. Load raw data
# ---------------------------------------------------------------------------
bills_enh = load_json(os.path.join(BILLS, "senate-bills_enhanced.json"))
comm_enh = load_json(os.path.join(COMM, "senate-committees_enhanced.json"))
bills_edges = load_hyperedges(os.path.join(BILLS, "real_Hyperedges-senate-bills.txt"))
comm_edges = load_hyperedges(os.path.join(COMM, "real_Hyperedges-senate-committees.txt"))
sa_weights = load_json(os.path.join(COMM, "sa_weights.json"))

print(f"bills users={len(bills_enh)} edges={len(bills_edges)}")
print(f"committee users={len(comm_enh)} edges={len(comm_edges)}")


# ---------------------------------------------------------------------------
# 2. Cross-layer node mapping  phi:  bills-id <-> committee-id  (by surname)
# ---------------------------------------------------------------------------
def surname(name):
    # bills names look like "ERVIN", "ABRAHAM, SPENCER [MI]"; committee like "Sam Ervin"
    name = name.strip()
    if "," in name:
        return name.split(",")[0].strip().upper()
    # strip trailing bracket info
    name = re.sub(r"\[.*?\]", "", name).strip()
    toks = name.split()
    return toks[-1].upper() if toks else name.upper()


comm_surname_to_id = {}
for cid, v in comm_enh.items():
    comm_surname_to_id.setdefault(surname(v["name"]), int(cid))

bills_to_comm = {}
for bid, v in bills_enh.items():
    s = surname(v["name"])
    if s in comm_surname_to_id:
        bills_to_comm[int(bid)] = comm_surname_to_id[s]
print(f"cross-layer mapped users: {len(bills_to_comm)}")


# ---------------------------------------------------------------------------
# 3. Interest / topic extraction  (academic-social fields)
# ---------------------------------------------------------------------------
FIELD_KEYWORDS = {
    "Law & Justice": ["law", "attorney", "judiciary", "judge", "legal", "constitution", "prosecutor", "counsel", "civil libert"],
    "Medicine & Health": ["health", "medicine", "medical", "physician", "hospital", "care", "disease", "public health"],
    "Defense & Security": ["defense", "military", "armed services", "veteran", "security", "intelligence", "navy", "army", "war"],
    "Economics & Finance": ["finance", "banking", "economic", "budget", "tax", "trade", "commerce", "fiscal", "appropriation"],
    "Environment & Energy": ["environment", "energy", "climate", "conservation", "natural resources", "nuclear", "oil", "water"],
    "Education & Science": ["education", "science", "research", "university", "school", "technology", "space", "student"],
    "Foreign Relations": ["foreign", "diplomacy", "international", "treaty", "relations", "ambassador", "global"],
    "Agriculture & Rural": ["agriculture", "farm", "rural", "food", "nutrition"],
    "Civil Rights & Society": ["civil rights", "segregation", "equality", "labor", "immigration", "housing", "welfare"],
    "Governance & Reform": ["government", "reform", "oversight", "ethics", "campaign", "investigation", "administration"],
}


def extract_interests(profile):
    text = " ".join([
        profile.get("biography", ""),
        profile.get("main_position", ""),
        profile.get("political_leaning", ""),
    ]).lower()
    scores = {}
    for field, kws in FIELD_KEYWORDS.items():
        c = sum(text.count(k) for k in kws)
        if c:
            scores[field] = c
    if not scores:
        scores["Governance & Reform"] = 1
    ranked = sorted(scores, key=scores.get, reverse=True)
    return ranked[:3]


# ---------------------------------------------------------------------------
# 4. Build merged user objects for the 小蓝书 layer
# ---------------------------------------------------------------------------
STATE_RE = re.compile(r"from ([A-Z][a-zA-Z ]+?)(?:\.|,|$| \()")


def pretty_name(raw):
    raw = re.sub(r"\[.*?\]", "", raw)
    raw = raw.split(",")
    if len(raw) >= 2:
        # "ABRAHAM, SPENCER" -> "Spencer Abraham"
        last = raw[0].strip().title()
        first = raw[1].strip().title()
        return f"{first} {last}".strip()
    return raw[0].strip().title()


users = {}
for bid, v in bills_enh.items():
    iid = int(bid)
    interests = extract_interests(v)
    bio = v.get("biography", "").strip()
    pos = v.get("main_position", "").strip()
    m = STATE_RE.search(pos) or STATE_RE.search(bio)
    state = m.group(1).strip() if m else ""
    # Prefer the fuller committee-layer name if mapped (e.g. "Sam Ervin" vs "ERVIN")
    display = pretty_name(v["name"])
    cid = bills_to_comm.get(iid)
    if cid is not None:
        display = comm_enh[str(cid)]["name"].strip()
    username = re.sub(r"[^a-z0-9]", "", display.lower())[:20] or f"user{iid}"
    users[iid] = {
        "id": iid,
        "username": username,
        "displayName": display,
        "party": v.get("party", v.get("affiliation", "")),
        "leaning": v.get("political_leaning", ""),
        "gender": v.get("gender", ""),
        "position": pos,
        "almaMater": v.get("alma_mater", ""),
        "educationLevel": v.get("education_level", ""),
        "state": state,
        "bio": bio,
        "interests": interests,
        "committeeId": cid,
        "avatar": "",  # filled by fetch_avatars.py; frontend falls back to generated
    }

# make usernames unique
seen = Counter()
for u in users.values():
    base = u["username"]
    if seen[base]:
        u["username"] = f"{base}{seen[base]}"
    seen[base] += 1

with open(os.path.join(GEN, "users.json"), "w", encoding="utf-8") as f:
    json.dump(list(users.values()), f, ensure_ascii=False, indent=2)
print(f"wrote users.json ({len(users)})")


# ---------------------------------------------------------------------------
# 5. Generate 3-5 posts per user based on interests
# ---------------------------------------------------------------------------
TEMPLATES = {
    "Law & Justice": [
        "Revisiting how procedural safeguards quietly shape the fairness of a verdict. Precedent is a living thing.",
        "A short note on statutory interpretation: the text matters, but so does the purpose behind it.",
        "Due process is not a formality — it is the difference between order and arbitrariness.",
    ],
    "Medicine & Health": [
        "Preventive care is the most under-funded, highest-return investment a society can make.",
        "Read a compelling study on health outcomes today; access, not just treatment, drives longevity.",
        "Public health is quiet infrastructure — invisible until it fails.",
    ],
    "Defense & Security": [
        "Deterrence is as much about credibility as capability. Thinking through both this week.",
        "National security and civil liberty are not opposites; the hard work is holding them in balance.",
        "Logistics wins wars more often than headlines admit.",
    ],
    "Economics & Finance": [
        "A budget is a moral document — it says out loud what a nation actually values.",
        "Trade policy note: comparative advantage is elegant in theory, messy in a factory town.",
        "Sound fiscal policy is boring on purpose. Boring is a feature.",
    ],
    "Environment & Energy": [
        "Energy transitions are measured in decades, not news cycles. Patience and policy both required.",
        "Conservation is inter-generational accounting: we borrow the land from those not yet born.",
        "Watched the river this morning. Clean water is the least glamorous, most essential victory.",
    ],
    "Education & Science": [
        "Basic research rarely promises returns and almost always delivers them — eventually.",
        "The best classroom is one that teaches students how to be wrong productively.",
        "Funding curiosity today is how you buy tomorrow's breakthroughs.",
    ],
    "Foreign Relations": [
        "Diplomacy is the art of letting someone else have your way. Long game, always.",
        "Alliances are gardens: they die without steady, unglamorous tending.",
        "Reading up on treaty history — the fine print is where the peace actually lives.",
    ],
    "Agriculture & Rural": [
        "Food security is national security. The people who grow it deserve a seat at the table.",
        "Rural broadband is the new rural electrification. Same fight, new century.",
        "Spent the morning with farmers; policy sounds different standing in a field.",
    ],
    "Civil Rights & Society": [
        "Rights on paper mean little without the means to exercise them. Access is everything.",
        "Progress is rarely a straight line, but the arc is worth the argument.",
        "Labor built the middle class; dignity of work should never be a partisan idea.",
    ],
    "Governance & Reform": [
        "Oversight is not obstruction — sunlight keeps institutions honest.",
        "Reform is slow because trust is slow. Rebuild both, brick by brick.",
        "Transparency should be the default setting of a democracy, not a favor granted.",
    ],
}
TAGS = {
    "Law & Justice": "#Law #Justice",
    "Medicine & Health": "#PublicHealth #Medicine",
    "Defense & Security": "#Security #Policy",
    "Economics & Finance": "#Economics #Policy",
    "Environment & Energy": "#Climate #Energy",
    "Education & Science": "#Science #Research",
    "Foreign Relations": "#Diplomacy #Global",
    "Agriculture & Rural": "#Agriculture #Rural",
    "Civil Rights & Society": "#CivilRights #Society",
    "Governance & Reform": "#Governance #Reform",
}

posts = []
pid = 1
for uid, u in users.items():
    rnd = random.Random(uid)
    n = rnd.randint(3, 5)
    interests = u["interests"] or ["Governance & Reform"]
    for k in range(n):
        field = interests[k % len(interests)]
        pool = TEMPLATES[field]
        content = rnd.choice(pool)
        # personalize a fraction with the user's state / position
        if u["state"] and rnd.random() < 0.4:
            content += f" — notes from {u['state']}."
        posts.append({
            "id": pid,
            "authorId": uid,
            "field": field,
            "content": content,
            "tags": TAGS[field],
            "image": f"topic:{field}",  # frontend maps field -> gradient/illustration
        })
        pid += 1

with open(os.path.join(GEN, "posts.json"), "w", encoding="utf-8") as f:
    json.dump(posts, f, ensure_ascii=False, indent=2)
print(f"wrote posts.json ({len(posts)})")


# ---------------------------------------------------------------------------
# 6. Recommendation model precompute  (Multi-Layer Hypergraph Recommender)
# ---------------------------------------------------------------------------
def hypergraph_affinity(edges, valid_ids):
    """tf-idf weighted co-occurrence: a hyperedge of size m contributes 1/(m-1)."""
    aff = defaultdict(lambda: defaultdict(float))
    deg = defaultdict(float)
    for e in edges:
        e = [x for x in set(e) if x in valid_ids]
        m = len(e)
        if m < 2:
            continue
        w = 1.0 / (m - 1)
        for i in range(m):
            deg[e[i]] += w
            for j in range(i + 1, m):
                a, b = e[i], e[j]
                aff[a][b] += w
                aff[b][a] += w
    # cosine-style normalization by sqrt(deg)
    norm = {}
    for i, nb in aff.items():
        for j, w in nb.items():
            d = math.sqrt((deg[i] + 1e-9) * (deg[j] + 1e-9))
            norm.setdefault(i, {})[j] = w / d
    return norm


bill_ids = set(int(k) for k in bills_enh.keys())
comm_ids = set(int(k) for k in comm_enh.keys())
struct_b = hypergraph_affinity(bills_edges, bill_ids)
struct_c = hypergraph_affinity(comm_edges, comm_ids)


# --- semantic TF-IDF over profile text (pure python) ---
def tokenize(t):
    return re.findall(r"[a-z]{3,}", t.lower())


STOP = set("the and for from with that was were his her had has been are who she him you our their its into".split())
docs = {}
df = Counter()
for uid, u in users.items():
    toks = [w for w in tokenize(u["bio"] + " " + u["position"] + " " + u["leaning"]) if w not in STOP]
    docs[uid] = Counter(toks)
    for w in set(toks):
        df[w] += 1
N = len(docs)
idf = {w: math.log(1 + N / (1 + c)) for w, c in df.items()}
vecs = {}
for uid, tf in docs.items():
    v = {w: (f / (sum(tf.values()) + 1e-9)) * idf[w] for w, f in tf.items()}
    nrm = math.sqrt(sum(x * x for x in v.values())) + 1e-9
    vecs[uid] = {w: x / nrm for w, x in v.items()}


def cosine(a, b):
    if len(a) > len(b):
        a, b = b, a
    return sum(x * b.get(w, 0.0) for w, x in a.items())


def topk_from_matrix(mat, uid, k=30):
    row = mat.get(uid, {})
    return sorted(row.items(), key=lambda kv: kv[1], reverse=True)[:k]


K = 30
model_users = {}
ids = sorted(users.keys())
for uid in ids:
    # structural (bills layer)
    sb = topk_from_matrix(struct_b, uid, K)
    # semantic (bills layer) - compute top-k on the fly
    sims = []
    va = vecs[uid]
    for vid in ids:
        if vid == uid:
            continue
        s = cosine(va, vecs[vid])
        if s > 0:
            sims.append((vid, s))
    sims.sort(key=lambda kv: kv[1], reverse=True)
    sem = sims[:K]
    # cross-layer (committee): map uid -> committee, neighbors -> back to bills
    cross = []
    cid = bills_to_comm.get(uid)
    if cid is not None:
        crow = topk_from_matrix(struct_c, cid, K * 2)
        comm_to_bills = {c: b for b, c in bills_to_comm.items()}
        for ncid, w in crow:
            nb = comm_to_bills.get(ncid)
            if nb is not None and nb != uid:
                cross.append((nb, w))
        cross = cross[:K]
    model_users[str(uid)] = {
        "struct": [[i, round(w, 5)] for i, w in sb],
        "sem": [[i, round(w, 5)] for i, w in sem],
        "cross": [[i, round(w, 5)] for i, w in cross],
    }

readable = sa_weights.get("readable", {})
abg = readable.get("alpha_beta_gamma", [0.27, 0.30, 0.43])
model = {
    "weights": {
        "alpha_struct": abg[0],
        "beta_semantic": abg[1],
        "gamma_cross": abg[2],
        "semanticRatio": readable.get("semantic_ratio", 0.55),
        "modulationLb": readable.get("modulation_lb_sc", [0.42, 0.89])[0],
        "modulationSc": readable.get("modulation_lb_sc", [0.42, 0.89])[1],
    },
    "users": model_users,
    "crossLayerMapping": {str(b): c for b, c in bills_to_comm.items()},
}
with open(os.path.join(GEN, "recommend_model.json"), "w", encoding="utf-8") as f:
    json.dump(model, f, ensure_ascii=False)
print(f"wrote recommend_model.json (weights={model['weights']})")


# ---------------------------------------------------------------------------
# 7. Simulated committee-membership change events (drive real-time updates)
# ---------------------------------------------------------------------------
events = []
comm_to_bills = {c: b for b, c in bills_to_comm.items()}
mapped_comm = list(comm_to_bills.keys())
for i in range(60):
    rnd = random.Random(1000 + i)
    cid = rnd.choice(mapped_comm)
    action = rnd.choice(["JOIN", "LEAVE", "CHAIR"])
    # pick a committee (hyperedge) this affects
    committee_idx = rnd.randrange(len(comm_edges))
    events.append({
        "seq": i + 1,
        "committeeMemberId": cid,
        "billsUserId": comm_to_bills[cid],
        "action": action,
        "committeeIndex": committee_idx,
        "weightDelta": round(rnd.uniform(0.1, 1.0), 3),
    })
with open(os.path.join(GEN, "committee_events.json"), "w", encoding="utf-8") as f:
    json.dump(events, f, ensure_ascii=False, indent=2)
print(f"wrote committee_events.json ({len(events)})")

print("DONE prepare_data")
