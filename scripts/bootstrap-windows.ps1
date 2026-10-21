<#
.SYNOPSIS
  Bootstrap WSA on Windows: install missing tools, start Docker infra, build, run both apps, smoke-test.

.DESCRIPTION
  Intended for a fresh or broken local machine. Re-runnable (skips tools already present).

  Steps:
    1. Clear stale docker-machine env
    2. Optionally install JDK 17 / Maven / Docker Desktop via winget
    3. Ensure Docker daemon is up
    4. Use ASCII work dir if the checkout path is non-ASCII (OpenAPI/Maven)
    5. Create .env, compose up, wait for postgres+kafka healthy
    6. mvn clean install -DskipTests (or -FullVerify)
    7. Start ingest-api (:8081) and analytics-service (:8080) in background
    8. Smoke-check health + one ingest to summary

.EXAMPLE
  .\scripts\bootstrap-windows.ps1

.EXAMPLE
  .\scripts\bootstrap-windows.ps1 -SkipInstall -FullVerify
#>
param(
    [switch] $SkipInstall,
    [switch] $FullVerify,
    [switch] $SkipApps,
    [switch] $SkipCompose,
    [string] $AsciiWorkDir = "C:\Temp\wsa-build"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-Command([string] $Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-JavaMajor {
    if (-not (Test-Command java)) { return 0 }
    $line = (& java -version 2>&1 | Out-String)
    if ($line -match 'version "(\d+)') { return [int]$Matches[1] }
    if ($line -match 'version "1\.(\d+)') { return [int]$Matches[1] }
    return 0
}

function Clear-StaleDockerEnv {
    Write-Step "Clearing stale DOCKER_* env (old docker-machine)"
    foreach ($n in @("DOCKER_HOST", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH")) {
        Remove-Item "Env:$n" -ErrorAction SilentlyContinue
        [Environment]::SetEnvironmentVariable($n, $null, "User")
    }
}

function Install-WithWinget([string] $Id, [string] $Label) {
    if (-not (Test-Command winget)) {
        throw "winget not found. Install $Label manually, or install App Installer from the Microsoft Store."
    }
    Write-Host "  Installing $Label ($Id) via winget..." -ForegroundColor DarkYellow
    winget install --id $Id -e --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0 -and $LASTEXITCODE -ne -1978335189) {
        Write-Host "  winget exit $LASTEXITCODE for $Label (continuing if tool becomes available)" -ForegroundColor DarkYellow
    }
}

function Refresh-Path {
    $machine = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $user = [Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = "$machine;$user"
}

function Ensure-Tools {
    if ($SkipInstall) {
        Write-Host "  Skipping installs (-SkipInstall)"
        return
    }

    Write-Step "Checking / installing prerequisites"

    $javaMajor = Get-JavaMajor
    if ($javaMajor -lt 17) {
        Install-WithWinget "EclipseAdoptium.Temurin.17.JDK" "JDK 17"
        Refresh-Path
        $javaMajor = Get-JavaMajor
        if ($javaMajor -lt 17) {
            throw "JDK 17+ still not on PATH. Open a new PowerShell after install, or set JAVA_HOME."
        }
    }
    Write-Host "  Java major=$javaMajor" -ForegroundColor Green

    if (-not (Test-Command mvn)) {
        Install-WithWinget "Apache.Maven" "Maven"
        Refresh-Path
        if (-not (Test-Command mvn)) {
            throw "mvn still not on PATH. Open a new PowerShell after install."
        }
    }
    Write-Host "  Maven OK: $((mvn -version 2>&1 | Select-Object -First 1))" -ForegroundColor Green

    if (-not (Test-Command docker)) {
        Install-WithWinget "Docker.DockerDesktop" "Docker Desktop"
        Refresh-Path
        Write-Host ""
        Write-Host "  Docker Desktop was installed (or already present). If this is the first install:" -ForegroundColor Yellow
        Write-Host "    1. Start Docker Desktop from the Start menu" -ForegroundColor Yellow
        Write-Host "    2. Complete first-run / WSL setup" -ForegroundColor Yellow
        Write-Host "    3. If hypervisor is not running, enable Virtualization + WSL features (README), then RESTART Windows" -ForegroundColor Yellow
        Write-Host "    4. Re-run: .\scripts\bootstrap-windows.ps1 -SkipInstall" -ForegroundColor Yellow
        Write-Host ""
        throw "Docker CLI not ready yet. Start Docker Desktop, then re-run with -SkipInstall."
    }
    Write-Host "  Docker CLI OK" -ForegroundColor Green
}

function Wait-DockerDaemon([int] $TimeoutSec = 180) {
    Write-Step "Waiting for Docker daemon"
    $dd = Join-Path ${env:ProgramFiles} "Docker\Docker\Docker Desktop.exe"
    if ((Test-Path $dd) -and -not (Get-Process "Docker Desktop" -ErrorAction SilentlyContinue)) {
        Write-Host "  Launching Docker Desktop..."
        Start-Process $dd
    }
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            docker info 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Write-Host "  Docker daemon ready" -ForegroundColor Green
                return
            }
        } catch { }
        Start-Sleep -Seconds 5
    }
    throw "Docker daemon did not become ready in ${TimeoutSec}s. Open Docker Desktop and fix any hypervisor/WSL errors, then re-run."
}

function Test-NonAsciiPath([string] $Path) {
    return [bool]($Path -match "[^\x00-\x7F]")
}

function Resolve-WorkRoot {
    $sourceRoot = Split-Path -Parent $PSScriptRoot
    if (-not (Test-Path (Join-Path $sourceRoot "pom.xml"))) {
        throw "Could not find WSA repo root (parent pom.xml missing next to scripts/)."
    }

    if (-not (Test-NonAsciiPath $sourceRoot)) {
        Write-Host "  Using source checkout: $sourceRoot" -ForegroundColor Green
        return $sourceRoot
    }

    Write-Step "Non-ASCII path detected - syncing to ASCII work dir"
    Write-Host "  Source (git): $sourceRoot"
    Write-Host "  Work (build): $AsciiWorkDir"
    Write-Host "  Git stays in the source folder; Maven/Compose run from the ASCII copy." -ForegroundColor DarkYellow

    New-Item -ItemType Directory -Force -Path $AsciiWorkDir | Out-Null
    robocopy $sourceRoot $AsciiWorkDir /MIR /NFL /NDL /NJH /NJS /nc /ns /np `
        /XD target .git .idea .vscode node_modules `
        /XF "*.iml" | Out-Null
    $rc = $LASTEXITCODE
    if ($rc -ge 8) {
        throw "robocopy failed with exit $rc"
    }
    if (-not (Test-Path (Join-Path $AsciiWorkDir "pom.xml"))) {
        throw "ASCII work dir missing pom.xml after sync."
    }
    return $AsciiWorkDir
}

function Import-DotEnv([string] $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $k, $v = $_ -split '=', 2
        Set-Item -Path "Env:$k" -Value $v.Trim()
    }
    $env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
}

function Wait-ComposeHealthy([int] $TimeoutSec = 180) {
    Write-Step "Waiting for Postgres and Kafka healthy"
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        $raw = docker compose --env-file .env ps 2>&1 | Out-String
        $postgresOk = $raw -match "(?i)postgres[^\r\n]*healthy"
        $kafkaOk = $raw -match "(?i)kafka[^\r\n]*healthy"
        if ($postgresOk -and $kafkaOk) {
            Write-Host "  Infra healthy" -ForegroundColor Green
            return
        }
        if ((Get-Date) -ge $deadline) {
            Write-Host $raw
            throw "Timed out waiting for healthy postgres/kafka."
        }
        Start-Sleep -Seconds 5
    } while ($true)
}

function Start-AppProcess([string] $Module, [int] $Port, [string] $LogFile) {
    $work = (Get-Location).Path
    $envFile = Join-Path $work ".env"
    $launcher = Join-Path $work ("target\start-" + $Module + ".ps1")
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine('$ErrorActionPreference = "Continue"')
    [void]$sb.AppendLine(('Set-Location -LiteralPath ''' + $work.Replace('''', '''''') + ''''))
    [void]$sb.AppendLine(('Get-Content -LiteralPath ''' + $envFile.Replace('''', '''''') + ''' | ForEach-Object {'))
    [void]$sb.AppendLine('  if ($_ -match "^\s*#" -or $_ -notmatch "=") { return }')
    [void]$sb.AppendLine('  $pair = $_ -split "=", 2')
    [void]$sb.AppendLine('  $k = $pair[0]; $v = $pair[1]')
    [void]$sb.AppendLine('  Set-Item -Path ("Env:" + $k) -Value $v.Trim()')
    [void]$sb.AppendLine('}')
    [void]$sb.AppendLine('$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"')
    [void]$sb.AppendLine(('mvn -pl ' + $Module + ' spring-boot:run *>&1 | Tee-Object -FilePath ''' + $LogFile.Replace('''', '''''') + ''''))
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($launcher, $sb.ToString(), $utf8)
    Write-Host ("  Starting " + $Module + " on :" + $Port + " (log: " + $LogFile + ")")
    Start-Process -FilePath "powershell.exe" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $launcher) `
        -WorkingDirectory $work `
        -WindowStyle Hidden
}

function Wait-HttpOk([string] $Url, [string] $Label, [int] $TimeoutSec = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $res = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($res.StatusCode -ge 200 -and $res.StatusCode -lt 500) {
                Write-Host "  $Label OK ($Url)" -ForegroundColor Green
                return
            }
        } catch { }
        Start-Sleep -Seconds 3
    }
    throw "$Label not reachable at $Url within ${TimeoutSec}s. Check logs under target/bootstrap-*.log"
}

# --- main ---
Clear-StaleDockerEnv
Ensure-Tools
Wait-DockerDaemon

$workRoot = Resolve-WorkRoot
Set-Location $workRoot
Write-Step "Working directory: $workRoot"

Write-Step "Ensuring .env"
if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "  Created .env from .env.example" -ForegroundColor DarkYellow
} else {
    Write-Host "  Found .env" -ForegroundColor Green
}
Import-DotEnv (Join-Path $workRoot ".env")

if (-not $SkipCompose) {
    Write-Step "Starting Compose infra"
    docker compose --env-file .env up -d
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed."
    }
    Wait-ComposeHealthy
} else {
    Write-Host "  Skipping compose (-SkipCompose)"
}

Write-Step $(if ($FullVerify) { "mvn clean verify (JaCoCo gate)" } else { "mvn clean install -DskipTests" })
if ($FullVerify) {
    mvn clean verify
} else {
    mvn clean install -DskipTests
}
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed."
}
Write-Host "  Build OK" -ForegroundColor Green

if (-not $SkipApps) {
    Write-Step "Starting Spring apps (background)"
    New-Item -ItemType Directory -Force -Path "target" | Out-Null
    foreach ($port in @(8080, 8081)) {
        $listening = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($listening) {
            Write-Host "  Port $port already in use - leaving existing process" -ForegroundColor DarkYellow
        }
    }
    $ingestListening = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
    $analyticsListening = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    if (-not $ingestListening) {
        Start-AppProcess "ingest-api" 8081 (Join-Path $workRoot "target\bootstrap-ingest.log")
    }
    if (-not $analyticsListening) {
        Start-AppProcess "analytics-service" 8080 (Join-Path $workRoot "target\bootstrap-analytics.log")
    }

    Wait-HttpOk "http://localhost:8081/actuator/health" "ingest-api"
    Wait-HttpOk "http://localhost:8080/actuator/health" "analytics-service"

    Write-Step "Smoke test (ingest -> Kafka -> Postgres)"
    $eid = "evt-bootstrap-$(Get-Date -Format 'yyyyMMddHHmmss')"
    $body = @{
        eventId      = $eid
        timestamp    = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        configId     = 14227
        policyId     = "pol_web1"
        clientIp     = "203.0.113.99"
        hostname     = "www.example.com"
        path         = "/api/v1/login"
        method       = "POST"
        statusCode   = 403
        userAgent    = "WSA-Bootstrap/1.0"
        rule         = @{
            id       = "950001"
            name     = "SQL_INJECTION"
            message  = "SQL Injection Attack Detected"
            severity = "CRITICAL"
            category = "INJECTION"
        }
        action       = "DENY"
        geoLocation  = @{ country = "CN"; city = "Beijing" }
        requestSize  = 1024
        responseSize = 256
    } | ConvertTo-Json -Depth 6

    $ing = Invoke-WebRequest -Method Post -Uri "http://localhost:8081/v1/events/ingest" `
        -ContentType "application/json" -Body $body -UseBasicParsing
    if ($ing.StatusCode -ne 201) {
        throw "Ingest expected 201, got $($ing.StatusCode)"
    }
    Write-Host "  Ingest 201 ($eid)" -ForegroundColor Green

    $ok = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        try {
            $sum = Invoke-RestMethod "http://localhost:8080/v1/stats/summary?configId=14227"
            if ([int64]$sum.totalEvents -ge 1) {
                Write-Host "  Summary totalEvents=$($sum.totalEvents)" -ForegroundColor Green
                $ok = $true
                break
            }
        } catch { }
    }
    if (-not $ok) {
        throw "Analytics did not show events in summary within ~60s. Check Kafka / analytics log."
    }
}

Write-Host ""
Write-Host "OK - WSA is running locally." -ForegroundColor Green
Write-Host "  Hub:       http://localhost:8080/"
Write-Host "  Ingest:    http://localhost:8081/swagger-ui/index.html"
Write-Host "  Analytics: http://localhost:8080/swagger-ui/index.html"
Write-Host "  Kafka UI:  http://localhost:8083/"
Write-Host "  Adminer:   http://localhost:8082/"
Write-Host "  Work dir:  $workRoot"
Write-Host "  App logs:  $workRoot\target\bootstrap-ingest.log"
Write-Host "             $workRoot\target\bootstrap-analytics.log"
Write-Host ""
Write-Host "Demo data (optional): .\scripts\seed-demo-data.ps1"
Write-Host "Stop apps: stop java/mvn on ports 8080/8081, then: docker compose --env-file .env down"
