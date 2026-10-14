# 受控线上 A/B 验证

本文记录受控线上 A/B 的后端、页面与分层工程验收。接口与分析口径以[正式契约](controlled-online-ab-api-contract.md)及[ADR-0007](adr/0007-controlled-online-ab.md)为准。下文各批历史结果保留当时的验证边界；部署开关默认关闭，实际启动条件见“启动就绪判定与部署前置”。

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

## 启动就绪判定与部署前置

2026-10-14 真实专用合成样本的工程闭环已完成：两组均有至少两份独立文档，覆盖重复 ORIGINAL、正式审批合并、草稿落地、原始评价/非盲态更正、带任务停止/紧急停止、停止后普通回退、迟到终态核验、报告冻结及结论门禁。失败、超额和缺失均保留；报告结论为 INSUFFICIENT_EVIDENCE，样本和盲态覆盖不足，不证明候选质量改善，也不推广配置。真实负面检查与既有协议/隔离数据库/双实例证据分层，不宣称在业务部署注入过全部历史故障。

运行总预算使用实际权威账本核对，停止后无未结算预留、执行占槽或结算未知；已批准合并内容保留原文，草稿没有暂存遗留。未投递任务没有 AgentExecution 或 Token 账本，运行保护可独立确证零调用，但报告继续保留执行/账本投影缺失，不伪造零费用记录。新报告修订的概要/完整身份和决定引用一致，同创建请求及终态恢复幂等，旧报告不可改写。历史原始失败评价也没有被后来的成功求值替换。

MCP注解扫描器公布枚举常量名，未遵循 ChangeOp 的小写 `@JsonValue`，导致模型按工具 schema 提交大写操作码失败。[WorkbenchMcpSchemaTest](../backend/task-service/src/test/java/com/agentdoc/task/mcp/WorkbenchMcpSchemaTest.java) 使用实际生成器验证两种写工具、两种操作码与载荷往返，非法编码仍拒绝；[McpChangeProposal](../backend/task-service/src/main/java/com/agentdoc/task/mcp/McpChangeProposal.java) 只覆盖 schema 描述，不改变业务 DTO、操作解析或权限。最终 MCP应用/schema/恢复4类24项定向回归通过；维护者加载 Task 后，真实初始化和 `tools/list` 确认两种写工具均公布小写 `replace/append`。该复核为元数据读取，未调用工具、模型或修改文档，自己的MCP会话已清理。

停止后的人工窄核验最终复用当前人类权限下的独立 Agent 事实及原有效 Task 凭证只读 A2A；不在 STOPPED 新签 CONTROL，不改变原凭证过期恢复政策。Task 6类38项回归覆盖过期/非法证明仍走原恢复策略、A2A身份冲突、来源未知/权限撤销/暂存/锁丢失拒绝；更新部署后的实际终态恢复与重复请求已核验。报告审批展示错误层级修复先复现页面字段缺失，随后3文件28项前端回归、lint和类型检查通过；真实页面已能显示已合并及人工修改比例，服务端事实没有修改。

2026-10-13 P6-05 真实服务验收发现并修复了两项评估问题：冻结 TASK_FACTS/AUDIT_FACTS 规则此前仍被求值入口拒绝；NUMBER 阈值按规范冻结为十进制字符串，但报告错误要求数字 JSON，导致已完成的文本指标显示缺失。修复复用既有确定性引擎，并为参与事实生成稳定证据摘要；未知账本仍拒绝求值。评估 9 类 64 项定向测试、专属随机隔离 MySQL 的评价持久化 6 项全部通过，无跳过，隔离库已清理。旧报告不重写，新修订的真实服务核验须在更新部署后进行。

停止后迟到的 Agent 终态可能因执行槽已释放而无法走普通草稿写入收尾。本批增加当前原操作者/受保护 OWNER 的人工无暂存核验路径，仅在当前人类授权的独立执行事实、原有效任务凭证只读 A2A、完整冻结账本、远端身份和当前文档授权一致时条件同步本地 Task。Document 3 类 16 项、Task 6 类 38 项定向回归通过；包含暂存存在、账本缺失、仍在运行、权限撤销、清单/授权人漂移、锁丢失及A2A身份冲突拒绝，以及迟到取消的真实终态映射、重复恢复不重复写回、原凭证过期/非法仍走原策略。更新部署后的真实核验已完成；不在 STOPPED 新签 CONTROL，不扩大文档写权限，不放宽原凭证过期策略。

预检不再无条件拒绝所有启动：Evaluation 的 `agent-doc.online.enabled` 和 `agent-doc.security.online-capability-enabled` 均为 true，且原有范围/当前权限、冻结规则、依赖和占位检查通过时，才返回 startable=true。两个部署条件默认 false；缺少任一条件仍返回 ONLINE_EXECUTION_NOT_READY。Auth 签发、Evaluation 验签及授权人/实验/Space/manifest一致性仍在实际 start/resume 状态写入之前校验，配置不能代替这些步骤。

[OnlineExperimentServiceTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineExperimentServiceTest.java) 覆盖默认关闭、缺少任一部署条件、有效工程草案允许启动、部署配置不能绕过范围/依赖/规则/占位错误，且保留小样本警告。[OnlineExperimentLifecycleServiceTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineExperimentLifecycleServiceTest.java) 覆盖保护授权先于状态写入、签发失败及授权人不符时禁止写入。2026-10-13 定向回归跨 Auth/Task/Agent/Evaluation 共 14 类、65 项通过，无跳过；真实签名/安全过滤链使用隔离测试配置和 RPC 替身。隔离 MySQL 的迁移、真实Mapper预检与双模板事务共 7 项通过，无跳过，5个专属随机验证库已清理；API/模板验证库先升级到当前 V42，没有改动业务数据库。

部署时 Auth、Document、Task、Agent、Evaluation 均需设置 `--agent-doc.security.online-capability-enabled=true`，并保持与 Auth 一致的 issuer（默认 agent-doc-workbench）；Task 和 Evaluation 还须分别设置 `--agent-doc.online.enabled=true`：前者开启 ORIGINAL 创建时的权威线上路由，后者开启权威对账。Task 的路由开关默认 false，此时创建仍走普通执行，不会进入线上账本；Evaluation 的预检不能证明 Task 进程已加载该开关。参数是 Spring Boot Program arguments，保留既有密钥和其他服务配置；不要关闭验签、修改数据库状态或把普通 JWT 当作线上窄凭证。运行进程须加载当前代码与配置后，才进行真实 HTTP/消息/模型/审批/报告验收。上述源码与隔离结果不等同于实际部署已启用或模型闭环已完成。

真实验收先创建一个专用任务，立即核对返回的 `onlineExperimentId`、`onlineAssignmentId` 及实验接受记录，再扩大样本；未绑定时停止新建并核查配置。所有专用任务消耗都纳入验收总预算，普通任务不进入线上实验账本不能解释为零消耗。工具白名单与任务指令必须让模型能够获知文档类型和冻结基线版本；需要时提供 `workbench_get_task_context`，不能让模型猜版本或反复调用错误写工具。预算超限、工具失败和未产生交付均按实际失败记录。

真实新建文档的初始版本为0，线上分配曾错误地使用正数资源ID解析器而拒绝该版本；Auth签发和Document冻结版本核验也存在同类问题。已增加统一的文档版本解析，资源ID规则保持严格。Auth真实签名测试先复现零版本交换错误；修复后9类41项定向测试通过，另3项隔离MySQL验证零版本分配/重试、同请求跨实例幂等及每组执行槽限制，均无失败或跳过，独占数据库已清理。这些结果证明代码修复；运行服务仍需重新加载后验证真实Task/模型/交付闭环。

## 终态边界修复与页面时延补证

2026-10-14 收口复查发现两个遗漏：线上终态规则把未收敛 Task 求值为 FAILED；报告只用 AgentExecution 状态，可能把仍为 CANCELING 的 Task 记为已完成及主指标通过。新增测试先复现失败，修复仅在 Evaluation 的线上事实适配和报告计算生效，既有 phase3-v1 引擎、已发布版本和历史评价不改。

[OnlineFactEvaluationServiceTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineFactEvaluationServiceTest.java) 覆盖运行、待授权、取消、未知状态、明确失败和独立账本审计；[OnlineReportCalculatorTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineReportCalculatorTest.java) 覆盖双方终态、观察截止、Task 缺失、历史未就绪 false、最新缺失不回退，以及两组各 50 文档仍未完成时关闭比较门禁。[OnlineEvaluationPersistenceMySqlTest](../backend/evaluation-service/src/test/java/com/agentdoc/evaluation/service/OnlineEvaluationPersistenceMySqlTest.java) 验证 SKIPPED 不生成 false、跨实例重试复用、后续完成追加及旧失败不改写。新报告计算版本为 online-report-v2，旧 revision 和决定引用保持不变。

相关后端回归 527 项通过，另 33 项环境跳过不计通过；最终定向 40 项通过，隔离 MySQL 迁移/原始产物/评价/报告事务 35 项通过，无跳过。隔离测试只创建和清理专属随机名称的验证库，没有修改业务数据库。维护者重新编译并重启 Evaluation 后，实际 HTTP 求值、报告和页面核验已通过。

部署补验 44 项检查通过：四项已有执行的冻结事实求值为 3 PASSED / 1 FAILED，失败执行的账本完整性仍独立通过；同请求复用原尝试。无执行身份的请求拒绝且没有尝试或失败指标，VIEWER 请求仍为 403。追加的报告 revision 5 使用 online-report-v2，Task 取消为 1、运行状态未知为 0；缺少 AgentExecution 和账本的来源仍保留未知，不伪造零值。新旧报告请求幂等，历史 revision 1～4 的 reportHash/inputHash 不变，24 条历史评价的身份/状态/原因码不变。当前决定仍引用原 revision 4，新的报告没有改写旧决定。

真实页面显示 revision 5 / SUPPORTED，取消、完成及失败计数与本地权威状态一致，工程比较门禁保持关闭。只读账本复核仍为 12 Task、11 Execution、11 条唯一账本、48 次模型调用与 107,787 Token；STOPPED、0 占槽/预留/结算未知，8 份文档版本及无暂存状态不变。本次补验只追加确定性评价和不可变报告，没有新任务、模型调用或文档写入。未收敛状态与足量样本门禁由上述单元/隔离数据库验证，没有为了复现而改写业务任务状态。

真实 localhost 页面使用既有专用合成样本，预热 3 次后，每个动作测量 20 次，失败数均为 0。列表完成标志为全部三行及实际任务数可见；详情为真实分配行与停止状态可见；完整报告为指定 revision 的 SUPPORTED 状态及已记录结论可见。每轮重新进入页面，避免把上一次已显示的报告当成加载完成。

| 页面动作 | 样本数 | p50（毫秒） | p95（毫秒） | 最大（毫秒） |
| --- | ---: | ---: | ---: | ---: |
| 返回列表至业务行可见 | 20 | 175 | 189 | 190 |
| 进入详情至分配行可见 | 20 | 178 | 186 | 278 |
| 核验完整报告至正文和结论可见 | 20 | 302 | 312 | 320 |

上述为自动化端记录的点击至浏览器业务内容可见耗时，包含浏览器工具往返开销；不是浏览器原生绘制指标、首次冷启动或生产容量基准。完整报告测量读取历史固定报告，不重算或改变结论。独立 HTTP 小样本测量每入口预热 10 次、测量 100 次：实验列表 p95 82.906 ms，assignment 每页 10/100 条为 74.630/64.946 ms，报告概要为 54.079 ms，失败数均为 0；HTTP 时间与上述页面时间、历史百万行服务/MySQL 时间分别记录。补证没有新建任务、调用模型或写文档。
