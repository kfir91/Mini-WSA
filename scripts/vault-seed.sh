#!/usr/bin/env bash
# Start Vault (if needed), seed KV + AppRole, write gitignored .vault/ files.
# Uses docker compose exec (no host Vault CLI). Persists postgres_password in
# .vault/postgres_password so Compose Postgres volumes survive Vault -dev restarts.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

step() { echo ""; echo "==> $*"; }

VAULT_DIR="${REPO_ROOT}/.vault"
mkdir -p "${VAULT_DIR}"

ROOT_TOKEN="${VAULT_DEV_ROOT_TOKEN_ID:-wsa-dev-root}"

vault_exec() {
  docker compose --env-file .env exec -T \
    -e "VAULT_ADDR=http://127.0.0.1:8200" \
    -e "VAULT_TOKEN=${ROOT_TOKEN}" \
    vault vault "$@"
}

vault_exec_allow_fail() {
  vault_exec "$@" >/dev/null 2>&1 || true
}

# Compose interpolates the whole file even for `up vault`; avoid blank-password warnings.
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-pending-vault-seed}"

step "Ensuring Vault is up"
docker compose --env-file .env up -d vault

deadline=$((SECONDS + 90))
while true; do
  raw="$(docker compose --env-file .env ps vault 2>&1 || true)"
  if echo "${raw}" | grep -qi healthy; then
    echo "  Vault healthy"
    break
  fi
  if (( SECONDS >= deadline )); then
    echo "${raw}"
    echo "Timed out waiting for Vault healthy." >&2
    exit 1
  fi
  sleep 3
done

step "Seeding Vault KV + AppRole"
vault_exec_allow_fail secrets enable -path=secret kv-v2
vault_exec_allow_fail auth enable approle

cat >"${VAULT_DIR}/wsa-local.hcl" <<'EOF'
path "secret/data/wsa/local" {
  capabilities = ["read"]
}
path "secret/metadata/wsa/local" {
  capabilities = ["read"]
}
path "secret/data/wsa" {
  capabilities = ["read"]
}
path "secret/metadata/wsa" {
  capabilities = ["read"]
}
EOF

vault_exec policy write wsa-local - <"${VAULT_DIR}/wsa-local.hcl"

vault_exec write auth/approle/role/wsa-local \
  token_policies=wsa-local \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0

PASS_FILE="${VAULT_DIR}/postgres_password"
if [[ -f "${PASS_FILE}" ]]; then
  PG_PASS="$(tr -d '\r\n' <"${PASS_FILE}")"
  echo "  Reusing .vault/postgres_password"
else
  PG_PASS="$(openssl rand -hex 24 2>/dev/null || head -c 24 /dev/urandom | xxd -p -c 24)"
  printf '%s' "${PG_PASS}" >"${PASS_FILE}"
  echo "  Generated new postgres_password"
fi

SONAR_TOKEN=""
if [[ -f "${VAULT_DIR}/sonar_token" ]]; then
  SONAR_TOKEN="$(tr -d '\r\n' <"${VAULT_DIR}/sonar_token")"
fi

vault_exec kv put secret/wsa/local \
  "postgres_password=${PG_PASS}" \
  "spring.datasource.password=${PG_PASS}" \
  "sonar_token=${SONAR_TOKEN}"

ROLE_ID="$(vault_exec read -field=role_id auth/approle/role/wsa-local/role-id | tr -d '\r\n')"
SECRET_ID="$(vault_exec write -field=secret_id -f auth/approle/role/wsa-local/secret-id | tr -d '\r\n')"

cat >"${VAULT_DIR}/approle.env" <<EOF
VAULT_ROLE_ID=${ROLE_ID}
VAULT_SECRET_ID=${SECRET_ID}
EOF

export POSTGRES_PASSWORD="${PG_PASS}"
export ADMINER_PASSWORD="${PG_PASS}"
export VAULT_ROLE_ID="${ROLE_ID}"
export VAULT_SECRET_ID="${SECRET_ID}"

echo "  Wrote .vault/approle.env (gitignored)"
echo "  Vault seed complete"
