#!/usr/bin/env bash
# ابزار خط فرمان (CLI) تست سریع درگاه پرداخت فائری (Faerie Payline)
set -euo pipefail

BASE_URL="${FAERIE_BASE_URL:-https://faerie.ir}"
TOKEN="${FAERIE_PAYLINE_TOKEN:-}"

if [[ -z "$TOKEN" ]]; then
  echo "لطفاً متغیر محیطی FAERIE_PAYLINE_TOKEN=pl_... را تنظیم کنید." >&2
  exit 1
fi

cmd="${1:-products}"
case "$cmd" in
  products)
    curl -sS -H "X-Payline: $TOKEN" "$BASE_URL/api/v1/iap/products"
    echo
    ;;
  init)
    sku="${2:?SKU الزامی است}"
    uid="${3:-cli_user}"
    curl -sS -X POST "$BASE_URL/api/v1/iap/init" \
      -H "X-Payline: $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"sku\":\"$sku\",\"user_id\":\"$uid\"}"
    echo
    ;;
  status)
    pid="${2:?purchase_id الزامی است}"
    curl -sS -H "X-Payline: $TOKEN" "$BASE_URL/api/v1/iap/status/$pid"
    echo
    ;;
  invoice)
    pid="${2:?purchase_id الزامی است}"
    curl -sS -H "X-Payline: $TOKEN" "$BASE_URL/api/v1/iap/invoice/$pid"
    echo
    ;;
  *)
    echo "Usage: FAERIE_PAYLINE_TOKEN=pl_... $0 {products|init <sku> [user_id]|status <purchase_id>|invoice <purchase_id>}"
    ;;
esac
