#!/usr/bin/env bash
# 对「已启动」的 mall 服务做中等并发 HTTP 冒烟（无需 Docker 测 JVM）。
# 用法：
#   1) docker compose up -d && mvn spring-boot:run
#   2) BASE_URL=http://127.0.0.1:8080 ./scripts/concurrent-api-smoke.sh

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
CONCURRENCY="${CONCURRENCY:-35}"

echo "BASE_URL=$BASE_URL CONCURRENCY=$CONCURRENCY"

fail=0
codes=$(mktemp)
login_body=$(mktemp)
trap 'rm -f "$codes" "$login_body"' EXIT

for i in $(seq 1 "$CONCURRENCY"); do
  page=$(( (i % 3) + 1 ))
  {
    c=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/products?page=${page}&size=20" || echo "000")
    echo "$c" >>"$codes"
  } &
done
wait

bad=$(grep -v '^200$' "$codes" || true)
if [[ -n "$bad" ]]; then
  echo "商品列表并发请求存在非 200 状态:" >&2
  sort "$codes" | uniq -c >&2
  fail=1
else
  echo "商品列表: ${CONCURRENCY} 路并发全部为 HTTP 200"
fi

login_code=$(curl -sS -o "$login_body" -w "%{http_code}" -X POST "${BASE_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}' || echo "000")
# 注意：失败时 API 常为 "data": null，不能用 .get('data',{})（会得到 None 再 .get 报错）
tok=$(python3 -c "
import json, sys
with open(sys.argv[1], encoding='utf-8') as f:
    j = json.load(f)
d = j.get('data')
if not isinstance(d, dict):
    d = {}
print(d.get('token', ''))
" "$login_body" 2>/dev/null || true)
if [[ -z "$tok" ]]; then
  snippet=$(head -c 300 "$login_body" | tr -d '\r' | tr '\n' ' ')
  echo "跳过购物车并发: 未拿到 JWT。HTTP=${login_code} 响应摘要: ${snippet}" >&2
  echo "常见原因: 库未跑 Flyway 种子用户 demo；或密码不是 demo123；或连错环境。" >&2
  exit "$fail"
fi

: >"$codes"
for i in $(seq 1 "$CONCURRENCY"); do
  {
    c=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/cart" -H "Authorization: Bearer ${tok}" || echo "000")
    echo "$c" >>"$codes"
  } &
done
wait

bad=$(grep -v '^200$' "$codes" || true)
if [[ -n "$bad" ]]; then
  echo "购物车并发请求存在非 200 状态:" >&2
  sort "$codes" | uniq -c >&2
  fail=1
else
  echo "购物车: ${CONCURRENCY} 路并发全部为 HTTP 200"
fi

exit "$fail"
