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

## 前端页面与边界验证

线上实验通过既有“评估与实验”入口访问。当前页面包含冻结草案、分页列表/详情、OWNER 预检与启停确认、分配观察、独立隐藏分组原始评价、固定 revision 的报告与人工结论。评估器目录支持发布线上原始文本/变更规则；普通离线版本选择器继续排除这些规则。

| 边界 | 可复查测试 |
| --- | --- |
| 长身份/Token 文本、0/false/未知、旧 schema 与部署未就绪 | [presentation.spec.ts](../frontend/src/features/evaluation/online/presentation.spec.ts) |
| 四项启动确认、预检过期/替换/撤权、正常停止与熔断、幂等重试 | [actions.spec.ts](../frontend/src/features/evaluation/online/actions.spec.ts) |
| 冻结草案编辑失效、发布版本及父资源重新核验、重复文档拒绝 | [create.spec.ts](../frontend/src/features/evaluation/online/create.spec.ts) |
| 当前空间权限、管理员不替代 OWNER、轮询退避/后台暂停/离页取消 | [view.spec.ts](../frontend/src/features/evaluation/online/view.spec.ts) |
| 独立隐藏接口、完整无效产物保留、0 分、更正、未知盲态与撤权清除原文 | [review.spec.ts](../frontend/src/features/evaluation/online/review.spec.ts) |
| 不兼容报告不展示正文、完整输入核验、固定版本决定、样本/覆盖阻塞、安全例外 | [report.spec.ts](../frontend/src/features/evaluation/online/report.spec.ts) |
| 线上/离线选择器分离与受限深链接 | [catalog.spec.ts](../frontend/src/features/evaluation/components/catalog.spec.ts)、[routes.spec.ts](../frontend/src/features/evaluation/routes.spec.ts) |
| 空间占位、暂停/停止中保留占位、当前生产配置与冻结基线的区别、撤权/切空间清除 | [participation.spec.ts](../frontend/src/features/evaluation/online/participation.spec.ts) |
| 两个普通任务入口复用请求键、阻止重叠提交、编辑与响应丢失说明、取消过期请求 | [use-task-creation.spec.ts](../frontend/src/features/task/use-task-creation.spec.ts)、[TaskCreateView.spec.ts](../frontend/src/views/TaskCreateView.spec.ts)、[DocumentEditorView.spec.ts](../frontend/src/views/DocumentEditorView.spec.ts) |
| 普通 Task 的实验/分配身份、实际组和冻结模板核对、未知组不推断、权限清除 | [task-online-association.spec.ts](../frontend/src/features/task/components/task-online-association.spec.ts) |
| Task 只读关联保留长身份、Token 回填保留绑定、不返回 WAIT/执行凭证或槽证明 | [TaskVOTest](../backend/task-service/src/test/java/com/agentdoc/task/pojo/vo/TaskVOTest.java) |
| 多主/辅助/保护规则、全部文档期望映射、精确十进制与秒级窗口 | [rule-draft.spec.ts](../frontend/src/features/evaluation/online/rule-draft.spec.ts)、[create.spec.ts](../frontend/src/features/evaluation/online/create.spec.ts) |
| 完整冻结 Prompt 的身份核验、明确读取、授权撤销和迟到响应 | [prompt-comparison.spec.ts](../frontend/src/features/evaluation/online/prompt-comparison.spec.ts)、[AgentOnlineConfigServiceTest](../backend/agent-service/src/test/java/com/agentdoc/agent/service/AgentOnlineConfigServiceTest.java) |
| 隐藏更正链分页、真实链尾、作者自由文本隔离、未知响应核对与重复阻断 | [quality-history.spec.ts](../frontend/src/features/evaluation/online/quality-history.spec.ts)、[review.spec.ts](../frontend/src/features/evaluation/online/review.spec.ts)、[OnlineQualityHistoryServiceTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineQualityHistoryServiceTest.java) |
| 发布版本和父资源批量核验、数字 JSON ID 拒绝、敏感读取 no-store | [EvaluationCatalogServiceTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/EvaluationCatalogServiceTest.java)、[OnlineEngineeringReadControllerTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/controller/OnlineEngineeringReadControllerTest.java) |

2026-10-12 首批前端全量 43 个测试文件、316 项测试通过；窄窗口创建弹窗修正后，9 个文件、117 项定向回归通过。补齐 Task/Space 关联后，全量 46 个文件、337 项通过；最后的请求身份观察修正后，3 个文件、21 项再次通过。全量 lint、类型检查与生产构建通过。Task 只读投影及既有详情/权限/原始证据回归 18 项通过，使用 JDK 21 与隔离构建目录，没有迁移业务数据库。构建仍提示较大的既有公共/用量分包，没有作为本批无关重构处理。

2026-10-13 补齐多角色规则、逐文档期望覆盖、秒级窗口、固定保护说明、完整冻结 Prompt 对照和隐藏反馈更正链。最终前端全量 49 个文件、361 项测试通过，lint、类型检查和生产构建通过；新增读契约及创建/原始评价/报告相关后端 9 类、75 项定向测试通过，无跳过。隔离 MySQL 复测迁移、原始提案捕获、评价更正竞争及报告冻结，共 33 项通过；只创建并清理专属随机名称的验证库，没有迁移业务数据库。

浏览器布局检查使用隔离接口替身和示例数据，覆盖列表、创建、正常停止确认、隐藏原始评审、报告概要、Task 冻结关联、暂停/停止中占位及撤权清除。最后补充实际规则发布确认与发布后只读、已发布版本选择、多规则草案确认、冻结 Prompt 全文与撤权清除、评价响应丢失后的链尾核对和重复阻断，以及完整报告的小样本结论门禁。680px 窄屏下 Prompt 改为单列，正文宽度等于视口内容宽度，创建弹窗输入可见。全部网络请求由本地内存适配器处理，模拟发布/创建/反馈没有业务副作用；未连接真实业务后端或调用模型。

P6-04 的源码、组件/API/权限回归和隔离页面验收已收口。真实服务、消息、模型、审批及页面网络闭环仍需后续部署验收；本结果不证明质量改善、生产容量或真实取消成功，公开 start/resume 部署门禁保持关闭。
