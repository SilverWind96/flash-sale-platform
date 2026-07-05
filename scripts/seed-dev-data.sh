#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SKU="${SKU:-LOAD-TICKET-$(date +%Y%m%d%H%M%S)}"
PRODUCT_NAME="${PRODUCT_NAME:-Load Test Ticket}"
PRICE_CENTS="${PRICE_CENTS:-2500}"
STOCK="${STOCK:-10000}"
ENV_FILE="${ENV_FILE:-.load-test.env}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_command curl
require_command jq

echo "Checking application health at ${BASE_URL}/actuator/health ..."
curl -fsS "${BASE_URL}/actuator/health" >/dev/null

echo "Creating product sku=${SKU} ..."
create_response="$(
  curl -fsS -X POST "${BASE_URL}/api/products" \
    -H "Content-Type: application/json" \
    -d "{
      \"sku\": \"${SKU}\",
      \"name\": \"${PRODUCT_NAME}\",
      \"priceCents\": ${PRICE_CENTS}
    }"
)"

product_id="$(printf '%s' "${create_response}" | jq -r '.id')"
if [[ -z "${product_id}" || "${product_id}" == "null" ]]; then
  echo "Could not read product id from response:" >&2
  printf '%s\n' "${create_response}" >&2
  exit 1
fi

echo "Adding stock=${STOCK} to product=${product_id} ..."
inventory_response="$(
  curl -fsS -X POST "${BASE_URL}/api/products/${product_id}/inventory/stock" \
    -H "Content-Type: application/json" \
    -d "{
      \"quantity\": ${STOCK}
    }"
)"

cat > "${ENV_FILE}" <<EOF
BASE_URL=${BASE_URL}
PRODUCT_ID=${product_id}
SKU=${SKU}
STOCK=${STOCK}
PRICE_CENTS=${PRICE_CENTS}
EOF

echo
echo "Seed data ready."
echo "Product id: ${product_id}"
echo "Inventory: $(printf '%s' "${inventory_response}" | jq -c '.')"
echo "Saved load-test environment to ${ENV_FILE}"
