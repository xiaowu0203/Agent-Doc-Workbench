# 受控线上 A/B 验证

本文记录 P6-03 原始评价、服务器盲态资格与 ONLINE_V1 后端验收。接口与分析口径以[正式契约](controlled-online-ab-api-contract.md)及[ADR-0007](adr/0007-controlled-online-ab.md)为准。完整页面属于 P6-04，真实服务、消息、模型及页面闭环属于 P6-05；公开 start/resume 的部署门禁仍关闭。

本批实现及测试对应提交 [e678ca6](https://github.com/xiaowu0203/Agent-Doc-Workbench/commit/e678ca6c16c4f3778d5d306701539e4d0fd826a6)。该提交的说明因终端编码显示为问号，源码内容及验收结果保持完整；此记录向前补充可复查的实现关联，不改写已推送历史。

## 功能与历史验证

2026-10-12 在 JDK 21 上完成相关模块回归，并在专门创建的 MySQL 5.7 隔离库验证真实迁移、唯一键、事务和竞争。验证不修改应用业务库，隔离库在结束后清理。

| 目标 | 可复查测试 |
| --- | --- |
| 完整原始集合包含失败提案；子集、旧集合和伪造身份拒绝 | [OnlineOriginalChangeSetTest](../backend/common/common-core/src/test/java/com/agentdoc/common/utils/OnlineOriginalChangeSetTest.java)、[OnlineChangeReviewServiceTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineChangeReviewServiceTest.java) |
| 0 分有效，首评唯一，更正只追加，实际证据与权限重复核验 | [OnlineQualityFeedbackServiceTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineQualityFeedbackServiceTest.java)、[OnlineQualityFeedbackPersistenceMySqlTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineQualityFeedbackPersistenceMySqlTest.java) |
| 完整隐藏上下文绑定冻结业务指令；历史不能补造盲态；知情与评分竞争保守保留偏差 | [OnlineBlindReviewServiceTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineBlindReviewServiceTest.java)、[OnlineReportReviewMySqlTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineReportReviewMySqlTest.java) |
| 每文档首 assignment，0/false/缺失、80% 边界、盲态更正、币种/截止及独立审批信号 | [OnlineReportCalculatorTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineReportCalculatorTest.java) |
| 权威事实批量读取，只用冻结价格，不从 Trace 或当前配置反推 | [TaskOnlineReportFactsServiceTest](../backend/task-service/src/test/java/com/agentdoc/task/service/TaskOnlineReportFactsServiceTest.java)、[OnlineReportExecutionFactsServiceTest](../backend/agent-service/src/test/java/com/agentdoc/agent/service/OnlineReportExecutionFactsServiceTest.java) |
| 报告并发重算复用、新 revision 追加、决定绑定、损坏/未知 schema 拒绝；分块连续性与整体摘要 | [OnlineReportReviewMySqlTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineReportReviewMySqlTest.java) |
| 独立安全确认暂停并幂等审计；普通低分没有保护副作用 | [OnlineReportReviewMySqlTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineReportReviewMySqlTest.java) |
| 新装、重试与 V38→V42 升级保持历史校验和、反馈和旧报告不变 | [OnlineReportReviewMigrationTest](../backend/auth-service/src/test/java/com/agentdoc/auth/migration/OnlineReportReviewMigrationTest.java) |
| 精确比例区间与独立高精度参考向量匹配 | [OnlineExperimentStatisticsTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineExperimentStatisticsTest.java)、[区间 fixture](fixtures/online-clopper-pearson-v1.json) |

最终回归数量与规模测量见下表；跳过测试不计为通过。集成测试依赖专门隔离库环境变量，未配置时按设计跳过，普通单元测试成功不能代替隔离 MySQL 验证。

| 验证集 | 通过 | 失败/错误 | 跳过 |
| --- | ---: | ---: | ---: |
| 96 类相关回归 | 467 | 0 | 3 |
| 隔离 MySQL 迁移/原始提案捕获/评价及报告事务 | 33 | 0 | 0 |
| 百万 assignment 规模测试 | 1 | 0 | 0 |

回归跳过项是 Agent 在线配置 MySQL、线上实验基础 MySQL、旧离线报告兼容性环境测试；它们需要额外的各自集成环境，本次 33 项隔离测试的计数不包含这些跳过项。

可从项目根目录运行 JDK 21 Maven 定向测试，例如：

```powershell
mvn -f backend/pom.xml -pl evaluation-service,task-service,agent-service -am '-Dtest=OnlineReportCalculatorTest,OnlineExperimentStatisticsTest,OnlineBlindReviewServiceTest,OnlineExperimentReportServiceTest,TaskOnlineReportFactsServiceTest,OnlineReportExecutionFactsServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

MySQL 测试只接受 localhost/127.0.0.1 下名称匹配 `agentdoc_p603_{change_fresh|change_upgrade|evaluation}_{12位小写十六进制}` 的专用空库。配置 `P603_CHANGE_FRESH_MYSQL_URL`、`P603_CHANGE_UPGRADE_MYSQL_URL`、`P603_EVALUATION_MYSQL_URL`、`P603_MYSQL_USER`、`P603_MYSQL_PASSWORD` 后，先运行 auth-service 的 `OnlineReportReviewMigrationTest` 准备 V42，再运行 task-service 的 `TaskOnlineOriginalProposalMySqlTest` 与 evaluation-service 的四个事务测试类。账号凭证仅通过本地环境提供。

规模测试另需 `P603_SCALE=1` 和 `P603_SCALE_RESULT` 输出路径，使用独立、已迁移 V42 的空 evaluation 隔离库运行 `OnlineReportScaleMySqlTest`。测试创建代表性数据，不能指向业务库或已有验证数据的库；运行方负责专用隔离库的创建与清理。

## 百万 assignment 规模

[OnlineReportScaleMySqlTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineReportScaleMySqlTest.java) 创建 100 个历史实验，每实验 10,000 条 assignment，总量 1,000,000；每条保存约 1,860 字节的代表性私有 binding。实际应用服务包含 MyBatis 分页、参与文档聚合、转换、当前人类知情记录及报告摘要校验，权限 RPC 使用替身。

每个读取入口先预热 10 次，再测量 100 次；目标是 p95 ≤1,000 毫秒。初次验证发现大输入超过 MySQL 默认 packet 上限，改为 V41 事务分块；随后列表 p95 约 1.38 秒，V42 添加参与文档覆盖索引与分页索引。已有迁移未回改。

| 入口 | 请求数 | p95（毫秒） | 失败数 |
| --- | ---: | ---: | ---: |
| 实验列表（每页 10 实验） | 100 | 71.58 | 0 |
| assignment 深分页（第 100 页，每页 100） | 100 | 39.40 | 0 |
| 固定 revision 报告概要 | 100 | 11.83 | 0 |

上表为最终源码复测，完成于 2026-10-12 15:24（Asia/Shanghai）。前一轮补索引后的同规模 p95 分别为 103.60、45.42、15.05 毫秒，也均通过。首份 10,000 条 assignment 的读取、聚合、逻辑输入冻结及事务保存本轮约 4.86 秒、前一轮约 12.61 秒，逻辑输入约 11.64 MB；这项独立计时受数据库缓存等环境影响，不适用读取接口的 1 秒目标。EXPLAIN 的参与文档计数使用 `idx_online_assignment_summary`，显示 `Using index`。

这是单进程、单并发、已预热的服务与数据库测量；绑定用于模拟数据体积，不是有效执行证明。规模样例没有真实模型、跨服务网络或有效质量事实，相关状态保持 UNKNOWN，比较门禁关闭。有效规则、人工评分、更正和审批语义由上述定向测试覆盖；性能数字不代表 HTTP、页面、模型延迟、并发容量或质量改善。

概要只验证报告正文，返回 `SUMMARY_VERIFIED`；完整报告和决定同时验证完整冻结输入，使用 `SUPPORTED`。两种状态不可互换。
