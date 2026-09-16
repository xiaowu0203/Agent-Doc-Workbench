param(
    [ValidateRange(1, 3650)]
    [int]$RetentionDays = 14,
    [string]$OpenSearchEndpoint = "http://127.0.0.1:9200"
)

$ErrorActionPreference = "Stop"
$policyName = "adwb-jaeger-trace-retention"
$policyBody = @{
    policy = @{
        description = "Delete Agent Doc Workbench Jaeger indices after $RetentionDays days"
        default_state = "hot"
        states = @(
            @{
                name = "hot"
                actions = @()
                transitions = @(
                    @{
                        state_name = "delete"
                        conditions = @{ min_index_age = "${RetentionDays}d" }
                    }
                )
            },
            @{
                name = "delete"
                actions = @(@{ delete = @{} })
                transitions = @()
            }
        )
        ism_template = @(
            @{
                index_patterns = @("jaeger-main-*")
                priority = 100
            }
        )
    }
} | ConvertTo-Json -Depth 10

$uri = "$OpenSearchEndpoint/_plugins/_ism/policies/$policyName"
try {
    Invoke-RestMethod -Method Put -Uri $uri -ContentType "application/json" -Body $policyBody | Out-Null
}
catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 409) {
        throw
    }

    $current = Invoke-RestMethod -Method Get -Uri $uri
    $seqNo = $current._seq_no
    $primaryTerm = $current._primary_term
    Invoke-RestMethod -Method Put -Uri "$uri`?if_seq_no=$seqNo&if_primary_term=$primaryTerm" `
        -ContentType "application/json" -Body $policyBody | Out-Null
}

Write-Host "Trace retention policy '$policyName' is set to $RetentionDays days."
