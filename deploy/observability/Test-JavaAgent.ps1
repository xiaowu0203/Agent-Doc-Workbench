param(
    [string]$JavaHome = "C:\Program Files\Java\jdk-21",
    [string]$JavaAgentPath,
    [string]$ConfigurationFile,
    [string]$CollectorEndpoint = "http://127.0.0.1:4317",
    [string]$JaegerEndpoint = "http://127.0.0.1:16686"
)

$ErrorActionPreference = "Stop"
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($JavaAgentPath)) {
    $JavaAgentPath = Join-Path $scriptDirectory ".tools\opentelemetry-javaagent.jar"
}
if ([string]::IsNullOrWhiteSpace($ConfigurationFile)) {
    $ConfigurationFile = Join-Path $scriptDirectory "otel-javaagent.properties"
}

$javaExecutable = Join-Path $JavaHome "bin\java.exe"
$apiJar = Join-Path $env:USERPROFILE ".m2\repository\io\opentelemetry\opentelemetry-api\1.49.0\opentelemetry-api-1.49.0.jar"
$contextJar = Join-Path $env:USERPROFILE ".m2\repository\io\opentelemetry\opentelemetry-context\1.49.0\opentelemetry-context-1.49.0.jar"
$probeSource = Join-Path $scriptDirectory "probe\TelemetryProbe.java"
foreach ($requiredFile in @($javaExecutable, $JavaAgentPath, $ConfigurationFile, $apiJar, $contextJar, $probeSource)) {
    if (-not (Test-Path -LiteralPath $requiredFile)) {
        throw "Java Agent probe file is missing: $requiredFile"
    }
}

$classPath = "$apiJar;$contextJar"
$traceId = & $javaExecutable `
    "-javaagent:$JavaAgentPath" `
    "-Dotel.javaagent.configuration-file=$ConfigurationFile" `
    "-Dotel.javaagent.enabled=true" `
    "-Dotel.service.name=adwb-javaagent-probe" `
    "-Dotel.exporter.otlp.endpoint=$CollectorEndpoint" `
    "-Dotel.traces.sampler=always_on" `
    -cp $classPath $probeSource |
    Where-Object { $_ -match '^[0-9a-f]{32}$' } | Select-Object -Last 1
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($traceId)) {
    throw "Java Agent probe did not generate a valid Trace ID"
}

$trace = $null
for ($attempt = 1; $attempt -le 10; $attempt++) {
    Start-Sleep -Milliseconds 500
    try {
        $trace = Invoke-RestMethod -Uri "$JaegerEndpoint/api/traces/$traceId"
        if ($trace.data.Count -gt 0) {
            break
        }
    }
    catch {
        if ($attempt -eq 10) {
            throw
        }
    }
}

if ($null -eq $trace -or $trace.data.Count -eq 0) {
    throw "Jaeger did not return the Java Agent probe trace: $traceId"
}
$serviceNames = $trace.data[0].processes.PSObject.Properties | ForEach-Object {
    $_.Value.serviceName
}
if ($serviceNames -notcontains "adwb-javaagent-probe") {
    throw "Java Agent probe service.name was not preserved: $traceId"
}
$traceJson = $trace | ConvertTo-Json -Depth 20 -Compress
if (-not $traceJson.Contains("javaagent-probe") -or $traceJson.Contains("ADWB_FORBIDDEN_JAVA_PROMPT")) {
    throw "Java Agent probe allowlist or sanitization contract failed: $traceId"
}

Write-Host "Java Agent pipeline passed. traceId=$traceId"
