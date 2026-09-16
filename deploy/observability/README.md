# 本地可观测基础设施

本目录提供 Phase 2 使用的 OpenTelemetry Collector、Jaeger v2 和 OpenSearch 单点环境。

## 本地启动

如果旧的 `research-jaeger` 仍在运行，先停止它以释放 `4317` 和 `16686`：

```powershell
docker stop research-jaeger
```

启动新栈：

```powershell
docker compose -f deploy/observability/docker-compose.yml up -d
```

初始化或更新本地 14 天 Trace 保留策略：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/observability/Initialize-TraceRetention.ps1 -RetentionDays 14
```

检查健康状态：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/observability/Test-Observability.ps1
docker compose -f deploy/observability/docker-compose.yml ps
```

验证 Collector 白名单与脱敏后的端到端 Trace：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/observability/Test-TracePipeline.ps1
```

验证 Collector、Jaeger 与 OpenSearch 分别停止后能够恢复，并在最后重跑健康检查和 Trace 管道：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/observability/Test-ObservabilityRecovery.ps1
```

也可用 `-Scenario Collector`、`Jaeger` 或 `OpenSearch` 单独验证。脚本只执行可恢复的 `stop/start`，不会删除容器或数据；它验证观测基础设施恢复能力，业务任务在故障期间的连续性仍需按 P2-08 验收清单另行执行。

Jaeger UI：<http://127.0.0.1:16686>

本地 Compose 不挂载 OpenSearch volume。`stop/start` 使用同一容器时数据仍在；执行 `down`、强制重建或删除 OpenSearch 容器后 Trace 会丢失，这符合当前本地开发决策。

停止：

```powershell
docker compose -f deploy/observability/docker-compose.yml stop
```

删除本地观测容器和临时 Trace：

```powershell
docker compose -f deploy/observability/docker-compose.yml down
```

## 持久目录覆盖

需要持久化时先创建一个明确的数据目录并设置绝对路径，然后叠加 override。示例只展示 Windows 本机路径；生产环境应使用受备份保护的专用磁盘目录。

```powershell
New-Item -ItemType Directory -Force 'D:\agent-doc-data\opensearch'
$env:OPENSEARCH_DATA_DIR = 'D:\agent-doc-data\opensearch'
docker compose -f deploy/observability/docker-compose.yml -f deploy/observability/docker-compose.persist.yml up -d
powershell -ExecutionPolicy Bypass -File deploy/observability/Initialize-TraceRetention.ps1 -RetentionDays 30
```

该 override 只增加持久目录，不自动完成生产认证、TLS 和备份。正式生产启动前仍需按 Phase 2 设计补齐这些配置。

不要在需要保留 Trace 时删除宿主机数据目录。日常停止使用 `stop`，不要使用带 volume 删除语义的命令。

## Java 服务接入

首次下载并校验固定版本 Java Agent：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/observability/Install-JavaAgent.ps1
powershell -ExecutionPolicy Bypass -File deploy/observability/Test-JavaAgent.ps1
```

每个服务使用独立 PowerShell 窗口。先以 dot-source 方式加载对应服务环境，再在同一窗口用现有方式启动服务：

```powershell
. .\deploy\observability\Enable-ServiceTelemetry.ps1 -ServiceName gateway-service
```

`ServiceName` 依次可取 `gateway-service`、`auth-service`、`document-service`、`task-service`、`agent-service`。脚本固定使用 Java Agent `2.31.1`，只导出 Trace 到 Collector，关闭 Agent 的 Metrics/Logs 导出；当前 Phase 不部署 Prometheus，也不建立日志后端。

使用 IntelliJ IDEA 时，不需要逐项填写 Environment variables。Java Agent 的本地默认值统一存放在 `otel-javaagent.properties`。在对应 Run Configuration 的 VM options 中加入：

```text
-javaagent:F:\SpringAi\Agent-Doc-Workbench\deploy\observability\.tools\opentelemetry-javaagent.jar
-Dotel.javaagent.configuration-file=F:\SpringAi\Agent-Doc-Workbench\deploy\observability\otel-javaagent.properties
-Dotel.service.name={service-name}
```

将 `{service-name}` 分别替换为 `gateway-service`、`auth-service`、`document-service`、`task-service` 或 `agent-service`。`application.yml` 由 Spring Boot 在应用启动阶段读取，而 Java Agent 在此之前启动，因此 Agent 配置和服务名不能依赖 Spring YAML。系统属性或 `OTEL_*` 环境变量仍可覆盖上述默认配置文件。
