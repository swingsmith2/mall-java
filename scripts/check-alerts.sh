#!/usr/bin/env bash
# 用管理员账号查看 outbox 告警。有失败或滞留事件时退出码为 1。
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
USER_NAME="${ADMIN_USER:-admin}"
PASSWORD="${ADMIN_PASSWORD:-demo123}"

login="$(curl -sS -X POST "${BASE_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${USER_NAME}\",\"password\":\"${PASSWORD}\"}")"
token="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["data"]["token"])' "$login")"
body="$(curl -sS "${BASE_URL}/api/admin/ops/alerts" -H "Authorization: Bearer ${token}")"
echo "$body"
python3 -c 'import json,sys; d=json.loads(sys.argv[1])["data"]; raise SystemExit(0 if not d["open"] else 1)' "$body"
