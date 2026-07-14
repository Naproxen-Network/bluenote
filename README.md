# Little Blue Note (LBN)

**Little Blue Note** is an academic social platform for researchers, lawyers, doctors, and knowledge creators. The UI uses a luxury-minimalist light-blue aesthetic. The core engine is a **Multi-Layer Hypergraph Recommender (MLHR)** that powers Discover and Search behind the scenes.

> 小蓝书 — 面向科研工作者的学术社交平台，淡蓝奢华质朴风格，多层超图推荐驱动发现与搜索。

---

## Features

| Module | Description |
|--------|-------------|
| **Profiles** | Avatar, name, bio, party, position, education, interests; 3–5 posts per user; Wikipedia-enhanced portraits |
| **Auth** | User sign-in + separate admin console |
| **Discover** | Personalized masonry feed; **manual Refresh only** (no auto-reload) |
| **Posts** | Like, comment, save; engagement counters |
| **Publish** | Create new posts by topic |
| **Search** | Full-text search with MLHR re-ranking |

---

## Tech Stack

Every item below is used in this project:

| Technology | Role |
|------------|------|
| **Spring Boot / Spring / Spring MVC** | REST services (user, post, recommend) |
| **Spring Cloud Gateway** | API gateway, routing, JWT auth |
| **Nacos** | Service discovery & config (Spring Cloud Alibaba) |
| **OpenFeign** | Inter-service calls (recommend → user/post) |
| **MyBatis Plus** | Persistence (users, posts, likes, comments, favorites, follows) |
| **Redis** | Sessions, hot posts, recommendation cache, cross-layer boost |
| **RabbitMQ** | Post events + committee-layer change events |
| **MySQL** | Primary database `little_blue_note` |
| **Vue 3 + Vite + Pinia + Vue Router** | SPA frontend (English UI) |
| **Node.js** (Express, ws, amqplib, nacos) | Layer-sync service for cross-network updates |

---

## Architecture

```
Vue3 (:5173) ──► Gateway (:8080) ──► user-service (:8081) ──► MySQL
                      │                post-service (:8082) ──┐
                      │                recommend-service (:8083)
                      │                      │ OpenFeign
                      │                      ▼
Node layer-sync (:9099) ──RabbitMQ──► recommend-service
         │
    Nacos (:8848)          Redis (:6379)    RabbitMQ (:5672 / :15672)
```

---

## Data & Multi-Layer Hypergraph

Two real U.S. Senate hypergraph layers seed the platform:

- **Bills layer** (`senate-bills`) → Little Blue Note users (294 senators), hyperedges = co-sponsorship
- **Committee layer** (`senate-committees`) → influence layer (analogous to external networks), hyperedges = committee membership

Cross-layer mapping φ links **166 users** by surname. Committee changes flow through the Node service → RabbitMQ → recommend-service to update rankings in real time.

### MLHR scoring (internal)

```
affinity(u,a) = α·struct + β·semantic + γ·cross + modulationSc · dynamicBoost
```

Weights (α, β, γ) come from offline-learned `sa_weights.json`. Precomputed signals live in `data/generated/recommend_model.json`.

---

## Quick Start (macOS + Homebrew)

### Prerequisites

Install [Homebrew](https://brew.sh), then:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### 1. Environment setup (first time only)

```bash
cd LBN
bash scripts/setup_env.sh
```

This installs OpenJDK 17, Maven, Node, MySQL, Redis, RabbitMQ, downloads Nacos, seeds the database (294 users, 1163 posts), and runs `npm install`.

### 2. Start all services

```bash
bash scripts/start_all.sh
```

### 3. Open the app

| URL | Purpose |
|-----|---------|
| **http://127.0.0.1:5173** | Frontend (main entry) |
| http://127.0.0.1:8080 | API Gateway |
| http://127.0.0.1:8848/nacos | Nacos console (`nacos` / `nacos`) |
| http://127.0.0.1:15672 | RabbitMQ UI (`guest` / `guest`) |

### Demo accounts

| Role | Username | Password |
|------|----------|----------|
| User | `ervin` (or any senator username) | `lbn123456` |
| Admin | `admin` | `admin123` |

### Stop

```bash
bash scripts/stop_all.sh
```

---

## Project Structure

```
LBN/
├── backend/                  Spring Cloud microservices (Maven multi-module)
│   ├── lbn-common/           Shared DTOs, JWT, constants
│   ├── lbn-gateway/          API gateway (:8080)
│   ├── lbn-user-service/     Auth, profiles, follows (:8081)
│   ├── lbn-post-service/     Posts, likes, comments, saves (:8082)
│   ├── lbn-recommend-service Recommendation & search (:8083)
│   └── sql/                  schema.sql + seed.sql
├── frontend/                 Vue 3 SPA (:5173)
├── layer-sync-service/       Node.js cross-layer sync (:9099)
├── data/
│   ├── raw/                  Original senate-bills & senate-committees data
│   └── generated/            Users, posts, MLHR model, committee events
├── infra/                    Nacos standalone server
└── scripts/
    ├── setup_env.sh          Install toolchain & init DB
    ├── start_all.sh          Build & launch everything
    ├── stop_all.sh           Stop application processes
    ├── prepare_data.py       Data prep + MLHR precompute
    ├── gen_sql.py            Generate SQL seed
    └── fetch_avatars.py      Wikipedia avatar enrichment
```

---

## Ports

| Port | Service |
|------|---------|
| 5173 | Vue frontend |
| 8080 | Spring Cloud Gateway |
| 8081 | User service |
| 8082 | Post service |
| 8083 | Recommend service |
| 9099 | Node layer-sync |
| 8848 | Nacos |
| 3306 | MySQL |
| 6379 | Redis |
| 5672 | RabbitMQ |
| 15672 | RabbitMQ management UI |

---

## Troubleshooting

**Discover page empty or stuck on "Refreshing…"**
- Use **http://127.0.0.1:5173** (not 5174 if a second dev server started)
- Sign in again (`ervin` / `lbn123456`)
- Click **Refresh** on the Discover page
- Ensure all backend ports (8080–8083) are up: `bash scripts/start_all.sh`

**Gateway returns 503**
- Wait ~15s after `start_all.sh` for services to register in Nacos
- Re-run `bash scripts/start_all.sh`

**MySQL seed fails on `party` column**
- Re-run `bash scripts/setup_env.sh` (schema uses `VARCHAR(128)`)

---

## License

Academic / demonstration project. Senator data is derived from public congressional hypergraph datasets.
