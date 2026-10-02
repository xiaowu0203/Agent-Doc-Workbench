# 离线只读巡检：仅输出计数，不导出报告正文、业务身份或凭证。
[CmdletBinding()]
param(
    [string]$DatabaseHost = 'localhost',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'agent_doc_workbench',
    [string]$DatabaseUser = 'root'
)

$ErrorActionPreference = 'Stop'
if ($env:DB_URL) {
    if ($env:DB_URL -notmatch '^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)') {
        throw 'DB_URL 不是支持的单节点 MySQL JDBC 地址，请显式指定连接参数。'
    }
    $DatabaseHost = $Matches[1]
    if ($Matches[2]) { $DatabasePort = [int]$Matches[2] }
    $DatabaseName = $Matches[3]
}
if ($env:DB_USERNAME) { $DatabaseUser = $env:DB_USERNAME }
$priorPassword = $env:MYSQL_PWD
try {
    # 凭证只经环境提供，不内置密码、不进入命令行或输出。
    if ($env:DB_PASSWORD) { $env:MYSQL_PWD = $env:DB_PASSWORD }
    elseif (-not $priorPassword) { throw '请通过 DB_PASSWORD 或 MYSQL_PWD 环境变量提供只读数据库凭证。' }
    $sql = @'
START TRANSACTION READ ONLY;
SELECT COALESCE(CAST(report_schema_version AS CHAR), 'NULL') AS schema_version,
       COUNT(*) AS report_count,
       SUM(report_schema_version IS NULL) AS schema_missing,
       SUM(report_schema_version IS NOT NULL AND report_schema_version <= 0) AS schema_invalid,
       SUM(report_schema_version > 1) AS schema_unsupported,
       SUM(report_json IS NULL OR NOT JSON_VALID(report_json)) AS report_json_invalid,
       SUM(selected_record_ids_json IS NULL OR NOT JSON_VALID(selected_record_ids_json)) AS selected_json_invalid,
       SUM(content_hash IS NULL OR report_json IS NULL OR
           BINARY content_hash <> BINARY SHA2(CONVERT(report_json USING utf8mb4), 256)) AS content_hash_mismatch
FROM experiment_report
GROUP BY report_schema_version
ORDER BY report_schema_version;
COMMIT;
'@
    & mysql --host=$DatabaseHost --port=$DatabasePort --user=$DatabaseUser --database=$DatabaseName `
        --default-character-set=utf8mb4 --batch --connect-timeout=5 --execute=$sql
    if ($LASTEXITCODE -ne 0) { throw '报告只读巡检失败，不能将其记录为已完成。' }
} finally {
    $env:MYSQL_PWD = $priorPassword
}
