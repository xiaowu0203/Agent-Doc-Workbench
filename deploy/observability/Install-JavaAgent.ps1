param(
    [string]$Destination
)

$ErrorActionPreference = "Stop"
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $scriptDirectory ".tools\opentelemetry-javaagent.jar"
}
$version = "2.31.1"
$expectedSha256 = "bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba"
$downloadUri = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v$version/opentelemetry-javaagent.jar"
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
$destinationDirectory = Split-Path -Parent $destinationPath
$temporaryPath = "$destinationPath.download"

New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
try {
    Invoke-WebRequest -Uri $downloadUri -OutFile $temporaryPath
    $actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $temporaryPath).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $expectedSha256) {
        throw "Java Agent SHA-256 校验失败：期望 $expectedSha256，实际 $actualSha256"
    }
    Move-Item -Force -LiteralPath $temporaryPath -Destination $destinationPath
}
finally {
    if (Test-Path -LiteralPath $temporaryPath) {
        Remove-Item -Force -LiteralPath $temporaryPath
    }
}

Write-Host "OpenTelemetry Java Agent $version 已安装并校验：$destinationPath"
