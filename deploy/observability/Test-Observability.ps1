param(
    [string]$CollectorHealthEndpoint = "http://127.0.0.1:13133/",
    [string]$JaegerHealthEndpoint = "http://127.0.0.1:13134/status",
    [string]$JaegerUiEndpoint = "http://127.0.0.1:16686/",
    [string]$OpenSearchEndpoint = "http://127.0.0.1:9200"
)

$ErrorActionPreference = "Stop"

Invoke-RestMethod -Uri $CollectorHealthEndpoint | Out-Null
Invoke-RestMethod -Uri $JaegerHealthEndpoint | Out-Null
Invoke-WebRequest -Uri $JaegerUiEndpoint -UseBasicParsing | Out-Null
$cluster = Invoke-RestMethod -Uri "$OpenSearchEndpoint/_cluster/health"

if ($cluster.status -notin @("green", "yellow")) {
    throw "OpenSearch cluster status is '$($cluster.status)'."
}

Write-Host "Collector, Jaeger and OpenSearch are healthy. OpenSearch status: $($cluster.status)."
