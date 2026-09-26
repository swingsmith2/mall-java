#!/usr/bin/env bash
# 对 compose 中的 Postgres 做自定义格式备份，输出到 backups/（不入库）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/backups"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="$ROOT/backups/mall-${stamp}.dump"
docker compose -f "$ROOT/docker-compose.yml" exec -T postgres \
  pg_dump -U "${DB_USER:-mall}" -d "${DB_NAME:-mall}" -Fc >"$out"
if [[ ! -s "$out" ]]; then
  echo "备份文件为空: $out" >&2
  exit 1
fi
echo "wrote $out"
echo "恢复示例: docker compose exec -T postgres pg_restore -U mall -d mall --clean --if-exists < $out"
