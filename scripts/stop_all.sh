#!/usr/bin/env bash
# Stop all 小蓝书 application processes (leaves brew infra services running).
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
for pid in run/*.pid; do
  [ -f "$pid" ] || continue
  kill "$(cat "$pid")" 2>/dev/null && echo "stopped $(basename "$pid" .pid)"
  rm -f "$pid"
done
if [ -x infra/nacos/bin/shutdown.sh ]; then bash infra/nacos/bin/shutdown.sh >/dev/null 2>&1 || true; echo "stopped nacos"; fi
echo "Infra (mysql/redis/rabbitmq) left running. Stop with: brew services stop mysql redis rabbitmq"
