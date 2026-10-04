# 受控线上 A/B：数据、API 与协议契约

状态：已冻结，2026-10-04；依据 [ADR-0007](adr/0007-controlled-online-ab.md)。下文为完整目标契约；当前源码实现边界见第 11 节，不代表已经部署。实施按先数据与只读预检、再分配安全内核、再评价报告、最后页面和受控真实验收推进。

## 1. 基线、版本与限制

- Run 继续由 Task/AgentExecution 一对一组成；Task 公共身份、输入 schema 1、execution schema 3 及历史 hash 不变。
- 线上 manifest/template/binding/bucket/report 各使用独立 domain + schemaVersion 2；废弃实现的 schema 1 不自动升级或执行。reportType 仍为 ONLINE_V1，正文 schemaVersion = 2。
- V30 历史 SQL checksum -1400201146，保持原文。V31 起向前调整，不重新 CREATE 已存在四张线上表，不复用旧编号；发布/升级目标必须包含 V1～V30 历史。
- 首版文档范围 1～1000 项，排序后唯一，跨 Space 拒绝；两组各一个执行槽，ALL_BOUND、无外部 MCP/跨任务会话记忆。
- 新资源 ID 使用正十进制字符串，范围 1～9223372036854775807，不接受前导零、加号、空白或 JSON 浮点。内部 Java Long 转同一字符串进入协议。
- name 去两端空白后 1～100 Unicode code point；requestKey 为 1～64 ASCII 字节，格式 [A-Za-z0-9._:-]+，区分大小写。reason/comment 上限 2000 code point。hash 为 64 位小写 hex，seed 为 32 位小写 hex（服务端随机 16 字节）。
- 总 Token/单 Task Token 为正 Long 且总额至少容纳一个 Task；maxTaskCount 1～10000。perTaskTimeoutSeconds 取当前 Agent 已配置且有效的执行超时，不另建可与 Agent 不一致的覆盖参数。每次任务预算必须显式/有效解析为有限值且不大于 manifest.perTaskTokenLimit，否则拒绝，不缩减原请求。
- candidateWeightBps 为 1～9999，baselineWeightBps = 10000 - candidateWeightBps。assignmentWindowSeconds 默认 604800，允许 1～2592000；completionObservationSeconds 默认 172800，允许 0～604800，均启动前冻结。
- 私有 manifest canonical UTF-8 上限 1 MiB，原始/最终 Prompt 同时受当前 SkillPackageProperties 的上限约束，预检返回实际限制；超限拒绝，不截断。

这些限制是首版领域协议常量。扫描频率、OTel、容量告警等部署参数单独配置；保留/取消/容量确认不能通过设置假默认值绕过。

## 2. Canonical、hash 与固定分桶

所有新 hash 输入采用 envelope：domain 字符串、schemaVersion 整数、payload 按下表定义（online.expected 为数组，其余为对象）。对象 key 按 UTF-8 无符号字节序排序，JSON 无额外空白/BOM/末尾换行，UTF-8 SHA-256 小写 hex；null/空对象/空数组互异，禁止重复 key、非法 Unicode、二进制浮点/NaN/Infinity。

ID/版本化十进制指标使用规范文本；整数策略参数保持 JSON 整数；小数参数必须为无指数的规范十进制字符串（去末尾零、零为 0），不得把字符串 0.80 和 0.8 当不同已规范输入。业务字符串保留内容，除各字段明确 trim 外不做 NFC 或换行替换。

documentIds 按数值升序；两组固定 BASELINE、CANDIDATE 顺序；Skill 按 versionId、工具按 UTF-8 工具名、规则按 ruleKey、期望映射按 documentId/动作排序。断言/业务焦点区域有顺序，保留原顺序；必须先完成领域排序再 canonical。

| domain | payload 固定字段 |
| --- | --- |
| online.bucket | experimentId、documentId、seed |
| online.create-request | creatorId、spaceId 及第 3 节所有创建字段（不含 clientRequestKey；服务端将 hash 纳入 key 冲突检查） |
| online.template-request | experimentId、spaceId、agentId、requestHash、candidateAgentPrompt；独立验证模板捕获重试，不与创建请求 hash 混用 |
| online.task-request | actorId、spaceId、agentId、documentId、name、instruction、tokenBudget、readScope、focusRegions |
| online.template | spaceId、agentId、agentConfigVersion、role、systemPrompt、nonPromptConfig、dependencyManifest |
| online.non-prompt | template 去除 role/systemPrompt 后全部字段 |
| online.dependencies | dependencyManifest 全部字段 |
| online.expected | 按 documentId 排序的 expectedBindings 数组；该 domain 的 payload 为数组 |
| online.preflight | experimentId、actorId、manifestHash、dependencyHash、stateVersion、checkedAt；缺失当前依赖使用 null |
| online.manifest | 第 4 节全部私有 manifest 字段 |
| online.binding | 第 6 节 OnlineTaskBindingDTO 全部字段（不含 bindingHash） |
| online.slot-permit | experimentId、assignmentId、taskId、bindingHash、generation |
| online.report-input | manifestHash、assignmentCutoffSequence、assignmentCutoffAt、observationCutoffAt、选定主体/结果/账本/证据/反馈的 ID/hash/状态及计算规则版本 |
| online.report | 第 9 节报告正文（不含自身 reportHash） |

online.task-request 的 name/instruction 使用既有 Java trim；readScope null 规范为 FULL，focusRegions null 为 []；tokenBudget 的原始 null 保留，不用当前默认预算替代。意图第一次解析默认/冻结文档版本后保存 effectiveBudget 和输入身份；同 key 重试使用该意图，不再次读当前版本改变输入。

分桶：hash = SHA256(canonical online.bucket envelope)；bucket = 无符号完整 256 位 hash 整数 mod 10000；bucket < candidateWeightBps 为 CANDIDATE，否则 BASELINE。前端不能提交 seed/桶/组。边界 bucket = weight 属于 BASELINE。

公开 [JSON 向量](fixtures/controlled-online-ab-v2.json) 与 [独立参考验证器](fixtures/verify_controlled_online_ab.py) 冻结规范；后续 Java/前端需比对这些向量，而不是修改向量迁就实现。

## 3. 公共 API、DTO 与权限

前缀 /api/evaluation/online-experiments；沿用 Result/PageVO 和 PageParam，ID 定位 @PathVariable，复合分页 POST JSON。所有入口先确认资源 Space，管理必须校验受保护 OWNER（不能走 SUPER_ADMIN 兜底），再校验具体动作。普通只读保持现有 Space 权限规则；Task/正文下钻另行资源授权。

| method/path（相对于前缀） | DTO/VO | 权限 |
| --- | --- | --- |
| POST / | OnlineExperimentCreateDTO → OnlineExperimentVO | OWNER + evaluation:manage + agent:manage |
| POST /search | OnlineExperimentSearchParam → PageVO<OnlineExperimentSummaryVO> | space:read + evaluation:read |
| GET /{id} | OnlineExperimentVO | space:read + evaluation:read |
| GET /{id}/preflight | OnlineExperimentPreflightVO | OWNER + evaluation:read |
| POST /{id}/start | OnlineExperimentStartDTO → OnlineExperimentVO | OWNER + evaluation:run + task:terminate（确认自动取消策略） |
| POST /{id}/pause、/resume、/stop | OnlineExperimentStateDTO → OnlineExperimentVO | OWNER + evaluation:run；resume 还复查启动取消权限 |
| POST /{id}/emergency-stop | OnlineExperimentStateDTO → OnlineExperimentVO | OWNER + evaluation:run，取消动作另校验 task:terminate；缺取消权限仍先关门并返回未决 |
| POST /{id}/assignments/search | OnlineAssignmentSearchParam → PageVO<OnlineAssignmentVO> | space:read + evaluation:read；任务详情另查 task:read/文档访问 |
| GET /{id}/reports、/reports/{revision} | revision 列表/OnlineExperimentReportVO | space:read + evaluation:read |
| POST /{id}/reports/recalculate | OnlineReportRecalculateDTO → OnlineReportRevisionVO | OWNER + evaluation:manage |
| POST /{id}/decision | OnlineExperimentDecisionDTO → OnlineExperimentVO | OWNER + evaluation:manage |

名称、ID 与脱敏 reasonCode 可读，普通 VO 不返回 Prompt、完整 template/manifest、secret、原始正文或失去访问权的证据。OWNER + agent:manage 的专门创建/编辑查看流程才允许读取 Prompt diff，停止后依然复查权限，不直接在报告返回正文。

### 3.1 创建与分析参数

| DTO | 冻结字段 |
| --- | --- |
| OnlineExperimentCreateDTO | clientRequestKey、spaceId、agentId、name、documentIds、candidateAgentPrompt、candidateWeightBps、authorizedTokenBudget、maxTaskCount、perTaskTokenLimit、assignmentWindowSeconds、completionObservationSeconds、analysisPlan、rules |
| OnlineAnalysisPlanDTO | mode（ENGINEERING/DESCRIPTIVE）、mdeAbsoluteRatio、mdeReason、humanQualityRequired；固定 minDocumentsPerGroup=30、minCoverageBps=8000、minBlindedCoverageBps=8000，服务端写入 manifest，不接受临时降门槛 |
| OnlineRuleBindingDTO | ruleKey（唯一，ASCII 1～64）、role、evaluatorVersionId、metricKey、valueType、unit、comparison（EQ/GE/LE）、threshold、evidenceTarget、expectedBindings |
| OnlineRuleExpectedDTO | documentId、expectedJson（对象，遵守该发布 evaluator 契约，服务端 canonical/hash）；evidenceTarget 由所属 rule 固定 |
| OnlineExperimentStartDTO | clientRequestKey、manifestHash、expectedStateVersion、preflightProofHash、liveSideEffectsAcknowledged、retentionAcknowledged、sharedResourcesAcknowledged、emergencyCancellationAcknowledged（四项必须 true） |
| OnlineExperimentStateDTO | clientRequestKey、manifestHash、expectedStateVersion、reason（非空）；恢复不接受新预算/比例/窗口 |
| OnlineReportRecalculateDTO | clientRequestKey、assignmentCutoffSequence、observationCutoffAt（UTC ISO-8601 秒）；截止必须不晚于服务器当前时间，集合/顺序服务器查证 |
| OnlineExperimentDecisionDTO | clientRequestKey、reportRevision、reportHash、decision、reason、safetyEvidenceIds（普通比较为空，安全 REJECTED 必需） |

role 为 PRIMARY/AUXILIARY/GUARDRAIL，evidenceTarget 为 ORIGINAL_TEXT/ORIGINAL_CHANGE/TASK_FACTS/AUDIT_FACTS。threshold 按 valueType 使用 bool/规范十进制文本/字符串，metricKey/单位/方向与冻结目录一致。范围内每个 documentId 对每条必需规则的期望必须全覆盖，两组使用同一映射，不能从未绑定 TestCase 或当前规则推断。

正常 TaskCreateDTO 没有业务任务类型，首版不新增根据自然语言推断写作/查询意图的路由器。准入校验既有 Capability 动作与文档范围；质量规则使用预注册 evidenceTarget/输出契约，实际输出不适用或无证据时按冻结规则记缺失，不能临时换规则。创建页提示复杂混合用途范围可能使质量规则不适用，需要用户在范围/任务用途上作明确约束。

创建幂等范围 Space + creatorId + key，hash 不同返回冲突；Action 幂等范围 experimentId + action + actorId + key，相同请求返回已存结果，不因旧 expectedStateVersion 变小重复执行。不同请求即使同目标状态仍保留不同审计身份；顺序先鉴权、查幂等记录，再校验新请求的状态版本。

预检重算依赖、范围、规则、占位、容量/保留确认、预算可达性与分布；proofHash 绑定 manifestHash、当前依赖 hash、stateVersion 及 checkedAt，60 秒有效。start/resume 不只相信 proofHash，必须重新权威校验并原子取占位/状态。

### 3.2 SearchParam 与 VO

| 类型 | 必需字段 |
| --- | --- |
| OnlineExperimentSearchParam | spaceId、agentId?、status?、keyword?、pageNum/pageSize |
| OnlineAssignmentSearchParam | variant?、documentId?、taskId?、confirmationStatus?、settlementStatus?、pageNum/pageSize；Space 从实验确认 |
| OnlineExperimentSummaryVO | id、spaceId、agentId、name、status、stateVersion、manifestSchemaVersion/hash、candidateWeightBps、analysisMode、documentCount/participatingDocumentCount、assignedTaskCount、预算汇总、inFlight/unknown/slotCounts、reasonCodes、lastReconciledAt、时间与审计身份 |
| OnlineExperimentVO | summary + 两组模板 ID/schema/hash/nonPromptHash、依赖 hash、窗口/分析/保护参数投影、实际 startedAt/deadline/observationDeadline、占位/熔断/取消/人工决定；无正文 |
| OnlineExperimentPreflightVO | experimentId、manifestHash、checkedAt、proofHash/expiresAt、draftEligible/startable、范围/参与分布、SRM 状态、plannedUpperTokenBudget（大整数文本）、historicalEstimate/样本/缺失、issues[{code,severity,documentId?,ruleKey?}] |
| OnlineAssignmentVO | id、experimentId、acceptedSequence、documentId、taskId、variant/bucket、template/binding/input 身份、reservedTokens、confirmation/dispatch/slot/cancel/settlement 状态、权威 Task/Execution 投影、缺失原因和时间 |
| OnlineReportRevisionVO | id、experimentId、revision、reportType/schemaVersion、reportHash/inputHash、cutoffSequence/时点、generatedAt/by |

金额为规范十进制文本；unknownCost/Token 不填零。计数与枚举始终存在，缺失事实为 null + reasonCode。列表不附整报告；summary 计数不逐 Task RPC。

## 4. Manifest 与模板所有权

manifest payload 固定包含 experimentId、spaceId、agentId、name、createdBy、documentIds、bucketProtocol（schemaVersion/seed/weight）、baseline/candidate（templateId/schema/hash/nonPromptHash）、dependencyHash、ruleBindings（版本内容 hash/目录版本/expected hash）、analysisPlan、budgetPlan（total/maxTask/perTask/timeout）、resourcePlan（perVariantLimit=1）、windowPlan、protectionPlan、disclosureSchemaVersion。

保护常量：healthWindowCount=20、healthFailureBps=3000、unresolvedSeconds=300、srmMinDocuments=100、srmMinExpectedCount=10、srmThreshold=0.001、srmRecheckSeconds=300。分配后不允许修改 manifest；start 时间由第一次状态提交冻结，恢复不能重置分配 deadline 或观察窗口。

agent_online_config 保存两条不可变 template，唯一 experimentId/role，template/schema/hash、nonPromptHash、依赖清单/hash、创建身份/时间；私有模板读取当前 Agent 而非历史指令。一次 prepare-template 请求捕获一次当前配置，并构造/原子保存两份模板，不能分别两次 capture 得到不同依赖。请求幂等使用 Evaluation 预分配 experimentId + 请求 hash，跨服务失败重试返回原一对模板；创建意图保存预分配实验身份，不持事务做 RPC。

nonPromptConfig 包含当前模型 configVersion/非秘密选项/价格与币种、Skill 版本及工具白名单、Agent 文档动作限制/迭代/预算/超时、应用与工具发布身份、缓存/会话政策。两模板该证明相同；systemPrompt 由统一 Prompt 构建层基于各自 Agent Prompt 和相同 Skill 目录生成。

现有 CandidateConfig 保留原来源非空与 schema 3 proof；不将它的旧 promptHash 用于新输入。模板不保存模型 API Key；运行获取当前合法凭证但不能替换服务/模型。不可见供应商版本不虚构冻结，作为报告限制。

create 时冻结分析/配置参数；P6-01 可存完整草案但 startable=false，直至线上规则、限额、恢复、报告和授权整套能力经验证。新 evaluator 版本必须按实际发布资产绑定，本文不提供伪造的数据库 ID。

## 5. 数据与事务不变量（V31 起）

| 对象/改动 | 固定规则 |
| --- | --- |
| online_experiment（已有 V30） | active_slot=1 只供 ACTIVE/PAUSED/STOPPING；唯一 (space_id,active_slot)，替换旧 (space_id,agent_id,active_slot)；CREATED/STOPPED=null。创建 key 唯一改为 (space_id,created_by,client_request_key)，替换旧 Space/key。增加 state_version、accepted_sequence、熔断/窗口/对账/两组槽计数。 |
| online_experiment_create_intent | Evaluation 所有，唯一 (space_id,created_by,request_key)，预分配 experimentId、request_hash 和创建状态；跨服务模板创建可查证，不能留下每次换 ID 的孤儿。 |
| task_creation_intent | Task 所有，唯一 (space_id,actor_id,request_key)，预分配 task_id 唯一，request_hash、解析后的预算/输入及绑定、处理状态/原因。 |
| online_assignment（已有） | task_id 唯一，新增唯一 (space_id,created_by,request_key)，接受序号唯一 (experiment_id,accepted_sequence)；分组/输入/模板身份不可改，确认/执行槽/预算/取消投影独立。 |
| online_execution_slot | 两组占用；唯一 task_id、assignment_id、(experiment_id,variant,slot_no)；首版 slot_no=1，occupant 为 null 时可取；generation 单调，未知不释放。 |
| online_evaluation_attempt（已有） | 追加 attemptNo、冻结 ruleKey/expected hash；唯一 (assignment_id,rule_key,attempt_no)，重复 producer/结果拒绝，实际执行仍单 Task。 |
| Result/Metric/Evidence | CASE_ATTEMPT 与 ONLINE_TASK 互斥且必需字段完整，MySQL 5.7 CHECK 不作为防线；保留旧 producer 唯一键，主体维度区分命名空间，批量一次写入。 |
| Feedback（已有） | 追加线上原产物/盲态/rubric/supersedes 信息，历史字段/score/hash 不回写；修正只追加。 |
| online_experiment_report（已有）/报告请求映射 | revision/input_hash 唯一、记录不可变；(experiment,actor,key) 的独立重算/决定请求映射确保不同 key 复用同计算结果。 |

实验行锁承担 ACTIVE 检查、acceptedSequence 增长及任务/Token 预留、关门，均短事务；slot 行锁 + 实验状态检查决定准入，与 emergency stop 使用相同锁顺序（实验→槽→assignment）。不得在锁内 RPC、模型、文件读/重算。

原子接受后 reservation=本 Task 有限上限；ledger executionId 唯一结算，actual replacing reservation 一次。确认无模型执行才能释放零实际；未知不释放。模型超额使 consumedTokens 超授权仍真实记录，并触发熔断，不能把账本截到预算。

初始接受 sequence 由锁下计数递增，文档主任务 min(acceptedSequence)；不使用 createdAt/雪花 ID 推断提交顺序。范围内普通 ORIGINAL 重试不重新接受。Assignment 找不到对应 Task 时 UNKNOWN/UNCONFIRMED 不从样本删除。

槽在 Task 执行准备/派发前取，签发 generation 对应 permit；所有线上 Agent 接受/开始必须验证 permit 当前有效且 binding 匹配。旧 generation/已熔断拒绝。协议重复调用返回原槽，AgentExecution 的 Task 唯一键防重复执行；证实终态释放槽，不要求账本已知；失联只暂停查证。

V31 新装/升级须先查重复 Space 占位/请求键、旧 schema/活跃数据及主体冲突。有旧数据不自动重算/hash 升级，保持只读并输出人工向前治理清单；不能直接 DROP 数据。Schema 1 记录保持 UNSUPPORTED_ONLINE_SCHEMA，不能参与新 LIVE。V31 的具体 SQL 在 P6-01编写/隔离验证，本批不执行。

## 6. Binding、派发与内部权限

OnlineTaskBindingDTO 的 schema 固定 2，包含 assignmentId、experimentId、manifestHash、acceptedSequence、variant、bucket、actorId、taskId、spaceId、agentId、documentId、documentVersion、documentContentHash、inputSchemaVersion=1、inputHash、templateId、templateSchemaVersion=2、templateHash、nonPromptHash、dependencyHash、tokenBudget、executionTimeoutSeconds、executionMode=LIVE、lineageType=ORIGINAL、schemaVersion。

bindingHash 使用第 2 节 domain。扩展 TaskCapabilityIssueDTO、AgentTaskInputDTO、恢复身份的 onlineBindingSchemaVersion/hash；执行派发再绑定 onlineSlotPermitHash/generation，普通非参与任务四项为空，参与派发必须完整。Task 签发/派发前先取得槽证明，JWT 同时签入 bindingHash 与 permitHash/generation；重投复用同一证明，不先发模型再补槽。终态恢复携带原执行 permit 身份，只能查证/收尾，不能以已释放的槽启动新执行。

Task/Agent 保存必要身份，Agent 接受/开始时向 Evaluation 权威复核 binding、槽 generation 与 emergency 门禁。缺字段/篡改/旧 generation 拒绝，不能只校验客户端上传的配置 hash 或把令牌签名有效等同于当前准入有效。

新增内部接口固定为 /internal/online-configs（Agent）、/internal/online-assignments（Evaluation）、/internal/online-observation 与 /internal/online-cancellation（Task/Document 按领域分别承接）。common-core 统一 Feign，Gateway 禁止公共路由到 /internal/**；每服务仍校验来源与窄权限，路径隐藏不能替代鉴权。

内部动作：prepare-template、request/confirm-assignment、claim/release-slot、read-binding、observe-task/ledger/original-evidence、request-emergency-cancel。取消只针对当前实验的既存 assignment Task 集合；不允许创建 LIVE、编辑 Agent、提交/合并文档。操作留内部身份及维护者确认来源，不能伪装任务发起人。

自动观察凭证为短期 ONLINE_OBSERVE 受众，绑定 Space/实验及实际 Task 集合，仅只读；失效可续同一范围，不改 TTL 策略绕过授权。Task 普通 Capability 仍按既有 6 小时 TTL/ADR-0006 恢复，不拿观察凭证执行模型/Workbench 写工具。

Global feature 默认关闭；关闭时普通创建无分配依赖。开启时普通入口使用已有 TaskCreateDTO 增加可选 clientRequestKey；命中线上参与检查时 key 必需，null 返回 REQUEST_KEY_REQUIRED。新前端所有正常创建生成稳定 key；一次表单重试复用。客户端不能提交 variant/template/执行模式。路由查证不可用拒绝有风险创建，不当作不参与。

## 7. 生命周期与保护

CREATED→ACTIVE（取占位/依赖复核）、ACTIVE→PAUSED、PAUSED→ACTIVE（原条件/原 deadline）、CREATED/ACTIVE/PAUSED→STOPPING→STOPPED；空 CREATED stop 可以同事务到 STOPPED。STOPPED 不可恢复；状态版本每成功变更递增，重复 key 返回原结果。

pause 不接受新 assignment，非安全暂停的已接受项继续；stop 关闭分配，已接受在途可正常收敛；emergency-stop 关闭分配与执行准入并请求取消，取消授权失效仍完成关门、返回待处理原因。正常停止已在 STOPPING 可升级 emergency，不允许反向恢复。

STOPPED 需要所有接受意图/Task/slot 确证未执行或权威终态，预算结算/未知为零；评价/反馈迟到不阻塞业务收敛。未知拒绝释放占位；普通新任务始终走当前正常生产配置，与 baseline 模板未必相同。

| 保护 | 固定条件/结果 |
| --- | --- |
| BUDGET_EXHAUSTED | 新完整预留会超预算/Task 数：拒绝 + PAUSED。 |
| BUDGET_OVERRUN | 权威已用超过授权：emergency-stop，不截账本。 |
| SECURITY_EVENT/BINDING_INVALID | 权威确认安全/越权/矛盾：emergency-stop。 |
| DEPENDENCY_DRIFT/SCOPE_DRIFT | 自动 PAUSED；越权风险另熔断，文档权限失效阻止新工具动作。 |
| HEALTH_WINDOW_FAILED | 每组最近 20 个 COMPLETED/FAILED/确定超时终态；至少 6 项失败/超时暂停，不足 20 不触发，用户主动取消另列。 |
| OBSERVATION_UNRESOLVED/SLOT_UNRESOLVED | 未决持续 300 秒暂停/告警，不释放；身份矛盾立即保护。 |
| SRM_DETECTED | 第 8 节有效诊断异常，运行隔 300 秒复核仍异常暂停，完整范围预检异常直接阻止 start。 |
| WINDOW_EXPIRED | 冻结分配 deadline 到期正常 stop，继续 48 小时等已确认窗口观察。 |

当前依赖复核覆盖所有传递项，不只 Agent.configVersion。报告原始产物/审批/费用观察失败不能修改执行终态；告警去重，扫描默认 30 秒部署参数，缺有效扫描/恢复能力不可 start。

## 8. 原始规则、盲态与统计

### 8.1 新线上原始评价器（既有类型不改）

| key/schema 1 | config/expected | 输出 |
| --- | --- | --- |
| online-original-text-assertion | config={}；expected.assertions 1～50 项，每项 field=originalText、operator=CONTAINS/NOT_CONTAINS/EXACT/SHA256、value≤2000 字符（SHA256 为合法 hash） | evaluation.online-original-text.pass-ratio，NUMBER/ratio，越大越好；PRIMARY 推荐 EQ 1 |
| online-document-change-validator | config={}；expected.requiredOutputType 为 CHANGE_REQUEST/DRAFT_DOCUMENT，targetContentSha256 可选，证据必须是审批前提案/草稿 | evaluation.online-document-change.valid，BOOLEAN；结构/目标/版本/提案身份有效才 true，内容 hash 可比时另产 accuracy，NUMBER/ratio |

缺证据/不适用/规则异常为 MISSING，不产质量 0；有明确结构/内容不符合且证据完整则 false/0 有效。在线规则目录/版本及引擎能力分别检查；语法合法已发布版本不能使尚未实现的 LIVE 适配自动 startable。

执行终态为主交付前置，至少一条 PRIMARY 为原始内容/变更质量；必需期望按文档/固定规则证据目标冻结。原始证据有 TEXT/CHANGE_REQUEST/DRAFT_DOCUMENT 类型、Task/Execution、内容版本/hash、审批前捕获时间、受控引用及可读状态；接口不把正文塞进 resultSummary。

Document 在生成/更新 LIVE 提案或草稿时保存不可变原始版本/hash，Agent 在确定本次最终输出时保存原始文本身份，均先于用户审批编辑。多次工具修改保留版本链，评价选本 Task 最终提交的原始产物集合，不能任意挑最优中间版本。terminal 评价只读这些冻结引用；若审批已改动而没有先前原始证明，返回缺证据，不用审批后正文补原产物。证据实际存储经所属领域现有适配层，Shared EvidenceReference 记录引用，不增加通用正文平台。

主分析每文档 min acceptedSequence；确定失败/超时或一项有效必需规则失败为 FAIL，无 FAIL 且全部必需有效通过为 PASS，其他 MISSING。报告 PASS/(PASS+FAIL) 与 PASS/全部主体及 MISSING 旁列，不用完成行反推总体。

### 8.2 Feedback 兼容

POST /api/evaluation/feedback 继续 evaluation:run 和原 Task/Document 资源校验。EvaluationFeedbackCreateDTO 增加可选 onlineQuality 字段；原请求不含该字段继续原语义。

OnlineQualityFeedbackDTO：assignmentId、originalEvidenceId/hash、rubricScore（0～4 整数）、blindness（BLINDED/NON_BLINDED/UNKNOWN）、supersedesFeedbackId?、reasonCodes（ASCII 数组≤20）、seriousSafetyEvent（bool）、safetyReason?。taskId/executionId/Space 从实际 assignment 交叉核验，caseRunId 不可混用。

既有 score 必须等于 rubricScore/4（0、0.25、0.5、0.75、1），服务端生成/校验，历史 0～1 值不改。label 继续现有 ACCEPTED/REJECTED/NEEDS_CHANGES/NOT_APPLICABLE，不把 rubric 直接写 4 到 score。

默认首份有效记录按 createdAt + id 选定；明确更正只能指向同一 Task/原产物及合法前序，理由必需、无环、并发更正只允许一个被选后继；报告选择规则固定。已知分组的评审不能声明 BLINDED，无法确认按 UNKNOWN；首评非盲态不能通过另造首评替换。

报告分盲态/非盲态/未知、首评/更正、未产出/未评。盲态覆盖 = 选定有效 BLINDED 文档 / 全部首 assignment 文档；另列可产物覆盖。非盲态不补门槛，更正不抹原先偏差。严重事件需要授权评审及原始证据，普通低分不自动当安全熔断。

### 8.3 SRM 与置信区间

完整冻结范围 n≥100、n×w/10000 及 n×(1-w/10000) 均≥10 才启用诊断；不是精确二项算法的数学适用下限。不满足为 NOT_ENOUGH_UNITS，小样本做去重/归属、hash/同文档组、分配关联人工核查，不能报 SRM PASS。

双侧精确二项 p：q=w/10000，P(k)=C(n,k)q^k(1-q)^(n-k)，求所有 P(k)≤P(observed) 的概率之和。n=0 不生成 p；比较不靠浮点截断。阈值严格 p<0.001。runtime 可使用数值稳定实现，尾概率绝对误差≤1e-12；距离阈值≤1e-12 时用精确整数/高精度复核。参考向量使用整数分子与同一 10000^n 分母，包含不对称尾及门槛边界。

运行只对处理前首次进入的可辩护样本诊断，不能对已完成/已评后处理样本用同一检验；不可确认前提时仅告警。重复观察非独立，不声称序贯误报率控制；保存监测历史。

Clopper–Pearson 95%：x=PASS，n=PASS+FAIL；n=0 返回 null，x=0 下界 0，x=n 上界 1，其他以 Beta 分位/等价二项尾方程求上下界，绝对误差≤1e-10。报告规范十进制文本 12 位，误差验证针对舍入前值。仅各组比例描述，不用区间重叠判优。

mode=ENGINEERING 或 SRM 不足时比较结论强制 INSUFFICIENT_EVIDENCE；DESCRIPTIVE 也须每组≥30、有效覆盖≥0.8、无未解漂移/SRM/关键证据问题。humanQualityRequired=true 时每组盲态有效覆盖≥0.8，服务端拒绝不合格 ACCEPTED/普通 REJECTED。

安全 REJECTED 不受比较样本限制，但必须引用独立确认的安全证据和原因；不能用普通低质量反馈冒充。MDE、主规则、模式、人工依据及窗口不得运行后改变。结论均不推广 Agent。

统计解释参考 [NIST 精确比例区间](https://itl.nist.gov/div898/software/dataplot/refman2/auxillar/exacbici.htm) 与 [Microsoft SRM 诊断](https://www.microsoft.com/en-us/research/articles/diagnosing-sample-ratio-mismatch-in-a-b-testing/)；工程样本策略/阈值为本项目约束，非来源给出的通用保证。

## 9. ONLINE_V1 报告正文与重算

OnlineExperimentReportVO：reportType/schemaVersion、id/experimentId/revision、manifestHash/inputHash/reportHash、assignmentCutoffSequence/At、observationCutoffAt、actualWindows、analysisPlan、groups、qualityCoverage、feedbackStrata、srmDiagnostics、budgetByCurrency、resource/latencyCoverage、exceptions、limitations、decisionEligibility。

每组固定字段：rangeDocumentCount、participatingDocumentCount、assigned/created/started/terminalTaskCount、completed/failed/timedOut/userCanceled/pending/unknownTaskCount、primaryPass/Fail/MissingDocumentCount、primaryValidRatio/provenCoverage、confidenceInterval、各 ruleKey 有效/失败/不适用/缺失分母、盲态/非盲态/未知及首评/更正/未评计数、Token/成本及币种覆盖、排队/执行延迟覆盖。

选定集合是截止序号内全部 assignment，即使没 Task；报告同时冻结 Task/Execution、ledger、attempt/Result/Metric/Evidence/Feedback ID/hash 和缺失状态。账本完整及执行终态按权威来源，不从 Trace 总和推算。不配对不同文档输入，不跨币种合计。

首版线上报告显式重算，不依赖尚未补证的离线首次自动报告。相同截止集合/事实选择/计算 hash 复用同 revision，即使请求 key 不同；新增事实/反馈产生新 revision。旧内容/hash 不更新，决定绑定 revision/hash。schema未知/hash不符安全降级并拒绝决定。

缺失状态 UNKNOWN/ACCESS_DENIED/EVIDENCE_UNAVAILABLE/TRACE_EXPIRED/NOT_SAMPLED/LEDGER_UNKNOWN 分开；正文失效只能保留当时冻结汇总，不能重验内容或保证可复现。原产物、编辑幅度、审批结果各自指标，不相互覆盖。

报告附近固定说明：描述性观察不提供组间假设检验，比例区间不构成 Prompt 优劣证明，共享资源延迟不直接归因；SRM p 值是分配诊断。熔断页显示远端未确认/在途费用/已发生变更不可撤销，停止后新任务使用当前生产配置。

## 10. 稳定拒绝原因与实现门禁

保持现有 Result 业务 code；以下 detailReasonCode 不复用旧含义：REQUEST_KEY_REQUIRED/IDEMPOTENCY_CONFLICT、OWNER_REQUIRED/RESOURCE_FORBIDDEN、UNSUPPORTED_ONLINE_SCHEMA、MANIFEST_INVALID/EXPECTED_MAPPING_MISSING、ONLINE_RULE_NOT_READY、SPACE_SLOT_OCCUPIED、DEPENDENCY_DRIFT/SCOPE_DRIFT、BINDING_INVALID/SLOT_PERMIT_INVALID、ASSIGNMENT_GATE_CLOSED、BUDGET_EXHAUSTED/BUDGET_OVERRUN/LEDGER_UNKNOWN、TASK_UNCONFIRMED/EXECUTION_UNKNOWN、SLOT_UNRESOLVED、CANCEL_AUTHORIZATION_UNAVAILABLE/CANCEL_UNCONFIRMED、SRM_NOT_ENOUGH_UNITS/SRM_DETECTED、EVALUATION_ERROR/NOT_APPLICABLE、BLINDED_COVERAGE_INSUFFICIENT、REPORT_UNSUPPORTED_SCHEMA/REPORT_HASH_INVALID、EVIDENCE_UNAVAILABLE/ACCESS_DENIED。

新模块实施前依次证明：迁移/主体唯一与历史不变；完整绑定/新输入/双实例槽/预算/停止/熔断/对账；原始 LIVE 规则/盲态/报告/决定；页面权限/确认/轮询；具体样本额度的真实验收。预检不能因草案可创建就返回 startable=true。

性能按明确环境/索引/数据量/查询形状记录：P6-01 EXPLAIN，P6-02热点并发，P6-03百万 assignment 规模/报告构建，P6-05补真实页面。1 秒 p95 仅分页/报告概要读取参考目标，构建另计，不预先建设聚合平台。

## 11. P6-01 已实现边界

源码已实现第 3 节的创建、实验分页、详情、OWNER 预检和 assignment 分页五个公共端点。创建先预留操作者范围内的幂等身份，再跨服务捕获当前配置；模板双写和最终实验落库各有独立短事务。概要/详情仅返回身份、证明、参数和统计投影，不返回 Prompt、expected 正文或完整 manifest。尚未接入 Task/Execution 事实时，assignment 的 factStatus 为 UNKNOWN。

Agent 内部入口为 POST /internal/online-configs/prepare 和 GET /internal/online-configs/{experimentId}/dependency?spaceId=...，使用 common-core 的 AgentOnlineConfigFeign 直连，服务复查当前 OWNER 及相应 evaluation/agent 权限。Evaluation 的 agent-doc.online.agent-url 读取 AGENT_INTERNAL_URL，默认 http://localhost:8084；部署必须指向 Agent 内网地址。Gateway 拒绝规范化后的 /internal 及其子路径，不能将直连地址配置为公共网关。受保护 OWNER 通过 GET /api/document/spaces/{spaceId}/owner-permission 核验，平台管理员身份不能替代该角色。

当前模板冻结模型配置/参数/价格、Skill 版本/正文与目录身份、工具白名单、Prompt 和会话策略。runtimeRelease/toolRelease 此批是 Agent 执行应用服务和工具会话工厂的 class 指纹，**尚不构成完整传递依赖的发布证明**；安全分配接入前须补齐实际 Runtime、适配器、工具定义/实现及跨服务发布身份，不能直接据这两个指纹判定可执行。

新线上质量规则已注册 expected/config 契约和指标元数据，版本标记 online-contract-v2；旧 text-assertion 仍读取摘要，旧隔离规则仍执行原契约。线上规则不能绑定离线 TestCase，也不能在旧离线引擎执行。真实 LIVE 原始证据评价、报告、启动/分配/启停与窄签名授权留在后续批次。

因此 preflight.startable 固定为 false，并返回 ONLINE_EXECUTION_NOT_READY、ONLINE_RULE_NOT_READY、ONLINE_REPORT_NOT_READY；draftEligible 仅表示本批能检查的结构、范围、版本、依赖和占位条件。历史成本估算不可用时明确为空，不填零。60 秒 preflight proof 目前是只读摘要；后续 start/resume 必须执行完整权威复查，不能将该摘要作为现成启动许可。

V31 新增意图/模板/槽/动作请求基础，向前修正占位和操作者幂等唯一键，并用 MySQL 5.7 INSERT/UPDATE trigger 约束 Result/Metric/Evidence 两种主体互斥。历史 schema 1 只读且不能启动，不自动重算 JSON/hash。迁移和事务验证在隔离库完成；此实现边界不授权迁移实际业务库或启动真实实验。
