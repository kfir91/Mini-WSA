<#
.SYNOPSIS
  Start Vault (if needed), seed KV + AppRole, write gitignored .vault/ files.

.DESCRIPTION
  Uses docker compose exec (no host Vault CLI). Persists postgres_password in
  .vault/postgres_password so Compose Postgres volumes survive Vault -dev restarts.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

Set-Location -LiteralPath $RepoRoot

$vaultDir = Join-Path $RepoRoot ".vault"
New-Item -ItemType Directory -Force -Path $vaultDir | Out-Null

$rootToken = $env:VAULT_DEV_ROOT_TOKEN_ID
if ([string]::IsNullOrWhiteSpace($rootToken)) { $rootToken = "wsa-dev-root" }

# Compose interpolates the whole file even for `up vault`; avoid blank-password warnings/abort.
if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD)) {
    $env:POSTGRES_PASSWORD = "pending-vault-seed"
}

Write-Step "Ensuring Vault is up"
docker compose --env-file .env up -d vault
if ($LASTEXITCODE -ne 0) { throw "Failed to start vault service." }

$deadline = (Get-Date).AddSeconds(90)
do {
    # Docker may write interpolation warnings to stderr; do not treat as terminating errors.
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $raw = docker compose --env-file .env ps vault 2>&1 | Out-String
    $ErrorActionPreference = $prevEap
    if ($raw -match "(?i)healthy") { break }
    if ((Get-Date) -ge $deadline) {
        Write-Host $raw
        throw "Timed out waiting for Vault healthy."
    }
    Start-Sleep -Seconds 3
} while ($true)
Write-Host "  Vault healthy" -ForegroundColor Green

function Invoke-Vault([string[]] $VaultArgs) {
    $all = @(
        "compose", "--env-file", ".env", "exec", "-T",
        "-e", "VAULT_ADDR=http://127.0.0.1:8200",
        "-e", "VAULT_TOKEN=$rootToken",
        "vault", "vault"
    ) + $VaultArgs
    & docker @all
    if ($LASTEXITCODE -ne 0) {
        throw "vault $($VaultArgs -join ' ') failed (exit $LASTEXITCODE)."
    }
}

function Invoke-VaultAllowFail([string[]] $VaultArgs) {
    $all = @(
        "compose", "--env-file", ".env", "exec", "-T",
        "-e", "VAULT_ADDR=http://127.0.0.1:8200",
        "-e", "VAULT_TOKEN=$rootToken",
        "vault", "vault"
    ) + $VaultArgs
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & docker @all 2>&1 | Out-Null
    $ErrorActionPreference = $prevEap
}

Write-Step "Seeding Vault KV + AppRole"
# Dev mode already enables secret/ as kv-v2; ignore errors if already present.
Invoke-VaultAllowFail @("secrets", "enable", "-path=secret", "kv-v2")
Invoke-VaultAllowFail @("auth", "enable", "approle")

$policy = @"
path "secret/data/wsa/local" {
  capabilities = ["read"]
}
path "secret/metadata/wsa/local" {
  capabilities = ["read"]
}
# Spring Cloud Vault may also probe the application-name parent path; keep it readable no-op.
path "secret/data/wsa" {
  capabilities = ["read"]
}
path "secret/metadata/wsa" {
  capabilities = ["read"]
}
"@
$policyBytes = [System.Text.Encoding]::UTF8.GetBytes($policy)
$policyFile = Join-Path $vaultDir "wsa-local.hcl"
[System.IO.File]::WriteAllBytes($policyFile, $policyBytes)

Get-Content -LiteralPath $policyFile -Raw | docker compose --env-file .env exec -T `
    -e "VAULT_ADDR=http://127.0.0.1:8200" `
    -e "VAULT_TOKEN=$rootToken" `
    vault vault policy write wsa-local -
if ($LASTEXITCODE -ne 0) { throw "Failed to write Vault policy." }

Invoke-Vault @(
    "write", "auth/approle/role/wsa-local",
    "token_policies=wsa-local",
    "token_ttl=1h",
    "token_max_ttl=4h",
    "secret_id_ttl=0"
)

$passFile = Join-Path $vaultDir "postgres_password"
if (Test-Path -LiteralPath $passFile) {
    $pgPass = (Get-Content -LiteralPath $passFile -Raw).Trim().Trim("`0")
    Write-Host "  Reusing .vault/postgres_password" -ForegroundColor DarkYellow
} else {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $pgPass = -join ($bytes | ForEach-Object { $_.ToString("x2") })
    Write-Host "  Generated new postgres_password" -ForegroundColor DarkYellow
}
# Always rewrite without BOM/newline so Compose + Vault see the identical secret.
[System.IO.File]::WriteAllText($passFile, $pgPass, (New-Object System.Text.UTF8Encoding $false))

$sonarFile = Join-Path $vaultDir "sonar_token"
$sonarToken = ""
if (Test-Path -LiteralPath $sonarFile) {
    $sonarToken = (Get-Content -LiteralPath $sonarFile -Raw).Trim()
}

Invoke-Vault @(
    "kv", "put", "secret/wsa/local",
    "postgres_password=$pgPass",
    "spring.datasource.password=$pgPass",
    "sonar_token=$sonarToken"
)

function Get-VaultField([string[]] $VaultArgs) {
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $raw = docker compose --env-file .env exec -T `
        -e "VAULT_ADDR=http://127.0.0.1:8200" `
        -e "VAULT_TOKEN=$rootToken" `
        vault vault @VaultArgs 2>&1 | Out-String
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($exit -ne 0) {
        throw "vault $($VaultArgs -join ' ') failed (exit $exit): $raw"
    }
    # Docker/PowerShell may append CR/LF or noise lines; keep the first non-empty token.
    $line = ($raw -split "(`r`n|`n)" | Where-Object { $_.Trim() -ne "" } | Select-Object -First 1)
    return $line.Trim()
}

$roleId = Get-VaultField @("read", "-field=role_id", "auth/approle/role/wsa-local/role-id")
if ([string]::IsNullOrWhiteSpace($roleId)) {
    throw "Failed to read AppRole role_id."
}

$secretId = Get-VaultField @("write", "-field=secret_id", "-f", "auth/approle/role/wsa-local/secret-id")
if ([string]::IsNullOrWhiteSpace($secretId)) {
    throw "Failed to create AppRole secret_id."
}

$approleEnv = Join-Path $vaultDir "approle.env"
# UTF-8 without BOM — a BOM breaks AppRole ids when sourced into process env.
$approleBody = "VAULT_ROLE_ID=$roleId`nVAULT_SECRET_ID=$secretId`n"
[System.IO.File]::WriteAllText($approleEnv, $approleBody, (New-Object System.Text.UTF8Encoding $false))

$env:POSTGRES_PASSWORD = $pgPass
$env:ADMINER_PASSWORD = $pgPass
$env:VAULT_ROLE_ID = $roleId
$env:VAULT_SECRET_ID = $secretId

Write-Host "  Wrote .vault/approle.env (gitignored)" -ForegroundColor Green
Write-Host "  Vault seed complete" -ForegroundColor Green
