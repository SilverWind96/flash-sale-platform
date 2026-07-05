#!/usr/bin/env bash

set -euo pipefail

if [[ -f ".load-test.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".load-test.env"
  set +a
fi

BASE_URL="${BASE_URL:-http://localhost:8080}"
PRODUCT_ID="${PRODUCT_ID:-}"
REQUESTS="${REQUESTS:-1000}"
CONCURRENCY="${CONCURRENCY:-50}"
QUANTITY="${QUANTITY:-1}"
IDEMPOTENCY_PREFIX="${IDEMPOTENCY_PREFIX:-load-checkout}"
RESULT_DIR="${RESULT_DIR:-target/load-test}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
CONNECT_TIMEOUT="${CONNECT_TIMEOUT:-2}"
MAX_TIME="${MAX_TIME:-10}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer. Got: ${value}" >&2
    exit 1
  fi
}

require_command curl
require_command jq
require_positive_integer REQUESTS "${REQUESTS}"
require_positive_integer CONCURRENCY "${CONCURRENCY}"
require_positive_integer QUANTITY "${QUANTITY}"

if [[ -z "${PRODUCT_ID}" ]]; then
  echo "PRODUCT_ID is required. Run scripts/seed-dev-data.sh first or pass PRODUCT_ID=..." >&2
  exit 1
fi

echo "Checking application health at ${BASE_URL}/actuator/health ..."
curl -fsS "${BASE_URL}/actuator/health" >/dev/null

fetch_inventory() {
  curl -fsS "${BASE_URL}/api/products/${PRODUCT_ID}/inventory"
}

mkdir -p "${RESULT_DIR}"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/flash-sale-load.XXXXXX")"
trap 'rm -rf "${tmp_dir}"' EXIT

results_file="${RESULT_DIR}/checkout-${RUN_ID}.tsv"
summary_file="${RESULT_DIR}/checkout-${RUN_ID}-summary.txt"

echo "Running checkout load test"
echo "Base URL: ${BASE_URL}"
echo "Product: ${PRODUCT_ID}"
echo "Requests: ${REQUESTS}"
echo "Concurrency: ${CONCURRENCY}"
echo "Quantity per request: ${QUANTITY}"
echo "Run id: ${RUN_ID}"
echo

before_inventory="$(fetch_inventory)"
before_available="$(printf '%s' "${before_inventory}" | jq -r '.available')"
before_reserved="$(printf '%s' "${before_inventory}" | jq -r '.reserved')"

started_at_epoch="$(date +%s)"

run_one() {
  local index="$1"
  local idempotency_key="${IDEMPOTENCY_PREFIX}-${RUN_ID}-${index}"
  local response_file="${tmp_dir}/response-${index}.json"
  local result_file="${tmp_dir}/result-${index}.tsv"
  local error_file="${tmp_dir}/error-${index}.txt"
  local metrics
  local exit_code

  set +e
  metrics="$(
    curl -sS -o "${response_file}" \
      --connect-timeout "${CONNECT_TIMEOUT}" \
      --max-time "${MAX_TIME}" \
      -w "%{http_code}\t%{time_total}" \
      -X POST "${BASE_URL}/api/checkout" \
      -H "Content-Type: application/json" \
      -H "Idempotency-Key: ${idempotency_key}" \
      -d "{\"productId\":\"${PRODUCT_ID}\",\"quantity\":${QUANTITY}}" \
      2>"${error_file}"
  )"
  exit_code="$?"
  set -e

  if [[ "${exit_code}" -eq 0 ]]; then
    printf '%s\t%s\t%s\n' "${metrics}" "${exit_code}" "${idempotency_key}" > "${result_file}"
  else
    printf 'CURL_ERROR\t0\t%s\t%s\n' "${exit_code}" "${idempotency_key}" > "${result_file}"
  fi
}

for index in $(seq 1 "${REQUESTS}"); do
  run_one "${index}" &

  while [[ "$(jobs -pr | wc -l | tr -d ' ')" -ge "${CONCURRENCY}" ]]; do
    sleep 0.02
  done
done

wait

finished_at_epoch="$(date +%s)"
duration_seconds="$((finished_at_epoch - started_at_epoch))"
if [[ "${duration_seconds}" -lt 1 ]]; then
  duration_seconds=1
fi

cat "${tmp_dir}"/result-*.tsv > "${results_file}"

after_inventory="$(fetch_inventory)"
after_available="$(printf '%s' "${after_inventory}" | jq -r '.available')"
after_reserved="$(printf '%s' "${after_inventory}" | jq -r '.reserved')"
success_count="$(awk -F '\t' '$1 == "201" { count++ } END { print count + 0 }' "${results_file}")"
expected_reserved_delta="$((success_count * QUANTITY))"
actual_reserved_delta="$((after_reserved - before_reserved))"
invariant_result="PASS"
if [[ "${actual_reserved_delta}" -ne "${expected_reserved_delta}" || "${after_available}" -lt 0 ]]; then
  invariant_result="FAIL"
fi

{
  echo "Checkout load test summary"
  echo "Run id: ${RUN_ID}"
  echo "Base URL: ${BASE_URL}"
  echo "Product: ${PRODUCT_ID}"
  echo "Requests: ${REQUESTS}"
  echo "Concurrency: ${CONCURRENCY}"
  echo "Quantity per request: ${QUANTITY}"
  echo "Connect timeout seconds: ${CONNECT_TIMEOUT}"
  echo "Max request time seconds: ${MAX_TIME}"
  echo "Duration seconds: ${duration_seconds}"
  echo "Approx requests/sec: $(awk -v r="${REQUESTS}" -v s="${duration_seconds}" 'BEGIN { printf "%.2f", r / s }')"
  echo
  echo "Status counts:"
  awk -F '\t' '{ counts[$1]++ } END { for (status in counts) printf "%s\t%s\n", status, counts[status] }' "${results_file}" | sort
  echo
  echo "Latency seconds:"
  awk -F '\t' '
    $1 != "CURL_ERROR" {
      count++;
      total += $2;
      if (min == "" || $2 < min) min = $2;
      if ($2 > max) max = $2;
    }
    END {
      if (count == 0) {
        print "no successful curl measurements";
      } else {
        printf "count\t%d\n", count;
        printf "avg\t%.4f\n", total / count;
        printf "min\t%.4f\n", min;
        printf "max\t%.4f\n", max;
      }
    }
  ' "${results_file}"
  echo
  echo "Inventory correctness:"
  echo "before_available	${before_available}"
  echo "before_reserved	${before_reserved}"
  echo "after_available	${after_available}"
  echo "after_reserved	${after_reserved}"
  echo "successful_201	${success_count}"
  echo "expected_reserved_delta	${expected_reserved_delta}"
  echo "actual_reserved_delta	${actual_reserved_delta}"
  echo "invariant	${invariant_result}"
} | tee "${summary_file}"

echo
echo "Raw results: ${results_file}"
echo "Summary: ${summary_file}"
