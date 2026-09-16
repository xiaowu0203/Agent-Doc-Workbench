param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("gateway-service", "auth-service", "document-service", "task-service", "agent-service")]
    [string]$ServiceName,
    [string]$JavaAgentPath,
    [string]$ConfigurationFile
)

$ErrorActionPreference = "Stop"
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($JavaAgentPath)) {
    $JavaAgentPath = Join-Path $scriptDirectory ".tools\opentelemetry-javaagent.jar"
}
if ([string]::IsNullOrWhiteSpace($ConfigurationFile)) {
    $ConfigurationFile = Join-Path $scriptDirectory "otel-javaagent.properties"
}

$expectedSha256 = "bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba"
$resolvedAgentPath = (Resolve-Path -LiteralPath $JavaAgentPath).Path
$resolvedConfigurationFile = (Resolve-Path -LiteralPath $ConfigurationFile).Path
$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedAgentPath).Hash.ToLowerInvariant()
if ($actualSha256 -ne $expectedSha256) {
    throw "Java Agent SHA-256 validation failed. Expected $expectedSha256, actual $actualSha256"
}

$javaAgentOption = "-javaagent:$resolvedAgentPath"
$configurationOption = "-Dotel.javaagent.configuration-file=$resolvedConfigurationFile"
$serviceNameOption = "-Dotel.service.name=$ServiceName"
$existingOptions = [Environment]::GetEnvironmentVariable("JAVA_TOOL_OPTIONS", "Process")
$requiredOptions = @($javaAgentOption, $configurationOption, $serviceNameOption)
$missingOptions = $requiredOptions | Where-Object {
    [string]::IsNullOrWhiteSpace($existingOptions) -or -not $existingOptions.Contains($_)
}
$env:JAVA_TOOL_OPTIONS = (@($existingOptions) + $missingOptions | Where-Object {
    -not [string]::IsNullOrWhiteSpace($_)
}) -join " "

Write-Host "OpenTelemetry is enabled for $ServiceName in the current PowerShell session."
Write-Host "Default configuration: $resolvedConfigurationFile"
Write-Host "Start the service in this session. Internal propagation uses W3C tracecontext and baggage."
