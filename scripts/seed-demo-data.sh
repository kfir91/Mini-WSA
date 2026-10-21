#!/usr/bin/env bash
# Fill Kafka + Postgres with the current WSA demo dataset (macOS / Linux).
# Usage: ./scripts/seed-demo-data.sh [--event-count N] [--rate N] [--skip-generator] [--skip-alerts] [--skip-fire]
set -euo pipefail

INGEST_URL=""
ANALYTICS_URL=""
EVENT_COUNT=10000
RATE=100
CONFIG_ID=14227
BURST_SIZE=10
WAIT_SECONDS=0
SKIP_GENERATOR=0
SKIP_ALERTS=0
SKIP_FIRE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ingest-url) INGEST_URL="$2"; shift 2 ;;
    --analytics-url) ANALYTICS_URL="$2"; shift 2 ;;
    --event-count) EVENT_COUNT="$2"; shift 2 ;;
    --rate) RATE="$2"; shift 2 ;;
    --config-id) CONFIG_ID="$2"; shift 2 ;;
    --burst-size) BURST_SIZE="$2"; shift 2 ;;
    --wait-seconds) WAIT_SECONDS="$2"; shift 2 ;;
    --skip-generator) SKIP_GENERATOR=1; shift ;;
    --skip-alerts) SKIP_ALERTS=1; shift ;;
    --skip-fire) SKIP_FIRE=1; shift ;;
    -h|--help)
      echo "Usage: $0 [--event-count N] [--rate N] [--skip-generator] [--skip-alerts] [--skip-fire]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
if [[ ! -f "${REPO_ROOT}/pom.xml" ]]; then
  echo "Could not find WSA repo root (parent pom.xml missing)." >&2
  exit 1
fi
cd "${REPO_ROOT}"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi
WSA_HOST="${WSA_HOST:-localhost}"
WSA_INGEST_PORT="${WSA_INGEST_PORT:-8081}"
WSA_ANALYTICS_PORT="${WSA_ANALYTICS_PORT:-8080}"
WSA_CLIENT_PORT="${WSA_CLIENT_PORT:-8084}"
WSA_KAFKA_UI_PORT="${WSA_KAFKA_UI_PORT:-8083}"
CLIENT_BASE="http://${WSA_HOST}:${WSA_CLIENT_PORT}"
if [[ -z "${INGEST_URL}" ]]; then
  INGEST_URL="http://${WSA_HOST}:${WSA_INGEST_PORT}"
fi
if [[ -z "${ANALYTICS_URL}" ]]; then
  ANALYTICS_URL="http://${WSA_HOST}:${WSA_ANALYTICS_PORT}"
fi

step() { printf '\n==> %s\n' "$1"; }

wait_http_ok() {
  local url="$1" label="$2" timeout_sec="${3:-60}"
  local deadline=$((SECONDS + timeout_sec))
  while (( SECONDS < deadline )); do
    if curl -fsS -o /dev/null --max-time 5 "${url}"; then
      echo "  ${label} is up (${url})"
      return 0
    fi
    sleep 2
  done
  echo "${label} not reachable at ${url} within ${timeout_sec}s. Start the service first." >&2
  exit 1
}

total_events() {
  curl -fsS --max-time 10 "${ANALYTICS_URL}/v1/stats/summary?configId=${CONFIG_ID}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("totalEvents", 0))'
}

step "Checking services"
wait_http_ok "${INGEST_URL}/swagger-ui/index.html" "ingest-api"
wait_http_ok "${ANALYTICS_URL}/v1/stats/summary" "analytics-service"

before=0
before="$(total_events 2>/dev/null || echo 0)"
echo "  Events in Postgres for config ${CONFIG_ID} before seed: ${before}"

if [[ "${WAIT_SECONDS}" -le 0 ]]; then
  safe_rate="${RATE}"
  if [[ "${safe_rate}" -lt 1 ]]; then safe_rate=1; fi
  WAIT_SECONDS=$(( EVENT_COUNT / safe_rate * 3 + 60 ))
  if [[ "${WAIT_SECONDS}" -lt 120 ]]; then WAIT_SECONDS=120; fi
fi

if [[ "${SKIP_GENERATOR}" -eq 0 ]]; then
  step "Generating ${EVENT_COUNT} mixed events at ~${RATE}/s (ingest -> Kafka)"
  echo "  This can take several minutes at 10k+ volume."
  ./generator/generate.sh \
    --base-url "${INGEST_URL}" \
    --count "${EVENT_COUNT}" \
    --rate "${RATE}" \
    --wave mixed \
    --config-id "${CONFIG_ID}" \
    --burst-size "${BURST_SIZE}"

  step "Waiting for analytics to persist events (up to ${WAIT_SECONDS}s)"
  target=$(( before + (EVENT_COUNT * 80 / 100) ))
  if [[ "${target}" -lt $((before + 1)) ]]; then target=$((before + 1)); fi
  deadline=$((SECONDS + WAIT_SECONDS))
  current="${before}"
  while (( SECONDS < deadline )); do
    current="$(total_events 2>/dev/null || echo "${current}")"
    echo "  Postgres events: ${current} (need >= ${target})"
    if [[ "${current}" -ge "${target}" ]]; then
      break
    fi
    sleep 2
  done
  if [[ "${current}" -lt "${target}" ]]; then
    echo "Timed out waiting for events in Postgres (have ${current}, want >= ${target})." >&2
    exit 1
  fi
  echo "  Kafka consume + DB write OK"
else
  echo "  Skipping generator (--skip-generator)"
fi

if [[ "${SKIP_ALERTS}" -eq 0 ]]; then
  step "Creating example alert rules from demo/alert-rules.json"
  RULES_PATH="${REPO_ROOT}/demo/alert-rules.json"
  if [[ ! -f "${RULES_PATH}" ]]; then
    echo "Missing ${RULES_PATH}" >&2
    exit 1
  fi
  python3 - "${ANALYTICS_URL}" "${RULES_PATH}" <<'PY'
import json, sys, urllib.error, urllib.request

analytics_url, rules_path = sys.argv[1], sys.argv[2]
with open(rules_path, encoding="utf-8") as f:
    demos = json.load(f)
created = skipped = failed = 0
for demo in demos:
    body = json.dumps(demo["rule"]).encode("utf-8")
    req = urllib.request.Request(
        analytics_url.rstrip("/") + "/v1/alerts/define",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            resp.read()
        created += 1
        print(f"  + {demo.get('label', '?')}")
    except urllib.error.HTTPError as ex:
        if ex.code in (400, 409):
            skipped += 1
            print(f"  = {demo.get('label', '?')} (already exists)")
        else:
            failed += 1
            print(f"  ! {demo.get('label', '?')}: HTTP {ex.code}", file=sys.stderr)
    except Exception as ex:
        failed += 1
        print(f"  ! {demo.get('label', '?')}: {ex}", file=sys.stderr)
print(f"  Alerts: created={created} skipped={skipped} failed={failed}")
if failed:
    sys.exit(1)
PY
else
  echo "  Skipping alerts (--skip-alerts)"
fi

if [[ "${SKIP_FIRE}" -eq 0 ]]; then
  step "Priming demo alert windows (POST /demo/alerts/fire)"
  curl -fsS -X POST --max-time 30 "${ANALYTICS_URL}/demo/alerts/fire"
  echo ""
  step "Evaluating alerts"
  curl -fsS --max-time 30 "${ANALYTICS_URL}/v1/alerts/evaluate" \
    | python3 -c 'import json,sys; e=json.load(sys.stdin); f=sum(1 for x in e if x.get("status")=="FIRING" or x.get("firing") is True); print(f"  FIRING~={f} total={len(e)}")'
else
  echo "  Skipping demo fire (--skip-fire)"
fi

echo ""
echo "Seed complete."
echo "  Hub:       ${CLIENT_BASE}/"
echo "  Samples:   ${ANALYTICS_URL}/v1/events/samples?configId=${CONFIG_ID}&limit=20"
echo "  Alerts:    ${ANALYTICS_URL}/v1/alerts/evaluate"
echo "  Kafka UI:  http://${WSA_HOST}:${WSA_KAFKA_UI_PORT}/"
echo "  Adminer:   ${ANALYTICS_URL}/adminer-go.html"
