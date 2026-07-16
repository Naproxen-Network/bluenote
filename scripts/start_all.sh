#!/usr/bin/env bash
# Build and launch the entire 小蓝书 distributed system. Logs -> logs/*.log
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p logs run

if [ ! -f .env ]; then
  echo "ERROR: Missing .env. Copy .env.example to .env and configure it first."
  exit 1
fi
while IFS='=' read -r name value; do
  name="${name#${name%%[![:space:]]*}}"
  name="${name%${name##*[![:space:]]}}"
  case "$name" in ''|'#'*) continue ;; esac
  if [[ ! "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "ERROR: Invalid environment variable name in .env: $name"
    exit 1
  fi
  export "$name=$value"
done < .env
if [ -z "${LBN_JWT_SECRET:-}" ] || [ "${#LBN_JWT_SECRET}" -lt 32 ]; then
  echo "ERROR: LBN_JWT_SECRET in .env must contain at least 32 characters."
  exit 1
fi

if [ -x /opt/homebrew/bin/brew ]; then eval "$(/opt/homebrew/bin/brew shellenv)"; fi
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17)"
export PATH="$JAVA_HOME/bin:$PATH"

echo "==> Ensuring infra is up (mysql, redis, rabbitmq)"
brew services start mysql >/dev/null 2>&1 || true
brew services start redis >/dev/null 2>&1 || true
brew services start rabbitmq >/dev/null 2>&1 || true

echo "==> Starting Nacos (standalone)"
if ! curl -s http://127.0.0.1:8848/nacos >/dev/null 2>&1; then
  ( export JAVA_HOME; bash infra/nacos/bin/startup.sh -m standalone ) >/dev/null 2>&1 || true
  echo -n "    waiting for Nacos "
  for i in $(seq 1 40); do
    if curl -s http://127.0.0.1:8848/nacos >/dev/null 2>&1; then echo " up"; break; fi
    echo -n "."; sleep 2
  done
fi

echo "==> Building backend (Maven)"
( cd backend && mvn -q -DskipTests clean package )

start_jar () {
  local name=$1 jar=$2 port=$3
  echo "==> Starting $name (:$port)"
  nohup java -jar "$jar" > "logs/$name.log" 2>&1 &
  echo $! > "run/$name.pid"
}

start_jar lbn-gateway            backend/lbn-gateway/target/lbn-gateway.jar            8080
start_jar lbn-user-service       backend/lbn-user-service/target/lbn-user-service.jar 8081
start_jar lbn-post-service       backend/lbn-post-service/target/lbn-post-service.jar 8082
start_jar lbn-recommend-service  backend/lbn-recommend-service/target/lbn-recommend-service.jar 8083
start_jar lbn-chat-service       backend/lbn-chat-service/target/lbn-chat-service.jar       8084

echo "==> Starting Node layer-sync service (:9099)"
( cd layer-sync-service && nohup node server.js > "$ROOT/logs/layer-sync.log" 2>&1 & echo $! > "$ROOT/run/layer-sync.pid" )

echo "==> Starting Vue3 frontend (:5173)"
( cd frontend && nohup npm run dev > "$ROOT/logs/frontend.log" 2>&1 & echo $! > "$ROOT/run/frontend.pid" )

cat <<EOF

============================================================
  小蓝书 is starting up.
  Frontend:      http://127.0.0.1:5173
  Gateway API:   http://127.0.0.1:8080
  Nacos console: http://127.0.0.1:8848/nacos  (nacos/nacos)
  RabbitMQ UI:   http://127.0.0.1:15672       (guest/guest)
  Layer-sync:    http://127.0.0.1:9099/api/layer/status

  Demo logins:
    user  ->  samervin / lbn123456   (or any senator username)
    admin ->  admin / admin123
  Tail logs: tail -f logs/*.log
============================================================
EOF
