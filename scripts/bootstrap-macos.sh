#!/usr/bin/env bash
# Bootstrap WSA on macOS: install missing tools, start Docker infra, build, run analytics + ingest + client, smoke-test.
#
# Usage:
#   chmod +x scripts/*.sh generator/*.sh
#   ./scripts/bootstrap-macos.sh
#   ./scripts/bootstrap-macos.sh --skip-install --full-verify
#
# Flags:
#   --skip-install   Do not brew install missing tools
#   --full-verify    mvn clean verify (default: install -DskipTests)
#   --skip-apps      Stop after build (do not start Spring apps)
#   --skip-compose   Skip docker compose up
set -euo pipefail

SKIP_INSTALL=0
FULL_VERIFY=0
SKIP_APPS=0
SKIP_COMPOSE=0

for arg in "$@"; do
  case "$arg" in
    --skip-install) SKIP_INSTALL=1 ;;
    --full-verify) FULL_VERIFY=1 ;;
    --skip-apps) SKIP_APPS=1 ;;
    --skip-compose) SKIP_COMPOSE=1 ;;
    -h|--help)
      sed -n '2,16p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
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

step() { printf '\n==> %s\n' "$1"; }

have() { command -v "$1" >/dev/null 2>&1; }

java_major() {
  if ! have java; then echo 0; return; fi
  local v
  v="$(java -version 2>&1 | head -n1 || true)"
  if [[ "$v" =~ version\ \"([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
  elif [[ "$v" =~ version\ \"1\.([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
  else
    echo 0
  fi
}

ensure_brew() {
  if have brew; then return; fi
  echo "Homebrew not found. Install from https://brew.sh then re-run." >&2
  exit 1
}

ensure_tools() {
  if [[ "${SKIP_INSTALL}" -eq 1 ]]; then
    echo "  Skipping installs (--skip-install)"
    return
  fi
  step "Checking / installing prerequisites"
  ensure_brew

  local major
  major="$(java_major)"
  if [[ "${major}" -lt 17 ]]; then
    echo "  Installing Temurin 17..."
    brew install --cask temurin@17 || brew install --cask temurin
    major="$(java_major)"
    if [[ "${major}" -lt 17 ]]; then
      echo "JDK 17+ still not on PATH. Check JAVA_HOME." >&2
      exit 1
    fi
  fi
  echo "  Java major=${major}"

  if ! have mvn; then
    echo "  Installing Maven..."
    brew install maven
  fi
  echo "  Maven OK: $(mvn -version 2>&1 | head -n1)"

  if ! have docker; then
    echo "  Installing Docker Desktop (cask)..."
    brew install --cask docker || true
    echo ""
    echo "  Start Docker Desktop (or OrbStack) from Applications, wait until running,"
    echo "  then re-run: ./scripts/bootstrap-macos.sh --skip-install"
    exit 1
  fi
  echo "  Docker CLI OK"
}

wait_docker() {
  step "Waiting for Docker daemon"
  if have open && [[ -d "/Applications/Docker.app" ]]; then
    if ! docker info >/dev/null 2>&1; then
      echo "  Opening Docker.app..."
      open -a Docker || true
    fi
  fi
  local deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do
    if docker info >/dev/null 2>&1; then
      echo "  Docker daemon ready"
      return
    fi
    sleep 5
  done
  echo "Docker daemon not ready. Start Docker Desktop / OrbStack / Colima, then re-run with --skip-install." >&2
  exit 1
}

merge_missing_env_keys() {
  local example=".env.example" target=".env"
  if [[ ! -f "${example}" ]]; then
    return
  fi
  if [[ ! -f "${target}" ]]; then
    cp "${example}" "${target}"
    echo "  Created .env from .env.example"
    return
  fi
  local appended=0
  local key
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ "${line}" =~ ^[[:space:]]*# ]] && continue
    [[ "${line}" != *=* ]] && continue
    key="${line%%=*}"
    key="$(echo "${key}" | tr -d '[:space:]')"
    [[ -z "${key}" ]] && continue
    if ! grep -qE "^[[:space:]]*${key}=" "${target}"; then
      if [[ "${appended}" -eq 0 ]]; then
        printf '\n# --- added from .env.example (missing keys) ---\n' >>"${target}"
      fi
      printf '%s\n' "${line}" >>"${target}"
      appended=$((appended + 1))
    fi
  done <"${example}"
  if [[ "${appended}" -gt 0 ]]; then
    echo "  Merged ${appended} missing key(s) from .env.example into .env"
  fi
}

apply_wsa_derived_env() {
  export WSA_HOST="${WSA_HOST:-localhost}"
  export WSA_INGEST_PORT="${WSA_INGEST_PORT:-8081}"
  export WSA_ANALYTICS_PORT="${WSA_ANALYTICS_PORT:-8080}"
  export WSA_CLIENT_PORT="${WSA_CLIENT_PORT:-8084}"
  export WSA_ADMINER_PORT="${WSA_ADMINER_PORT:-8082}"
  export WSA_KAFKA_UI_PORT="${WSA_KAFKA_UI_PORT:-8083}"
  export WSA_POSTGRES_PORT="${WSA_POSTGRES_PORT:-5432}"
  export WSA_KAFKA_PORT="${WSA_KAFKA_PORT:-9092}"
  export WSA_VAULT_PORT="${WSA_VAULT_PORT:-8200}"
  local db_name="${POSTGRES_DB:-wsa}"

  export WSA_ANALYTICS_BASE_URL="http://${WSA_HOST}:${WSA_ANALYTICS_PORT}"
  export ADMINER_URL="http://${WSA_HOST}:${WSA_ADMINER_PORT}/"
  export VAULT_URI="http://${WSA_HOST}:${WSA_VAULT_PORT}"
  export KAFKA_BOOTSTRAP_SERVERS="${WSA_HOST}:${WSA_KAFKA_PORT}"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${WSA_HOST}:${WSA_POSTGRES_PORT}/${db_name}"

  if [[ -z "${KAFKA_ADVERTISED_LISTENERS:-}" ]]; then
    export KAFKA_ADVERTISED_LISTENERS="PLAINTEXT://${WSA_HOST}:${WSA_KAFKA_PORT},INTERNAL://kafka:29092"
  fi
}

load_env() {
  set -a
  # shellcheck disable=SC1091
  source .env
  if [[ -f .vault/approle.env ]]; then
    # shellcheck disable=SC1091
    source .vault/approle.env
  fi
  if [[ -f .vault/postgres_password ]]; then
    POSTGRES_PASSWORD="$(tr -d '\r\n' <.vault/postgres_password)"
    export POSTGRES_PASSWORD
    export ADMINER_PASSWORD="${POSTGRES_PASSWORD}"
  fi
  set +a
  apply_wsa_derived_env
}

wait_compose_healthy() {
  step "Waiting for Vault, Postgres, and Kafka healthy"
  local deadline=$((SECONDS + 180))
  while true; do
    local raw
    raw="$(docker compose --env-file .env ps 2>&1 || true)"
    if echo "${raw}" | grep -qiE 'vault.*healthy' \
      && echo "${raw}" | grep -qiE 'postgres.*healthy' \
      && echo "${raw}" | grep -qiE 'kafka.*healthy'; then
      echo "  Infra healthy"
      return
    fi
    if (( SECONDS >= deadline )); then
      echo "${raw}"
      echo "Timed out waiting for healthy vault/postgres/kafka." >&2
      exit 1
    fi
    sleep 5
  done
}

port_in_use() {
  local port="$1"
  if have lsof; then
    lsof -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
  else
    return 1
  fi
}

start_app() {
  local module="$1"
  local port="$2"
  local log="$3"
  echo "  Starting ${module} on :${port} (log: ${log})"
  (
    load_env
    export SERVER_PORT="${port}"
    export KAFKA_BOOTSTRAP_SERVERS
    export SPRING_DATASOURCE_URL
    export VAULT_URI
    export ADMINER_URL
    export WSA_ANALYTICS_BASE_URL
    mvn -pl "${module}" spring-boot:run
  ) >"${log}" 2>&1 &
  echo $! >"target/bootstrap-${module}.pid"
}

wait_http_ok() {
  local url="$1" label="$2" timeout_sec="${3:-180}"
  local deadline=$((SECONDS + timeout_sec))
  while (( SECONDS < deadline )); do
    if curl -fsS -o /dev/null --max-time 5 "${url}"; then
      echo "  ${label} OK (${url})"
      return
    fi
    sleep 3
  done
  echo "${label} not reachable at ${url}. Check ${REPO_ROOT}/target/bootstrap-*.log" >&2
  exit 1
}

# --- main ---
chmod +x "${SCRIPT_DIR}"/*.sh "${REPO_ROOT}/generator"/*.sh 2>/dev/null || true

ensure_tools
wait_docker

step "Working directory: ${REPO_ROOT}"

step "Ensuring .env"
merge_missing_env_keys
if [[ -f .env ]]; then
  echo "  Found .env"
fi
load_env

INGEST_BASE="http://${WSA_HOST}:${WSA_INGEST_PORT}"
ANALYTICS_BASE="http://${WSA_HOST}:${WSA_ANALYTICS_PORT}"
CLIENT_BASE="http://${WSA_HOST}:${WSA_CLIENT_PORT}"

if [[ "${SKIP_COMPOSE}" -eq 0 ]]; then
  step "Seeding HashiCorp Vault (KV + AppRole)"
  bash "${SCRIPT_DIR}/vault-seed.sh"
  load_env
  step "Starting Compose infra (Postgres password from Vault)"
  docker compose --env-file .env up -d
  wait_compose_healthy
else
  echo "  Skipping compose (--skip-compose)"
fi

if [[ "${FULL_VERIFY}" -eq 1 ]]; then
  step "mvn clean verify (JaCoCo gate)"
  mvn clean verify
else
  step "mvn clean install -DskipTests"
  mvn clean install -DskipTests
fi
echo "  Build OK"

if [[ "${SKIP_APPS}" -eq 0 ]]; then
  step "Starting Spring apps (background)"
  mkdir -p target
  # Analytics first on empty DB (Flyway owns shared schema); ingest uses a separate history table.
  if ! port_in_use "${WSA_ANALYTICS_PORT}"; then
    start_app analytics-service "${WSA_ANALYTICS_PORT}" target/bootstrap-analytics.log
  else
    echo "  Port ${WSA_ANALYTICS_PORT} already in use — leaving existing process"
  fi
  if ! port_in_use "${WSA_INGEST_PORT}"; then
    start_app ingest-api "${WSA_INGEST_PORT}" target/bootstrap-ingest.log
  else
    echo "  Port ${WSA_INGEST_PORT} already in use — leaving existing process"
  fi
  if ! port_in_use "${WSA_CLIENT_PORT}"; then
    start_app client "${WSA_CLIENT_PORT}" target/bootstrap-client.log
  else
    echo "  Port ${WSA_CLIENT_PORT} already in use — leaving existing process"
  fi

  wait_http_ok "${ANALYTICS_BASE}/actuator/health" "analytics-service"
  wait_http_ok "${INGEST_BASE}/actuator/health" "ingest-api"
  wait_http_ok "${CLIENT_BASE}/actuator/health" "client"

  step "Smoke test (ingest → Kafka → Postgres)"
  eid="evt-bootstrap-$(date +%Y%m%d%H%M%S)"
  curl -fsS -X POST "${INGEST_BASE}/v1/events/ingest" \
    -H "Content-Type: application/json" \
    -d "{
      \"eventId\": \"${eid}\",
      \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
      \"configId\": 14227,
      \"policyId\": \"pol_web1\",
      \"clientIp\": \"203.0.113.99\",
      \"hostname\": \"www.example.com\",
      \"path\": \"/api/v1/login\",
      \"method\": \"POST\",
      \"statusCode\": 403,
      \"userAgent\": \"WSA-Bootstrap/1.0\",
      \"rule\": {
        \"id\": \"950001\",
        \"name\": \"SQL_INJECTION\",
        \"message\": \"SQL Injection Attack Detected\",
        \"severity\": \"CRITICAL\",
        \"category\": \"INJECTION\"
      },
      \"action\": \"DENY\",
      \"geoLocation\": { \"country\": \"CN\", \"city\": \"Beijing\" },
      \"requestSize\": 1024,
      \"responseSize\": 256
    }" >/dev/null
  echo "  Ingest 201 (${eid})"

  ok=0
  for _ in $(seq 1 30); do
    sleep 2
    found="$(curl -fsS "${ANALYTICS_BASE}/v1/events/samples?configId=14227&limit=100&offset=0" \
      | python3 -c "import json,sys; eid=sys.argv[1]; items=json.load(sys.stdin).get('items') or []; print(sum(1 for i in items if i.get('eventId')==eid))" "${eid}" 2>/dev/null || echo 0)"
    if [[ "${found}" -ge 1 ]]; then
      total="$(curl -fsS "${ANALYTICS_BASE}/v1/stats/summary?configId=14227" \
        | python3 -c 'import json,sys; print(json.load(sys.stdin).get("totalEvents",0))' 2>/dev/null || echo 0)"
      echo "  Sample found for ${eid}; summary totalEvents=${total}"
      ok=1
      break
    fi
  done
  if [[ "${ok}" -ne 1 ]]; then
    echo "Analytics did not show eventId=${eid} in samples within ~60s." >&2
    exit 1
  fi
fi

echo ""
echo "OK — WSA is running locally."
echo "  Hub:       ${CLIENT_BASE}/"
echo "  Stats UI:  ${CLIENT_BASE}/stats"
echo "  Ingest:    ${INGEST_BASE}/swagger-ui/index.html"
echo "  Analytics: ${ANALYTICS_BASE}/swagger-ui/index.html"
echo "  Kafka UI:  http://${WSA_HOST}:${WSA_KAFKA_UI_PORT}/"
echo "  Adminer:   http://${WSA_HOST}:${WSA_ADMINER_PORT}/"
echo "  Work dir:  ${REPO_ROOT}"
echo "  App logs:  ${REPO_ROOT}/target/bootstrap-ingest.log"
echo "             ${REPO_ROOT}/target/bootstrap-analytics.log"
echo "             ${REPO_ROOT}/target/bootstrap-client.log"
echo "  PIDs:      ${REPO_ROOT}/target/bootstrap-*.pid"
echo ""
echo "Demo data (optional): ./scripts/seed-demo-data.sh"
echo "Stop apps: kill \$(cat target/bootstrap-*.pid); docker compose --env-file .env down"
