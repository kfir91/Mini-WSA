#!/usr/bin/env bash
# Validate a clean WSA checkout: compose up, mvn verify, docker image builds (macOS / Linux).
# Usage: ./scripts/validate-clean-checkout.sh [--skip-compose] [--skip-docker-builds]
set -euo pipefail

SKIP_COMPOSE=0
SKIP_DOCKER_BUILDS=0
for arg in "$@"; do
  case "$arg" in
    --skip-compose) SKIP_COMPOSE=1 ;;
    --skip-docker-builds) SKIP_DOCKER_BUILDS=1 ;;
    -h|--help)
      echo "Usage: $0 [--skip-compose] [--skip-docker-builds]"
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

step "Checking .env"
if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "  Created .env from .env.example"
else
  echo "  Found .env"
fi

if [[ "${SKIP_COMPOSE}" -eq 0 ]]; then
  step "Starting Compose infra (postgres + kafka + adminer + kafka-ui)"
  if ! docker compose --env-file .env up -d; then
    echo "docker compose up failed (is Docker running? Desktop / OrbStack / Colima)." >&2
    exit 1
  fi

  step "Waiting for Postgres and Kafka healthy"
  deadline=$((SECONDS + 180))
  while true; do
    raw="$(docker compose --env-file .env ps 2>&1 || true)"
    if echo "${raw}" | grep -qiE 'postgres.*healthy' && echo "${raw}" | grep -qiE 'kafka.*healthy'; then
      echo "  Infra looks healthy"
      break
    fi
    if (( SECONDS >= deadline )); then
      echo "${raw}"
      echo "Timed out waiting for healthy postgres/kafka." >&2
      exit 1
    fi
    sleep 5
  done
else
  echo "  Skipping compose (--skip-compose)"
fi

step "mvn clean verify (JaCoCo >=95% LINE/BRANCH)"
mvn clean verify
echo "  verify PASSED"

if [[ "${SKIP_DOCKER_BUILDS}" -eq 0 ]]; then
  step "Building ingest-api image"
  docker build -f ingest-api/Dockerfile -t wsa-ingest .
  step "Building analytics-service image"
  docker build -f analytics-service/Dockerfile -t wsa-analytics .
  echo "  Docker images built"
else
  echo "  Skipping docker builds (--skip-docker-builds)"
fi

echo ""
echo "OK - clean-checkout checks passed."
echo "Start apps (two terminals; load .env first — see README):"
echo "  mvn -pl ingest-api spring-boot:run"
echo "  mvn -pl analytics-service spring-boot:run"
echo "Do NOT use -am with spring-boot:run (parent POM has no main class)."
