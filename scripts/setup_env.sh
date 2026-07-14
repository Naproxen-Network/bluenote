#!/usr/bin/env bash
# 小蓝书 environment setup (macOS / Homebrew). Installs the full toolchain and
# infrastructure, downloads Nacos, and initialises the database.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -x /opt/homebrew/bin/brew ]; then eval "$(/opt/homebrew/bin/brew shellenv)"; fi
command -v brew >/dev/null || { echo "Homebrew not found. Install it first."; exit 1; }

echo "==> Installing toolchain via Homebrew (this can take a while)"
brew install openjdk@17 maven node redis rabbitmq mysql || true

# make the JDK visible to Maven / Nacos
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17)"
echo "JAVA_HOME=$JAVA_HOME"

echo "==> Starting infrastructure services (mysql, redis, rabbitmq)"
brew services start mysql
brew services start redis
brew services start rabbitmq
sleep 8

echo "==> Downloading Nacos (standalone)"
NACOS_VER=2.3.2
mkdir -p infra
if [ ! -d "infra/nacos" ]; then
  curl -fSL "https://github.com/alibaba/nacos/releases/download/${NACOS_VER}/nacos-server-${NACOS_VER}.tar.gz" -o infra/nacos.tar.gz
  tar -xzf infra/nacos.tar.gz -C infra
  rm -f infra/nacos.tar.gz
fi

echo "==> Initialising MySQL schema + seed data"
MYSQL_BIN="$(brew --prefix mysql)/bin/mysql"
"$MYSQL_BIN" -uroot < backend/sql/schema.sql
"$MYSQL_BIN" -uroot < backend/sql/seed.sql
echo "    seeded users + posts."

echo "==> Installing Node dependencies (layer-sync + frontend)"
( cd layer-sync-service && npm install )
( cd frontend && npm install )

echo "==> Done. Now run: scripts/start_all.sh"
