$ErrorActionPreference = 'Stop'

$taskEnvPath = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $taskEnvPath) {
    throw '.env已经存在，未覆盖；请保留其中的凭据，按需要手动修改。'
}

$taskContent = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot '.env.example'))
$taskSecretNames = @(
    'KEYCLOAK_ADMIN_PASSWORD',
    'POSTGRES_ADMIN_PASSWORD',
    'KEYCLOAK_DB_PASSWORD',
    'KEYCLOAK_POC_WEB_CLIENT_SECRET',
    'KEYCLOAK_POC_TASK_CLIENT_SECRET',
    'KEYCLOAK_POC_USER_PASSWORD'
)
$taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    foreach ($taskSecretName in $taskSecretNames) {
        $taskBytes = New-Object byte[] 32
        $taskRandom.GetBytes($taskBytes)
        $taskValue = [Convert]::ToBase64String($taskBytes)
        $taskPattern = '(?m)^' + [regex]::Escape($taskSecretName) + '=[^\r\n]*'
        $taskContent = [regex]::Replace($taskContent, $taskPattern, "$taskSecretName=$taskValue")
    }
} finally {
    $taskRandom.Dispose()
}

$taskEncoding = New-Object System.Text.UTF8Encoding($false)
$taskOutput = $taskEncoding.GetBytes($taskContent)
$taskStream = [System.IO.File]::Open($taskEnvPath, [System.IO.FileMode]::CreateNew,
    [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
try {
    $taskStream.Write($taskOutput, 0, $taskOutput.Length)
} finally {
    $taskStream.Dispose()
}

Write-Output '已生成 deploy/identity/.env，未输出任何密码；未启动Docker或容器。'
