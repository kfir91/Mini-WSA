<#
.SYNOPSIS
  Validate that a clean WSA checkout can compose up, pass mvn verify, and build app images.

.DESCRIPTION
  Fails fast on the first error. Does not start the Spring apps (prints the documented spring-boot:run commands).

.EXAMPLE
  .\scripts\validate-clean-checkout.ps1
#>
param(
    [switch] $SkipDockerBuilds,
    [switch] $SkipCompose
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $repoRoot "pom.xml"))) {
    throw "Could not find WSA repo root (parent pom.xml missing)."
}
Set-Location $repoRoot

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

Write-Step "Checking .env"
$envFile = Join-Path $repoRoot ".env"
if (-not (Test-Path $envFile)) {
    Copy-Item (Join-Path $repoRoot ".env.example") $envFile
    Write-Host "  Created .env from .env.example" -ForegroundColor DarkYellow
} else {
    Write-Host "  Found .env" -ForegroundColor Green
}

if (-not $SkipCompose) {
    Write-Step "Starting Compose infra (postgres + kafka + adminer + kafka-ui)"
    docker compose --env-file .env up -d
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed (is Docker running? Desktop / OrbStack / Colima)."
    }

    Write-Step "Waiting for Postgres and Kafka healthy"
    $deadline = (Get-Date).AddMinutes(3)
    do {
        $raw = docker compose --env-file .env ps 2>&1 | Out-String
        $postgresOk = $raw -match "(?i)postgres[^\r\n]*healthy"
        $kafkaOk = $raw -match "(?i)kafka[^\r\n]*healthy"
        if ($postgresOk -and $kafkaOk) {
            Write-Host "  Infra looks healthy" -ForegroundColor Green
            break
        }
        if ((Get-Date) -ge $deadline) {
            Write-Host $raw
            throw "Timed out waiting for healthy postgres/kafka."
        }
        Start-Sleep -Seconds 5
    } while ($true)
} else {
    Write-Host "  Skipping compose (-SkipCompose)"
}

Write-Step "mvn clean verify (JaCoCo >=95% LINE/BRANCH)"
mvn clean verify
if ($LASTEXITCODE -ne 0) {
    throw "mvn clean verify failed."
}
Write-Host "  verify PASSED" -ForegroundColor Green

if (-not $SkipDockerBuilds) {
    Write-Step "Building ingest-api image"
    docker build -f ingest-api/Dockerfile -t wsa-ingest .
    if ($LASTEXITCODE -ne 0) {
        throw "ingest-api docker build failed."
    }

    Write-Step "Building analytics-service image"
    docker build -f analytics-service/Dockerfile -t wsa-analytics .
    if ($LASTEXITCODE -ne 0) {
        throw "analytics-service docker build failed."
    }
    Write-Host "  Docker images built" -ForegroundColor Green
} else {
    Write-Host "  Skipping docker builds (-SkipDockerBuilds)"
}

Write-Host ""
Write-Host "OK - clean-checkout checks passed." -ForegroundColor Green
Write-Host "Start apps (two terminals; load .env first — see README):"
Write-Host "  mvn -pl ingest-api spring-boot:run"
Write-Host "  mvn -pl analytics-service spring-boot:run"
Write-Host "Do NOT use -am with spring-boot:run (parent POM has no main class)."
