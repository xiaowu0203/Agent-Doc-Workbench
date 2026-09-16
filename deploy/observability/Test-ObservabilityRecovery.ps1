param(
    [ValidateSet("Collector", "Jaeger", "OpenSearch", "All")]
    [string]$Scenario = "All",
    [string]$ComposeFile = "$PSScriptRoot/docker-compose.yml"
)

$ErrorActionPreference = "Stop"
$resolvedComposeFile = (Resolve-Path -LiteralPath $ComposeFile).Path

function Invoke-Compose {
    param([string[]]$Arguments)
    & docker compose -f $resolvedComposeFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Arguments -join ' ')"
    }
}

function Wait-HttpHealthy {
    param(
        [string]$Name,
        [string]$Endpoint,
        [int]$MaxAttempts = 30
    )
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            Invoke-WebRequest -Uri $Endpoint -UseBasicParsing -TimeoutSec 2 | Out-Null
            return
        }
        catch {
            if ($attempt -eq $MaxAttempts) {
                throw "$Name did not recover at $Endpoint."
            }
            Start-Sleep -Seconds 2
        }
    }
}

function Assert-HttpUnavailable {
    param([string]$Name, [string]$Endpoint)
    try {
        Invoke-WebRequest -Uri $Endpoint -UseBasicParsing -TimeoutSec 2 | Out-Null
    }
    catch {
        return
    }
    throw "$Name remained reachable after it was stopped."
}

function Test-CollectorRecovery {
    try {
        Invoke-Compose @("stop", "otel-collector")
        Assert-HttpUnavailable "Collector" "http://127.0.0.1:13133/"
    }
    finally {
        Invoke-Compose @("start", "otel-collector")
    }
    Wait-HttpHealthy "Collector" "http://127.0.0.1:13133/"
    Write-Host "Collector stop/start recovery passed."
}

function Test-JaegerRecovery {
    try {
        Invoke-Compose @("stop", "jaeger")
        Assert-HttpUnavailable "Jaeger" "http://127.0.0.1:13134/status"
        Wait-HttpHealthy "Collector" "http://127.0.0.1:13133/"
    }
    finally {
        Invoke-Compose @("start", "jaeger")
    }
    Wait-HttpHealthy "Jaeger" "http://127.0.0.1:13134/status"
    Write-Host "Jaeger stop/start recovery passed; Collector stayed healthy."
}

function Test-OpenSearchRecovery {
    try {
        Invoke-Compose @("stop", "opensearch")
        Assert-HttpUnavailable "OpenSearch" "http://127.0.0.1:9200/_cluster/health"
        Wait-HttpHealthy "Collector" "http://127.0.0.1:13133/"
    }
    finally {
        Invoke-Compose @("start", "opensearch")
    }
    Wait-HttpHealthy "OpenSearch" "http://127.0.0.1:9200/_cluster/health" 45
    Write-Host "OpenSearch stop/start recovery passed; Collector stayed healthy."
}

if ($Scenario -in @("Collector", "All")) { Test-CollectorRecovery }
if ($Scenario -in @("Jaeger", "All")) { Test-JaegerRecovery }
if ($Scenario -in @("OpenSearch", "All")) { Test-OpenSearchRecovery }

& powershell -ExecutionPolicy Bypass -File "$PSScriptRoot/Test-Observability.ps1"
if ($LASTEXITCODE -ne 0) { throw "Observability health check failed after recovery." }
& powershell -ExecutionPolicy Bypass -File "$PSScriptRoot/Test-TracePipeline.ps1"
if ($LASTEXITCODE -ne 0) { throw "Trace pipeline failed after recovery." }

Write-Host "Observability recovery passed for scenario: $Scenario."
