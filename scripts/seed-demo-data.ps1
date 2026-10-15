<#
.SYNOPSIS
  Fill Kafka + Postgres with the current Mini WSA demo dataset.

.DESCRIPTION
  Prerequisites: Docker Compose infra up, ingest-api (:8081) and analytics-service (:8080) running.

  1. Posts synthetic events via the generator (ingest → Kafka → analytics → Postgres)
  2. Waits until events appear in Postgres
  3. Creates every rule in demo/alert-rules.json (skips duplicates)
  4. Calls POST /demo/alerts/fire so assignment/smoke rules show FIRING

.EXAMPLE
  .\scripts\seed-demo-data.ps1
#>
param(
    [string] $IngestUrl = "http://localhost:8081",
    [string] $AnalyticsUrl = "http://localhost:8080",
    [int] $EventCount = 10000,
    [int] $Rate = 100,
    [long] $ConfigId = 14227,
    [int] $BurstSize = 10,
    # Default wait scales with volume (consume lag); override with -WaitSeconds if needed.
    [int] $WaitSeconds = 0,
    [switch] $SkipGenerator,
    [switch] $SkipAlerts,
    [switch] $SkipFire
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $repoRoot "pom.xml"))) {
    throw "Could not find Mini WSA repo root (parent pom.xml missing)."
}
Set-Location $repoRoot

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Wait-HttpOk {
    param(
        [string] $Url,
        [string] $Label,
        [int] $TimeoutSec = 60
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $res = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($res.StatusCode -ge 200 -and $res.StatusCode -lt 500) {
                Write-Host "  $Label is up ($Url)" -ForegroundColor Green
                return
            }
        } catch {
            # keep polling
        }
        Start-Sleep -Seconds 2
    }
    throw "$Label not reachable at $Url within ${TimeoutSec}s. Start the service first."
}

function Get-TotalEvents {
    $summary = Invoke-RestMethod -Uri "$AnalyticsUrl/v1/stats/summary?configId=$ConfigId" -TimeoutSec 10
    return [int64]$summary.totalEvents
}

Write-Step "Checking services"
Wait-HttpOk -Url "$IngestUrl/swagger-ui/index.html" -Label "ingest-api"
Wait-HttpOk -Url "$AnalyticsUrl/v1/stats/summary" -Label "analytics-service"

$before = 0
try {
    $before = Get-TotalEvents
} catch {
    $before = 0
}
Write-Host "  Events in Postgres for config $ConfigId before seed: $before"

if ($WaitSeconds -le 0) {
    # Ingest time ≈ count/rate, plus headroom for Kafka → Postgres consume.
    $WaitSeconds = [Math]::Max(120, [int](($EventCount / [Math]::Max(1, $Rate)) * 3) + 60)
}

if (-not $SkipGenerator) {
    Write-Step "Generating $EventCount mixed events at ~$Rate/s (ingest → Kafka)"
    Write-Host "  This can take several minutes at 10k+ volume." -ForegroundColor DarkGray
    & (Join-Path $repoRoot "generator\generate.ps1") `
        -BaseUrl $IngestUrl `
        -Count $EventCount `
        -Rate $Rate `
        -Wave mixed `
        -ConfigId $ConfigId `
        -BurstSize $BurstSize
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    Write-Step "Waiting for analytics to persist events (up to ${WaitSeconds}s)"
    $target = $before + [Math]::Max(1, [int]($EventCount * 0.8))
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    $current = $before
    while ((Get-Date) -lt $deadline) {
        try {
            $current = Get-TotalEvents
            Write-Host "  Postgres events: $current (need >= $target)"
            if ($current -ge $target) {
                break
            }
        } catch {
            Write-Host "  Waiting for stats API…" -ForegroundColor DarkGray
        }
        Start-Sleep -Seconds 2
    }
    if ($current -lt $target) {
        throw "Timed out waiting for events in Postgres (have $current, want >= $target). Check Kafka consumer / analytics logs."
    }
    Write-Host "  Kafka consume + DB write OK" -ForegroundColor Green
} else {
    Write-Host "  Skipping generator (-SkipGenerator)"
}

if (-not $SkipAlerts) {
    Write-Step "Creating example alert rules from demo/alert-rules.json"
    $rulesPath = Join-Path $repoRoot "demo\alert-rules.json"
    if (-not (Test-Path $rulesPath)) {
        throw "Missing $rulesPath"
    }
    $demos = Get-Content -Raw -Path $rulesPath | ConvertFrom-Json
    $created = 0
    $skipped = 0
    $failed = 0
    foreach ($demo in $demos) {
        $body = $demo.rule | ConvertTo-Json -Compress -Depth 6
        try {
            $null = Invoke-RestMethod `
                -Method Post `
                -Uri "$AnalyticsUrl/v1/alerts/define" `
                -ContentType "application/json" `
                -Body $body `
                -TimeoutSec 15
            $created++
            Write-Host "  + $($demo.label)" -ForegroundColor Green
        } catch {
            $status = $null
            if ($_.Exception.Response) {
                $status = [int]$_.Exception.Response.StatusCode
            }
            # Duplicate rule → treat as already seeded
            if ($status -eq 400 -or $status -eq 409) {
                $skipped++
                Write-Host "  = $($demo.label) (already exists)" -ForegroundColor DarkYellow
            } else {
                $failed++
                Write-Host "  ! $($demo.label): $($_.Exception.Message)" -ForegroundColor Red
            }
        }
    }
    Write-Host "  Alerts: created=$created skipped=$skipped failed=$failed"
    if ($failed -gt 0) {
        throw "Failed to create $failed alert rule(s)."
    }
} else {
    Write-Host "  Skipping alerts (-SkipAlerts)"
}

if (-not $SkipFire) {
    Write-Step "Priming demo alert windows (POST /demo/alerts/fire)"
    $fire = Invoke-RestMethod -Method Post -Uri "$AnalyticsUrl/demo/alerts/fire" -TimeoutSec 30
    Write-Host ($fire | ConvertTo-Json -Depth 6)

    Write-Step "Evaluating alerts"
    $evals = Invoke-RestMethod -Uri "$AnalyticsUrl/v1/alerts/evaluate" -TimeoutSec 30
    $firing = @($evals | Where-Object { $_.status -eq "FIRING" }).Count
    $ok = @($evals | Where-Object { $_.status -ne "FIRING" }).Count
    Write-Host "  FIRING=$firing  ok/other=$ok  total=$($evals.Count)" -ForegroundColor Green
} else {
    Write-Host "  Skipping demo fire (-SkipFire)"
}

Write-Host ""
Write-Host "Seed complete." -ForegroundColor Green
Write-Host "  Hub:       http://localhost:8080/"
Write-Host "  Samples:   $AnalyticsUrl/v1/events/samples?configId=$ConfigId&limit=20"
Write-Host "  Alerts:    $AnalyticsUrl/v1/alerts/evaluate"
Write-Host "  Kafka UI:  http://localhost:8083/"
Write-Host "  Adminer:   http://localhost:8080/adminer-go.html"
