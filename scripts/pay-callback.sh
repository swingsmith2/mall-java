#!/usr/bin/env bash
# 按 HMAC 调用支付回调。依赖环境变量 PAY_CALLBACK_SECRET、ORDER_ID、AMOUNT_CENT。
set -euo pipefail
: "${PAY_CALLBACK_SECRET:?请先 source .env.local}"
: "${ORDER_ID:?}"
: "${AMOUNT_CENT:?}"
PAYMENT_NO="${PAYMENT_NO:-pay-$(date +%s)}"
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
STATUS="${STATUS:-SUCCESS}"

sig="$(python3 -c 'import hmac,hashlib,sys
order_id, payment_no, amount, status, secret = sys.argv[1:]
msg = f"{order_id}|{payment_no}|{amount}|{status}".encode()
print(hmac.new(secret.encode(), msg, hashlib.sha256).hexdigest())' \
  "$ORDER_ID" "$PAYMENT_NO" "$AMOUNT_CENT" "$STATUS" "$PAY_CALLBACK_SECRET")"

curl -sS -X POST "${BASE_URL}/api/payments/callback" \
  -H 'Content-Type: application/json' \
  -H "X-Mall-Signature: ${sig}" \
  -d "{\"orderId\":${ORDER_ID},\"paymentNo\":\"${PAYMENT_NO}\",\"amountCent\":${AMOUNT_CENT},\"status\":\"${STATUS}\"}"
echo
