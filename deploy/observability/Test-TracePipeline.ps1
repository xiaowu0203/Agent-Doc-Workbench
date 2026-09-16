param(
    [string]$CollectorHttpEndpoint = "http://127.0.0.1:4318",
    [string]$JaegerEndpoint = "http://127.0.0.1:16686",
    [int]$TraceQueryAttempts = 30
)

$ErrorActionPreference = "Stop"
$traceId = [guid]::NewGuid().ToString("N")
$spanId = $traceId.Substring(0, 16)
$startedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000000
$finishedAt = $startedAt + 1000000
$forbiddenMarkers = [ordered]@{
    Authorization = "ADWB_FORBIDDEN_AUTHORIZATION_MARKER"
    Cookie = "ADWB_FORBIDDEN_COOKIE_MARKER"
    QueryToken = "ADWB_FORBIDDEN_QUERY_TOKEN_MARKER"
    SqlParameter = "ADWB_FORBIDDEN_SQL_PARAMETER_MARKER"
    Prompt = "ADWB_FORBIDDEN_PROMPT_MARKER"
    DocumentBody = "ADWB_FORBIDDEN_DOCUMENT_BODY_MARKER"
    ToolArguments = "ADWB_FORBIDDEN_TOOL_ARGUMENTS_MARKER"
    ModelResponse = "ADWB_FORBIDDEN_MODEL_RESPONSE_MARKER"
    ExceptionMessage = "ADWB_FORBIDDEN_EXCEPTION_MESSAGE_MARKER"
    ExceptionStack = "ADWB_FORBIDDEN_EXCEPTION_STACK_MARKER"
}

$payload = @{
    resourceSpans = @(@{
        resource = @{ attributes = @(
            @{ key = "service.name"; value = @{ stringValue = "adwb-telemetry-smoke" } },
            @{ key = "deployment.environment.name"; value = @{ stringValue = "local" } }
        ) }
        scopeSpans = @(@{
            scope = @{ name = "adwb-smoke" }
            spans = @(@{
                traceId = $traceId
                spanId = $spanId
                name = "agentdoc.smoke"
                kind = 1
                startTimeUnixNano = "$startedAt"
                endTimeUnixNano = "$finishedAt"
                attributes = @(
                    @{ key = "run.id"; value = @{ intValue = "9001" } },
                    @{ key = "agentdoc.tool.technical_name"; value = @{ stringValue = "safe-tool" } },
                    @{ key = "http.request.header.authorization"; value = @{ stringValue = "Bearer $($forbiddenMarkers.Authorization)" } },
                    @{ key = "http.request.header.cookie"; value = @{ stringValue = $forbiddenMarkers.Cookie } },
                    @{ key = "url.full"; value = @{ stringValue = "https://example.invalid/task?token=$($forbiddenMarkers.QueryToken)" } },
                    @{ key = "db.query.text"; value = @{ stringValue = "select * from task where secret='$($forbiddenMarkers.SqlParameter)'" } },
                    @{ key = "gen_ai.prompt"; value = @{ stringValue = $forbiddenMarkers.Prompt } },
                    @{ key = "agentdoc.document.body"; value = @{ stringValue = $forbiddenMarkers.DocumentBody } },
                    @{ key = "gen_ai.tool.call.arguments"; value = @{ stringValue = $forbiddenMarkers.ToolArguments } },
                    @{ key = "gen_ai.completion"; value = @{ stringValue = $forbiddenMarkers.ModelResponse } },
                    @{ key = "exception.message"; value = @{ stringValue = $forbiddenMarkers.ExceptionMessage } }
                )
                events = @(@{
                    timeUnixNano = "$finishedAt"
                    name = "exception"
                    attributes = @(
                        @{ key = "exception.type"; value = @{ stringValue = "SyntheticException" } },
                        @{ key = "exception.stacktrace"; value = @{ stringValue = $forbiddenMarkers.ExceptionStack } }
                    )
                })
            })
        })
    })
} | ConvertTo-Json -Depth 12 -Compress

Invoke-RestMethod -Method Post -Uri "$CollectorHttpEndpoint/v1/traces" `
    -ContentType "application/json" -Body $payload | Out-Null

$trace = $null
for ($attempt = 1; $attempt -le $TraceQueryAttempts; $attempt++) {
    Start-Sleep -Seconds 1
    try {
        $trace = Invoke-RestMethod -Uri "$JaegerEndpoint/api/traces/$traceId"
        if ($trace.data.Count -gt 0) {
            break
        }
    }
    catch {
        if ($attempt -eq $TraceQueryAttempts) {
            throw
        }
    }
}

if ($null -eq $trace -or $trace.data.Count -eq 0) {
    throw "Jaeger 未查到合成 Trace：$traceId"
}

$traceJson = $trace | ConvertTo-Json -Depth 20 -Compress
if (-not $traceJson.Contains("run.id") -or -not $traceJson.Contains("safe-tool")) {
    throw "允许列表字段未完整保留：$traceId"
}
$leakedMarkers = @($forbiddenMarkers.Values | Where-Object { $traceJson.Contains($_) })
if ($leakedMarkers.Count -gt 0) {
    throw "禁止内容进入了 Jaeger：$traceId，命中 $($leakedMarkers -join ', ')"
}

Write-Host "Trace pipeline passed. traceId=$traceId; allowed attributes kept; 10 forbidden markers removed."
