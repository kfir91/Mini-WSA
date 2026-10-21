#!/usr/bin/env bash
# Bootstrap WSA on macOS: install missing tools, start Docker infra, build, run both apps, smoke-test.
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

load_env() {
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
  export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
}

wait_compose_healthy() {
  step "Waiting for Postgres and Kafka healthy"
  local deadline=$((SECONDS + 180))
  while true; do
    local raw
    raw="$(docker compose --env-file .env ps 2>&1 || true)"
    if echo "${raw}" | grep -qiE 'postgres.*healthy' && echo "${raw}" | grep -qiE 'kafka.*healthy'; then
      echo "  Infra healthy"
      return
    fi
    if (( SECONDS >= deadline )); then
      echo "${raw}"
      echo "Timed out waiting for healthy postgres/kafka." >&2
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
if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "  Created .env from .env.example"
else
  echo "  Found .env"
fi
load_env

if [[ "${SKIP_COMPOSE}" -eq 0 ]]; then
  step "Starting Compose infra"
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
  if ! port_in_use 8081; then
    start_app ingest-api 8081 target/bootstrap-ingest.log
  else
    echo "  Port 8081 already in use — leaving existing process"
  fi
  if ! port_in_use 8080; then
    start_app analytics-service 8080 target/bootstrap-analytics.log
  else
    echo "  Port 8080 already in use — leaving existing process"
  fi

  wait_http_ok "http://localhost:8081/actuator/health" "ingest-api"
  wait_http_ok "http://localhost:8080/actuator/health" "analytics-service"

  step "Smoke test (ingest → Kafka → Postgres)"
  eid="evt-bootstrap-$(date +%Y%m%d%H%M%S)"
  curl -fsS -X POST "http://localhost:8081/v1/events/ingest" \
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
    total="$(curl -fsS "http://localhost:8080/v1/stats/summary?configId=14227" \
      | python3 -c 'import json,sys; print(json.load(sys.stdin).get("totalEvents",0))' 2>/dev/null || echo 0)"
    if [[ "${total}" -ge 1 ]]; then
      echo "  Summary totalEvents=${total}"
      ok=1
      break
    fi
  done
  if [[ "${ok}" -ne 1 ]]; then
    echo "Analytics did not show events in summary within ~60s." >&2
    exit 1
  fi
fi

echo ""
echo "OK — WSA is running locally."
echo "  Hub:       http://localhost:8080/"
echo "  Ingest:    http://localhost:8081/swagger-ui/index.html"
echo "  Analytics: http://localhost:8080/swagger-ui/index.html"
echo "  Kafka UI:  http://localhost:8083/"
echo "  Adminer:   http://localhost:8082/"
echo "  Work dir:  ${REPO_ROOT}"
echo "  App logs:  ${REPO_ROOT}/target/bootstrap-ingest.log"
echo "             ${REPO_ROOT}/target/bootstrap-analytics.log"
echo "  PIDs:      ${REPO_ROOT}/target/bootstrap-*.pid"
echo ""
echo "Demo data (optional): ./scripts/seed-demo-data.sh"
echo "Stop apps: kill \$(cat target/bootstrap-*.pid); docker compose --env-file .env down"
