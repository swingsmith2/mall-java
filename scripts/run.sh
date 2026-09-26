#!/usr/bin/env bash
# 生成（或复用）.env.local 后启动应用。JWT 与支付回调密钥不会写入仓库。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env.local"

if [[ ! -f "$ENV_FILE" ]]; then
  umask 077
  jwt="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
  pay="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
  cat >"$ENV_FILE" <<EOF
JWT_SECRET=${jwt}
PAY_CALLBACK_SECRET=${pay}
REDIS_PASSWORD=mall-redis-dev
EOF
  echo "wrote $ENV_FILE"
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

cd "$ROOT"
exec mvn spring-boot:run
