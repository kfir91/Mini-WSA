#!/usr/bin/env bash
# Generate synthetic security events into ingest-api (macOS / Linux).
# Usage: ./generator/generate.sh [--base-url URL] [--count N] [--rate N] [--wave sql|xss|mixed] [--config-id N] [--burst-size N]
set -euo pipefail

BASE_URL=""
COUNT=20
RATE=10
WAVE="sql"
CONFIG_ID=14227
BURST_SIZE=50

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --count) COUNT="$2"; shift 2 ;;
    --rate) RATE="$2"; shift 2 ;;
    --wave) WAVE="$2"; shift 2 ;;
    --config-id) CONFIG_ID="$2"; shift 2 ;;
    --burst-size) BURST_SIZE="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--base-url URL] [--count N] [--rate N] [--wave sql|xss|mixed] [--config-id N] [--burst-size N]"
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
if [[ -z "${BASE_URL}" ]]; then
  BASE_URL="http://${WSA_HOST:-localhost}:${WSA_INGEST_PORT:-8081}"
fi

ARGS_LINE="--base-url ${BASE_URL} --count ${COUNT} --rate ${RATE} --wave ${WAVE} --config-id ${CONFIG_ID} --burst-size ${BURST_SIZE}"
echo "Building generator modules (quiet)..."
mvn -q -pl generator -am -DskipTests install
echo "Starting generator..."
mvn -q -pl generator -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.wsa.generator.GeneratorMain \
  "-Dexec.args=${ARGS_LINE}"
