param(
    [string] $BaseUrl = "",
    [int] $Count = 20,
    [int] $Rate = 10,
    [ValidateSet("sql", "xss", "mixed")]
    [string] $Wave = "sql",
    [long] $ConfigId = 14227,
    [int] $BurstSize = 50
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $repoRoot "pom.xml"))) {
    throw "Run this script from the WSA repo (could not find parent pom.xml)."
}
Set-Location $repoRoot

if (Test-Path -LiteralPath (Join-Path $repoRoot ".env")) {
    Get-Content -LiteralPath (Join-Path $repoRoot ".env") | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $k, $v = $_ -split '=', 2
        Set-Item -Path "Env:$k" -Value $v.Trim()
    }
}
if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $hostName = if ($env:WSA_HOST) { $env:WSA_HOST } else { "localhost" }
    $ingestPort = if ($env:WSA_INGEST_PORT) { $env:WSA_INGEST_PORT } else { "8081" }
    $BaseUrl = "http://${hostName}:${ingestPort}"
}

$argsLine = "--base-url $BaseUrl --count $Count --rate $Rate --wave $Wave --config-id $ConfigId --burst-size $BurstSize"
Write-Host "Building generator modules (quiet)..." -ForegroundColor DarkGray
mvn -q -pl generator -am -DskipTests install
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
Write-Host "Starting generator..." -ForegroundColor Cyan
mvn -q -pl generator -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=com.wsa.generator.GeneratorMain" "-Dexec.args=$argsLine"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
