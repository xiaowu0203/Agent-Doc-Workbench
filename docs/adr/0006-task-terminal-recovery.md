# ADR-0006：Task 终态自动恢复与人工兜底

- 状态：已采纳
- 日期：2026-09-29
- 适用版本：v0.2.0 起
- 关联：[ADR-0001](0001-run-compatible-execution-model.md)、[ADR-0002](0002-replay-side-effect-isolation.md)、[ADR-0003](0003-telemetry-audit-ledger-boundaries.md)

## 背景

Task Capability 的有效期为 6 小时。远端 A2A/AgentExecution 已终态、回调丢失且原凭证过期时，常规定时对账无法继续查询远端，本地 Task 可能保持 `RUNNING/CANCELING`，连带 EvaluationRun/Experiment 无法结束。

现有终态同步还承担 LIVE 草稿提交/丢弃、Token 权威账本和隔离产物记录。草稿收尾使用原 Capability；仅解决远端查询鉴权仍会在凭证过期后阻塞 LIVE 草稿收尾。现有 A2A 对账锁按 Task ID 设置后直接删除，也需要补齐 owner-token 安全释放。

## 决策

采用方案 A：由经过认证的 task-service 自动恢复同一个已存在 Task 的远端终态。方案 B 作为异常时的去重告警、只读诊断与受审计人工重试入口；人工操作也遵守相同身份校验和最小权限，不提供手填终态或直接修改数据库的途径。

本 ADR 冻结待实现契约；“已采纳”表示设计决策成立，不表示恢复代码、跨服务安全测试或真实故障演练已经完成。

### 1. 恢复触发与信任边界

自动恢复同时满足以下条件：

1. task-service 重新读取数据库确认 Task 仍处于现有远端活动态，且已有 A2A Task ID。
2. 常规凭证已经过期。网络故障、签名错误、身份不匹配不能被等同于过期而触发放宽鉴权。
3. 原加密 Capability 能受控解密，原签名和冻结 Task/Space/Agent/Document、执行模式、文档版本/内容 hash、input schema/hash、可选派生请求 hash 均可核验。
4. Agent 端存在同一 A2A Task/AgentExecution 关联。远端不存在、出现多义关联或冻结身份不一致时停止自动写回并告警。

auth-service 是唯一凭证签发者，task-service 是唯一恢复签发请求者。首版使用专属机器密钥认证：由部署环境向 auth-service 和 task-service 注入相同的至少 32 字节随机密钥，默认空值、空值时关闭签发；auth-service 恒定时间比较凭证，固定绑定 `task-service` 身份，不信任调用方自行声明的服务名、来源 IP 或普通用户 JWT。

签发接口仅允许内部网络访问，task-service 直接调用 auth-service，不经公共 Gateway；Gateway 对该内部路径显式拒绝转发。非本机传输必须使用 HTTPS 或提供等价加密的受信服务网络。本机开发允许 loopback HTTP，不能据此允许远程明文传输机器密钥。该密钥只用于恢复签发，不能用于登录或通用 Task Capability 签发。

原 Capability 可以在专用签发校验中作为**历史授权证明**接受已经过去的 `exp`；仍必须核验算法、签名、issuer、原 audience、actor/scope、必填声明与 `nbf/iat`，拒绝尚未生效或未来签发证明。该校验不进入通用 `JwtDecoder`、`TaskCapabilityVerifier` 或用户请求过滤链，所有正常请求继续拒绝过期 Token。

auth-service 以原签名证明限定冻结范围，以经过机器认证的 task-service 数据库复查结果限定当前 Task/A2A 绑定；不读取当前 Agent 配置、当前文档正文或请求中的任意 action 来扩大原授权。受信 task-service 对本地活动态和远端终态事实负责，Agent/Document 接收方仍做独立资源核验。

### 2. 两类独立恢复凭证

恢复 JWT 使用现有 auth-service RSA 签名及 JWKS，必须带有效签名、严格匹配的 issuer/audience、`iat/nbf/exp/jti`、`actorType=SERVICE`、`scope=service`、`service=task-service`。恢复声明使用独立 `purpose/recoveryActions`，不能借 `agentActions` 获得普通 Agent 能力。

| 凭证 | audience | purpose | 允许动作 | 有效期 |
| --- | --- | --- | --- | --- |
| A2A 恢复 | `agent-service-task-recovery` | `TASK_TERMINAL_RECOVERY` | `QUERY_EXISTING_A2A_TASK`；本地已请求取消时才可附加 `CANCEL_EXISTING_A2A_TASK` | 固定最长 300 秒 |
| LIVE 草稿收尾 | `document-service-task-finalization` | `TASK_DRAFT_FINALIZATION` | 远端完成对应 `FINALIZE_EXISTING_TASK_DRAFT`；失败/取消对应 `DISCARD_EXISTING_TASK_DRAFT`，二者互斥 | 固定最长 60 秒 |

两类凭证均绑定 `taskId/spaceId/agentId/documentId/executionMode/a2aTaskId`、原冻结文档与输入身份、原凭证 `jti` 和本次恢复关联 ID。草稿凭证另绑定 task-service 已从 Agent 受控查询取得的 `remoteTerminalStatus`。TTL 和动作集合为领域常量，客户端不能覆盖。

凭证仅存于内存和专属恢复请求头，不能回填 `task.capability_token`，不返回浏览器，不写日志、快照、审计正文或 Redis 值。恢复调用不能把凭证塞入通用 `AuthorizationContext/TaskCapabilityContext`；缺少窄权限时不能回退到普通 Agent JWT。

### 3. 接口与接收方校验

以下路径是恢复专用契约；复用现有应用服务与同步规则，避免改变正常 A2A/Workbench MCP 的授权语义：

| 入口 | 授权与作用 |
| --- | --- |
| `POST /api/auth/internal/task-recovery-capabilities` | 专属 task-service 机器密钥 + 原签名证明 + 当前冻结身份，签发 A2A 恢复凭证 |
| `POST /api/auth/internal/task-draft-finalization-capabilities` | 同上，并核验 LIVE、原 `WRITE_DRAFT` 授权及合法远端终态，签发单一草稿收尾动作 |
| `GET /api/agent/internal/a2a/tasks/{a2aTaskId}/recovery` | A2A 恢复凭证；验证路径 ID 及 AgentExecution 中全部可验证的 Task/Space/Agent/A2A/模式/输入身份，查询同一既有 A2A Task |
| `POST /api/agent/internal/a2a/tasks/{a2aTaskId}/recovery-cancel` | 同上，额外要求取消动作，只取消该既有 Task，不创建执行 |
| `POST /api/document/internal/task-drafts/{taskId}/finalize` | 草稿凭证；从声明解析 Document/Space 和终态，不接受正文或替代文档 ID；核验暂存归属后提交/丢弃 |
| `GET /api/task/tasks/{id}/recovery-status` | 人类用户 + 同 Space `task:read`，读取脱敏诊断 |
| `POST /api/task/tasks/{id}/recovery` | 人类用户 + 同 Space `task:read` 与 `task:terminate`；触发一次相同恢复流程，不接受远端 ID、终态、凭证或任意动作载荷 |

人工恢复若将提交/丢弃 LIVE 草稿，还需同 Space `document:edit`；在产生收尾副作用前校验，缺少权限不能部分执行。用户可先查看诊断。人工恢复只查询当前权威状态；只有已经记录的 `CANCELING` 意图才触发取消，恢复按钮本身不追加取消意图。

恢复 JWT 即使被放入普通 Authorization、X-Task-Capability 或其他头，也不能进入用户业务 API、正常 A2A send、MCP、任意 Task 列表/上下文读取或通用文档写入口。专用 verifier 必须检查 audience/purpose/动作/资源/时间，入口范围默认拒绝；不能只验证签名后把 SERVICE 当作 Agent 或用户。

正常用户访问入口仍经 Gateway，机器签发与 Agent/Document 恢复入口直接走受信内部连接。公共 Gateway 默认拒绝以上 `/internal/` 恢复路径；普通用户 JWT 与 Evaluation Worker JWT 均不能用于这些入口。

Agent 端返回的 A2A 数据仅供 task-service 既有转换与同步使用，不传给浏览器。状态缺失、终态映射不受支持或 payload 身份不一致时拒绝写回。远端仍活动时保持本地活动态并继续正常对账，不能为达到恢复时限伪造失败/取消。

实现中，AgentExecution 的配置快照不包含全部业务输入字段；接收方用既有加密 A2A 历史的唯一初始输入补充模式、文档基线和输入 hash，并与 AgentExecution 的 Task/Agent/Space/A2A 关联及原证明 jti 交叉核验。历史输入缺失或有歧义时 fail-closed，不推断、不补造；输入历史不返回调用方。文档初始版本 v0 是合法冻结基线。

新 A2A Task 在通过入口授权后、业务执行前，先发布包含原始请求 Message 的 SUBMITTED Task 事件；后续状态和产物事件沿用 SDK 同一任务事件队列，使初始输入随既有 TaskStore 加密保存。既有 Task 的续发请求不重新初始化历史，取消入口也不补写；缺少初始输入的历史记录继续拒绝恢复，不从当前配置或请求回填。

### 4. LIVE 草稿收尾与隔离

草稿凭证只在可信远端终态已经取得后签发，且原签名证明具有 `WRITE_DRAFT`。缺少原权限、文档不再是草稿、属于其他 Space、暂存属于另一 Task 或版本存在冲突时停止收尾并转人工处理。

document-service 使用已有 `agent_staged_task_id`、`agent_staged_base_version` 与当前版本条件更新：

- 完成：只提交该 Task 已有暂存，遵守 `baseVersion` 乐观锁，按现有版本规则生成 `AGENT_DRAFT` 快照。
- 失败/取消：只丢弃该 Task 已有暂存。
- 无暂存：幂等 no-op；已提交的版本通过 `DocumentVersion.sourceTaskId` 关联核验，不重复产生版本，不把当前其他 Task 暂存解释为本次产物。
- 冲突：返回稳定 `DRAFT_VERSION_CONFLICT`，保留冲突证据与待处理状态；人工恢复也不能自动覆盖或强制丢弃他人暂存。

恢复入口不接受新正文、不创建新暂存、不写正式文档或 ChangeRequest。ISOLATED Task 永不申请草稿收尾凭证，不调用文档提交/丢弃接口，继续遵守 ADR-0002 四层隔离。

机器密钥只信任 task-service 的恢复协调权；草稿终态断言也属于该明确的信任范围。其泄露或 task-service 被攻陷仍是风险，必须通过密钥撤销、内部网络控制和审计识别处理，不能声称 audience 校验能消除受信服务被攻陷的风险。

### 5. 幂等、重放与并发

自动与人工恢复使用同一个单 Task Redis 锁，每次生成唯一 owner token，释放使用 `deleteIfValueMatches`；锁过期后旧 owner 不得删除新 owner 的锁。所有耗时远程调用设置连接/读取超时，总处理预算小于锁租期；超时中止本轮，不能在丢失锁所有权后继续提交本地终态。

Redis 锁用于减少重复工作。数据库仍通过 Task 当前活动态、冻结身份及既有唯一键保护终态、Token 账本和 Artifact；重复或过期回调不得覆盖已经终态的 Task，也不得切换 AgentExecution 关联。

恢复 query/cancel 的重复请求仅在短期凭证绑定的同一 Task 内幂等执行。草稿收尾通过暂存归属、版本条件更新和 sourceTaskId 确保重复请求不会生成第二个版本。不得用“消费一次 jti”阻止安全网络重试，也不得仅凭 Redis 消费记录代替业务数据库幂等。

顺序固定为：复查本地身份 → 受控读取/必要取消远端 → 核验远端身份与终态 → 必要的幂等草稿收尾 → 短本地事务回填 Task、执行关联、Token 账本和 Artifact。远程查询与草稿调用不放入长期本地数据库事务；复用现有领域规则和写入服务，不建立第二套终态状态机。

草稿成功后本地写回失败可重复执行；未成功完成必要收尾及账本/产物回填时，不能提前把本地 Task 记为已恢复终态。已有 Run/Experiment 终态不被恢复流程覆盖，其后续对账继续从原正式关系读取 Task 事实。

### 6. 响应目标、告警与人工兜底

- 默认扫描间隔 60 秒，心跳过期阈值 120 秒，保持现有对账配置。
- 在依赖健康、原证明可验证、无草稿冲突、远端已终态且扫描处理容量满足验收负载时，从**首次发现需恢复**起 5 分钟内自动回填该 Task 的完整终态。这个目标不包含模型仍运行的时间。
- 发现签发失败、身份不匹配、草稿冲突、锁/处理容量异常或依赖持续不可用后，5 分钟内输出首次去重告警和只读诊断。容量不足必须作为明确原因告警，不能继续宣称时限达标。
- 同 Task + 原因码首次告警、原因变化与连续 3 次失败阈值各记录一次；常规重试不逐次告警，持续异常按小时汇总。
- 身份/签名/范围不匹配及草稿冲突直接要求人工处理；瞬时依赖故障继续有界周期重试，达到阈值时提示人工介入。人工修复后可触发同一恢复流程。
- auth/Agent/Document 或存储不可用时不承诺固定时间最终收敛。依赖恢复且仍满足范围/数据条件后恢复重试，保留已发生的告警与审计。

稳定原因码至少覆盖 `CAPABILITY_EXPIRED`、`RECOVERY_SERVICE_UNCONFIGURED`、`SOURCE_CAPABILITY_INVALID`、`RECOVERY_IDENTITY_MISMATCH`、`RECOVERY_ISSUANCE_FAILED`、`REMOTE_TASK_UNAVAILABLE`、`REMOTE_TASK_ACTIVE`、`DRAFT_VERSION_CONFLICT`、`RECOVERY_CAPACITY_EXCEEDED`、`RECOVERY_WRITEBACK_FAILED`。诊断状态从审计和当前 Task 派生，不新增业务 Task 状态或独立恢复执行表。

### 7. 审计、密钥轮换与验证

每次恢复动作记录：恢复关联 ID、Task/Space/A2A Task ID、触发类型 AUTO/MANUAL、动作、开始/结束时间、结果/稳定原因码、Trace ID；人工动作另记录真实用户 ID。签发记录 service、purpose、Task 与结果；不记录机器密钥、原证明、恢复 JWT、Prompt、正文、工具参数、模型输出或远端完整 payload。

机器密钥支持当前/前一密钥短期重叠轮换，正常切换重叠窗口为 5 分钟；泄露时立即撤销旧密钥，不等待窗口。JWT 签名轮换沿用 auth-service/JWKS，验证旧签名所需公钥无法取得时 fail-closed；人工恢复也不能跳过验签。已签发恢复凭证最长 5 分钟到期，紧急隔离通过关闭签发和禁用恢复接收入口阻止剩余有效凭证继续使用。

安全设计评审结论：只有专属机器身份可签发；历史证明不作为在线鉴权；两种 audience/动作隔离；接收方再次核验资源；LIVE 收尾受原授权、暂存归属和版本冲突约束；ISOLATED 无文档副作用；失败转人工且不能绕过校验。维护者已选择 A 并保留 B 兜底，以下验证是实施出口，尚未执行：

1. 原证明过期可恢复；错误签名/issuer/audience/未来时间/冻结身份不匹配均拒绝。
2. 伪造服务名、普通用户/Worker 凭证、错误机器密钥、未配置密钥均拒绝签发；Gateway 不暴露机器入口。
3. 两种恢复凭证不能交叉使用，不能调用 A2A send、MCP、通用文档写或其他 Task。
4. 回调丢失、跨日重启、远端运行/完成/取消、重复与并发恢复、锁过期接管、终态保护。
5. Token/Artifact 幂等，LIVE 草稿提交/丢弃幂等、版本冲突不覆盖，ISOLATED 无草稿调用。
6. 5 分钟目标在记录的容量/超时条件下达标，依赖故障不伪造终态，告警去重和人工权限/审计完整。

## 未采用方案

只用告警与人工恢复：保留作异常兜底，不能承担正常跨日任务的无人值守恢复目标。

重新签发完整 Task Capability：拒绝，它会重新开放普通执行与写工具权限。

全局忽略原 JWT 过期，或根据 AgentExecution 状态直接改 Task：拒绝，前者破坏在线鉴权，后者遗漏 A2A 身份、草稿收尾和账本/产物一致性。
