<#
.SYNOPSIS
  Bootstrap WSA on Windows: install missing tools, start Docker infra, build, run analytics + ingest + client, smoke-test.

.DESCRIPTION
  Intended for a fresh or broken local machine. Re-runnable (skips tools already present).

  Steps:
    1. Clear stale docker-machine env
    2. Optionally install JDK 17 / Maven / Docker Desktop via winget
    3. Ensure Docker daemon is up
    4. Use ASCII work dir if the checkout path is non-ASCII (OpenAPI/Maven)
    5. Create .env, vault-seed, compose up with Vault-backed Postgres password
    6. mvn clean install -DskipTests (or -FullVerify)
    7. Start analytics / ingest / client (WSA_*_PORT from .env) with AppRole env
    8. Smoke-check health + one ingest until eventId appears in analytics samples

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
    Write-Step "Clearing stale DOCKER_* env (old docker-machine / Toolbox)"
    foreach ($n in @("DOCKER_HOST", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH", "DOCKER_MACHINE_NAME", "DOCKER_TOOLBOX_INSTALL_PATH")) {
        Remove-Item "Env:$n" -ErrorAction SilentlyContinue
        [Environment]::SetEnvironmentVariable($n, $null, "User")
    }
    # Testcontainers prefers docker-machine.exe on PATH over Docker Desktop.
    $env:Path = (($env:Path -split ';' | Where-Object { $_ -and $_ -notmatch 'Docker Toolbox' }) -join ';')
    if (Test-Path "\\.\pipe\dockerDesktopLinuxEngine") {
        $env:DOCKER_HOST = "npipe:////./pipe/dockerDesktopLinuxEngine"
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

function Get-EnvFileKeys([string] $EnvFile) {
    $keys = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    if (-not (Test-Path -LiteralPath $EnvFile)) { return $keys }
    Get-Content -LiteralPath $EnvFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $k = ($_ -split '=', 2)[0].Trim()
        if ($k) { [void]$keys.Add($k) }
    }
    return $keys
}

function Merge-MissingEnvKeys([string] $ExampleFile, [string] $TargetFile) {
    if (-not (Test-Path -LiteralPath $ExampleFile)) { return }
    if (-not (Test-Path -LiteralPath $TargetFile)) {
        Copy-Item -LiteralPath $ExampleFile -Destination $TargetFile
        Write-Host "  Created .env from .env.example" -ForegroundColor DarkYellow
        return
    }
    $existing = Get-EnvFileKeys $TargetFile
    $appended = 0
    Get-Content -LiteralPath $ExampleFile | ForEach-Object {
        $line = $_
        if ($line -match '^\s*#' -or $line -notmatch '=') { return }
        $k = ($line -split '=', 2)[0].Trim()
        if ($k -and -not $existing.Contains($k)) {
            if ($appended -eq 0) {
                Add-Content -LiteralPath $TargetFile -Value ""
                Add-Content -LiteralPath $TargetFile -Value "# --- added from .env.example (missing keys) ---"
            }
            Add-Content -LiteralPath $TargetFile -Value $line
            [void]$existing.Add($k)
            $appended++
        }
    }
    if ($appended -gt 0) {
        Write-Host "  Merged $appended missing key(s) from .env.example into .env" -ForegroundColor DarkYellow
    }
}

function Import-DotEnv([string] $EnvFile) {
    if (-not (Test-Path -LiteralPath $EnvFile)) { return }
    Get-Content -LiteralPath $EnvFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $k, $v = $_ -split '=', 2
        Set-Item -Path "Env:$k" -Value $v.Trim()
    }
}

function Get-WsaEnvOrDefault([string] $Name, [string] $Default) {
    $val = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($val)) { return $Default }
    return $val.Trim()
}

function Apply-WsaDerivedEnv {
    $hostName = Get-WsaEnvOrDefault "WSA_HOST" "localhost"
    $ingestPort = Get-WsaEnvOrDefault "WSA_INGEST_PORT" "8081"
    $analyticsPort = Get-WsaEnvOrDefault "WSA_ANALYTICS_PORT" "8080"
    $clientPort = Get-WsaEnvOrDefault "WSA_CLIENT_PORT" "8084"
    $adminerPort = Get-WsaEnvOrDefault "WSA_ADMINER_PORT" "8082"
    $kafkaUiPort = Get-WsaEnvOrDefault "WSA_KAFKA_UI_PORT" "8083"
    $postgresPort = Get-WsaEnvOrDefault "WSA_POSTGRES_PORT" "5432"
    $kafkaPort = Get-WsaEnvOrDefault "WSA_KAFKA_PORT" "9092"
    $vaultPort = Get-WsaEnvOrDefault "WSA_VAULT_PORT" "8200"
    $dbName = Get-WsaEnvOrDefault "POSTGRES_DB" "wsa"

    $env:WSA_HOST = $hostName
    $env:WSA_INGEST_PORT = $ingestPort
    $env:WSA_ANALYTICS_PORT = $analyticsPort
    $env:WSA_CLIENT_PORT = $clientPort
    $env:WSA_ADMINER_PORT = $adminerPort
    $env:WSA_KAFKA_UI_PORT = $kafkaUiPort
    $env:WSA_POSTGRES_PORT = $postgresPort
    $env:WSA_KAFKA_PORT = $kafkaPort
    $env:WSA_VAULT_PORT = $vaultPort

    $env:WSA_ANALYTICS_BASE_URL = "http://${hostName}:${analyticsPort}"
    $env:ADMINER_URL = "http://${hostName}:${adminerPort}/"
    $env:VAULT_URI = "http://${hostName}:${vaultPort}"
    $env:KAFKA_BOOTSTRAP_SERVERS = "${hostName}:${kafkaPort}"
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://${hostName}:${postgresPort}/${dbName}"

    if ([string]::IsNullOrWhiteSpace($env:KAFKA_ADVERTISED_LISTENERS)) {
        $env:KAFKA_ADVERTISED_LISTENERS = "PLAINTEXT://${hostName}:${kafkaPort},INTERNAL://kafka:29092"
    }
}

function Import-VaultRuntimeEnv([string] $WorkRoot) {
    $approle = Join-Path $WorkRoot ".vault\approle.env"
    $passFile = Join-Path $WorkRoot ".vault\postgres_password"
    Import-DotEnv $approle
    if (Test-Path -LiteralPath $passFile) {
        $pg = (Get-Content -LiteralPath $passFile -Raw).Trim()
        $env:POSTGRES_PASSWORD = $pg
        $env:ADMINER_PASSWORD = $pg
    }
    Apply-WsaDerivedEnv
}

function Wait-ComposeHealthy([int] $TimeoutSec = 180) {
    Write-Step "Waiting for Vault, Postgres, and Kafka healthy"
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        $raw = docker compose --env-file .env ps 2>&1 | Out-String
        $vaultOk = $raw -match "(?i)vault[^\r\n]*healthy"
        $postgresOk = $raw -match "(?i)postgres[^\r\n]*healthy"
        $kafkaOk = $raw -match "(?i)kafka[^\r\n]*healthy"
        if ($vaultOk -and $postgresOk -and $kafkaOk) {
            Write-Host "  Infra healthy" -ForegroundColor Green
            return
        }
        if ((Get-Date) -ge $deadline) {
            Write-Host $raw
            throw "Timed out waiting for healthy vault/postgres/kafka."
        }
        Start-Sleep -Seconds 5
    } while ($true)
}

function Start-AppProcess([string] $Module, [int] $Port, [string] $LogFile) {
    $work = (Get-Location).Path
    $envFile = Join-Path $work ".env"
    $approleFile = Join-Path $work ".vault\approle.env"
    $launcher = Join-Path $work ("target\start-" + $Module + ".ps1")
    $q = { param($s) "'" + $s.Replace("'", "''") + "'" }
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine('$ErrorActionPreference = "Continue"')
    [void]$sb.AppendLine(('Set-Location -LiteralPath ' + (& $q $work)))
    [void]$sb.AppendLine('function Import-EnvFile([string] $Path) {')
    [void]$sb.AppendLine('  if (-not (Test-Path -LiteralPath $Path)) { return }')
    [void]$sb.AppendLine('  Get-Content -LiteralPath $Path | ForEach-Object {')
    [void]$sb.AppendLine('    if ($_ -match "^\s*#" -or $_ -notmatch "=") { return }')
    [void]$sb.AppendLine('    $pair = $_ -split "=", 2')
    [void]$sb.AppendLine('    Set-Item -Path ("Env:" + $pair[0]) -Value $pair[1].Trim()')
    [void]$sb.AppendLine('  }')
    [void]$sb.AppendLine('}')
    [void]$sb.AppendLine(('Import-EnvFile ' + (& $q $envFile)))
    [void]$sb.AppendLine(('Import-EnvFile ' + (& $q $approleFile)))
    [void]$sb.AppendLine(('$env:SERVER_PORT = ' + (& $q ([string]$Port))))
    [void]$sb.AppendLine(('$env:KAFKA_BOOTSTRAP_SERVERS = ' + (& $q $env:KAFKA_BOOTSTRAP_SERVERS)))
    [void]$sb.AppendLine(('$env:SPRING_DATASOURCE_URL = ' + (& $q $env:SPRING_DATASOURCE_URL)))
    [void]$sb.AppendLine(('$env:VAULT_URI = ' + (& $q $env:VAULT_URI)))
    [void]$sb.AppendLine(('$env:ADMINER_URL = ' + (& $q $env:ADMINER_URL)))
    [void]$sb.AppendLine(('$env:WSA_ANALYTICS_BASE_URL = ' + (& $q $env:WSA_ANALYTICS_BASE_URL)))
    [void]$sb.AppendLine(('mvn -pl ' + $Module + ' spring-boot:run *>&1 | Tee-Object -FilePath ' + (& $q $LogFile)))
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
$envExample = Join-Path $workRoot ".env.example"
$envFile = Join-Path $workRoot ".env"
Merge-MissingEnvKeys $envExample $envFile
if (Test-Path -LiteralPath $envFile) {
    Write-Host "  Found .env" -ForegroundColor Green
}
Import-DotEnv $envFile
Apply-WsaDerivedEnv

$hostName = $env:WSA_HOST
$ingestPort = [int]$env:WSA_INGEST_PORT
$analyticsPort = [int]$env:WSA_ANALYTICS_PORT
$clientPort = [int]$env:WSA_CLIENT_PORT
$adminerPort = [int]$env:WSA_ADMINER_PORT
$kafkaUiPort = [int]$env:WSA_KAFKA_UI_PORT
$ingestBase = "http://${hostName}:${ingestPort}"
$analyticsBase = "http://${hostName}:${analyticsPort}"
$clientBase = "http://${hostName}:${clientPort}"

if (-not $SkipCompose) {
    Write-Step "Seeding HashiCorp Vault (KV + AppRole)"
    & (Join-Path $PSScriptRoot "vault-seed.ps1") -RepoRoot $workRoot
    if ($LASTEXITCODE -ne 0) { throw "vault-seed.ps1 failed." }
    Import-VaultRuntimeEnv $workRoot

    Write-Step "Starting Compose infra (Postgres password from Vault)"
    docker compose --env-file .env up -d
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed."
    }
    Wait-ComposeHealthy
} else {
    Write-Host "  Skipping compose (-SkipCompose)"
    Import-VaultRuntimeEnv $workRoot
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
    foreach ($port in @($analyticsPort, $ingestPort, $clientPort)) {
        $listening = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($listening) {
            Write-Host "  Port $port already in use - leaving existing process" -ForegroundColor DarkYellow
        }
    }
    $ingestListening = Get-NetTCPConnection -LocalPort $ingestPort -State Listen -ErrorAction SilentlyContinue
    $analyticsListening = Get-NetTCPConnection -LocalPort $analyticsPort -State Listen -ErrorAction SilentlyContinue
    $clientListening = Get-NetTCPConnection -LocalPort $clientPort -State Listen -ErrorAction SilentlyContinue
    # Analytics first on empty DB (Flyway owns shared schema); ingest uses a separate history table.
    if (-not $analyticsListening) {
        Start-AppProcess "analytics-service" $analyticsPort (Join-Path $workRoot "target\bootstrap-analytics.log")
    }
    if (-not $ingestListening) {
        Start-AppProcess "ingest-api" $ingestPort (Join-Path $workRoot "target\bootstrap-ingest.log")
    }
    if (-not $clientListening) {
        Start-AppProcess "client" $clientPort (Join-Path $workRoot "target\bootstrap-client.log")
    }

    Wait-HttpOk "$ingestBase/actuator/health" "ingest-api"
    Wait-HttpOk "$analyticsBase/actuator/health" "analytics-service"
    Wait-HttpOk "$clientBase/actuator/health" "client"

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

    $ing = Invoke-WebRequest -Method Post -Uri "$ingestBase/v1/events/ingest" `
        -ContentType "application/json" -Body $body -UseBasicParsing
    if ($ing.StatusCode -ne 201) {
        throw "Ingest expected 201, got $($ing.StatusCode)"
    }
    Write-Host "  Ingest 201 ($eid)" -ForegroundColor Green

    $ok = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        try {
            $samples = Invoke-RestMethod "$analyticsBase/v1/events/samples?configId=14227&limit=100&offset=0"
            $found = @($samples.items | Where-Object { $_.eventId -eq $eid })
            if ($found.Count -ge 1) {
                $sum = Invoke-RestMethod "$analyticsBase/v1/stats/summary?configId=14227"
                Write-Host "  Sample found for $eid; summary totalEvents=$($sum.totalEvents)" -ForegroundColor Green
                $ok = $true
                break
            }
        } catch { }
    }
    if (-not $ok) {
        throw "Analytics did not show eventId=$eid in samples within ~60s. Check Kafka / analytics log."
    }
}

Write-Host ""
Write-Host "OK - WSA is running locally." -ForegroundColor Green
Write-Host "  Hub:       ${clientBase}/"
Write-Host "  Stats UI:  ${clientBase}/stats"
Write-Host "  Ingest:    ${ingestBase}/swagger-ui/index.html"
Write-Host "  Analytics: ${analyticsBase}/swagger-ui/index.html"
Write-Host "  Kafka UI:  http://${hostName}:${kafkaUiPort}/"
Write-Host "  Adminer:   http://${hostName}:${adminerPort}/"
Write-Host "  Work dir:  $workRoot"
Write-Host "  App logs:  $workRoot\target\bootstrap-ingest.log"
Write-Host "             $workRoot\target\bootstrap-analytics.log"
Write-Host "             $workRoot\target\bootstrap-client.log"
Write-Host ""
Write-Host "Demo data (optional): .\scripts\seed-demo-data.ps1"
Write-Host "Stop apps: stop java/mvn on ports ${analyticsPort}/${ingestPort}/${clientPort}, then: docker compose --env-file .env down"
