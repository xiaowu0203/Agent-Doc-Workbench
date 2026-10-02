# Task 终态恢复配置与验证

本功能落实 [ADR-0006](adr/0006-task-terminal-recovery.md)：自动恢复为主路径，人工触发为异常兜底。它不续期普通 Task Capability，不创建新的模型执行，也不绕过文档审批。默认关闭。

## 配置

配置由环境变量或被忽略的本地配置提供；不要把密钥写进 Git、命令行参数、聊天、日志或验收记录。

| 配置 | 服务 | 默认与约束 |
| --- | --- | --- |
| `TASK_RECOVERY_ENABLED` | Task、Agent、Document | `false`；分别控制协调和两个恢复接收端 |
| `TASK_RECOVERY_MACHINE_KEY` | Auth、Task | 空；两端使用同一专属随机密钥，至少 32 字节，不复用用户 JWT 或其他配置密钥 |
| `TASK_RECOVERY_PREVIOUS_MACHINE_KEY` | Auth | 空；正常轮换的旧密钥 |
| `TASK_RECOVERY_KEY_ROTATED_AT` | Auth | 空；轮换生效的 UTC ISO-8601 时间，旧密钥仅在其后 300 秒内接受 |
| `TASK_RECOVERY_AUTH_URL` | Task | `http://localhost:8081`；恢复签发内部直连地址 |
| `TASK_RECOVERY_AGENT_URL` | Task | `http://localhost:8084`；既有 A2A Task 查询/取消内部直连地址 |
| `TASK_RECOVERY_DOCUMENT_URL` | Task | `http://localhost:8082`；既有草稿收尾内部直连地址 |
| `TASK_RECOVERY_TRUSTED_ENCRYPTED_NETWORK` | Auth、Task、Agent、Document | `false`；仅在运维确认具有等价加密保护的内部网络时开启 |

非 loopback 连接使用 HTTPS 或等价加密内部网络。`trusted-encrypted-network=true` 是部署方对传输保护的明确确认，不是“允许任意 HTTP”，也不替代专属机器密钥/签名授权。接收方不以服务名、来源 IP、普通用户 JWT 或请求中的转发头建立恢复身份。公共 Gateway 拒绝内部恢复路径，运维仍应限制服务端口的外部访问。

专用 Auth/Document Feign 客户端固定连接超时 2 秒、响应超时 5 秒，日志级别 `none`；A2A 恢复客户端同样受 2/5 秒上限约束。整轮处理预算 45 秒，小于既有 55 秒锁租期。不得扩大客户端超时、启用请求/响应或凭证日志。对账继续采用 60 秒间隔和 120 秒心跳阈值，按主键循环扫描；积压超过单批容量会输出 `RECOVERY_CAPACITY_EXCEEDED` 去重告警。

Auth 的 RSA 签名密钥以及 Task/Agent 的既有加密密钥必须在重启后保持可用。开发环境若依赖 Auth 自动生成的临时 RSA 密钥，重启后旧签名证明可能无法验证；恢复会拒绝，不能靠人工按钮绕过。跨日重启验收须使用稳定签名密钥。

## 轮换与紧急关闭

正常机器密钥轮换：Auth 配置新的当前密钥、原密钥作为 previous 和切换时间，随后 Task 切换到新密钥；五分钟重叠期结束后清空 previous。泄露时立即清空旧密钥，不等待重叠期。

紧急隔离时同时停止签发并关闭接收端：Auth 清空当前/旧机器密钥，Task/Agent/Document 设置 `TASK_RECOVERY_ENABLED=false`。仅关闭签发不能立即撤销已经签出的短期凭证；接收端开关用来拒绝其剩余有效期。配置按现有服务启动方式生效，不宣称支持热更新。

## 人工诊断

- `GET /api/task/tasks/{id}/recovery-status`：登录人类用户、Task 所属 Space 的 `task:read`；只读返回当前 Task 状态、配置可用性、稳定原因码和最新脱敏事件。
- `POST /api/task/tasks/{id}/recovery`：同 Space `task:read + task:terminate`；LIVE 草稿额外需要 `document:edit`。不提交 JSON 载荷，不传状态、远端 ID、凭证或取消命令。

人工触发和自动对账共用 owner-token 锁、验签和资源校验。只有 Task 已经为 `CANCELING` 才能请求取消，远端仍运行时不能伪造终态。草稿冲突、原证明不可验证或归属不一致，需要修复实际原因后重试，不能强制覆盖。

恢复原因和尝试记录在现有 `audit_log` 中，不新增恢复状态表。服务主体使用 `actorType=3`、保留 `actorId=0` 和 `service=task-service`；人工触发记录真实用户身份。事件包含 recoveryId、Task/Space/A2A 身份、动作、时间、原因和既有 Trace 关联，不包含密钥、证明、JWT 或业务正文。

## 验证门禁

源码测试覆盖签名/冻结身份、机器密钥轮换、两种用途隔离、网关和传输门禁、权限、版本冲突、锁接管、终态保护与事务回滚。Agent 使用既有加密 A2A 历史的初始输入与 AgentExecution 交叉核验；输入缺失或歧义时拒绝，不补造输入。响应去掉携带原凭证的输入历史。

新 A2A Task 必须在业务执行前发布携带原始 Message 的 SUBMITTED 事件，并经正常异步事件链路加密持久化；只验证手工构造的 TaskManager 不能证明此路径。升级不会修复旧记录的空历史，既有任务续发和取消也不补写输入；真实恢复验收应使用升级后创建且冻结身份完整的样本。

真实验收仍须在测试环境执行，不能把模拟成功当作真实故障演练：

1. 使用合法已过期的原证明，并保留本地活动 Task、既有 A2A ID 与完整冻结身份。通过受控测试环境制造回调丢失，不伪造签名或直接强改业务终态。
2. 验证远端完成、取消和仍运行，以及 LIVE 草稿提交/丢弃、版本冲突和 ISOLATED 无文档副作用。
3. 验证跨日重启、重复/并发对账、锁过期接管和签发/Agent/Document 不可用；故障解除后可继续恢复。
4. 同一 execution 的账本、同一来源产物和同一 Task 的草稿版本不得重复；已终态 Task、Run、Experiment 不被覆盖。
5. 在依赖健康、无冲突且容量满足验收负载时，首次发现后五分钟内收敛；异常在五分钟内有去重告警和诊断。真实无法满足该前提时记录原因，不宣称 SLA 达标。

只保存非敏感身份、原因、计数和耗时，不把请求/响应原文、原凭证或恢复凭证保存为证据。

### 验证分层与已知边界

验证记录必须区分部署故障演练、真实依赖集成和隔离组件测试，不把它们合并表述为全链路演练：

- 部署演练已覆盖原证明自然过期后的 LIVE 提交/丢弃/冲突、ISOLATED 产物、跨日 Task 重启、Agent/Document 不可用、告警去重和人工并发幂等。
- `TaskRecoveryDeploymentIntegrationTest` 使用真实 MySQL、Redis、Auth、Agent、Document 与人类登录/权限。仅在真实查询返回后用线程屏障控制时序，验证 AUTO/MANUAL 争用、55 秒租约自然到期接管、旧 owner 不再执行写回且不能删除新 owner 的锁。样本为既有过期 LIVE 版本冲突任务，正常结果是拒绝覆盖；本项不声称完成成功终态写回。
- `A2aTaskRecoveryServiceTest` 的异步用例使用真实 SDK 事件链、生产 Mapper/XML、H2 持久化、AES 编解码及 RSA 签名/验签，验证 WORKING 查询、CANCELED 收敛、重复取消和输入历史保护；执行档案、用量查询和模型执行仍为测试替身，来源原证明为测试 fixture，不等于部署 Auth 的自然过期原证明。

部署集成测试默认不运行。显式设置 `P5_RECOVERY_DEPLOYMENT_INTEGRATION=true`、`P5_RECOVERY_TASK_ID`、`P5_RECOVERY_OWNER_FILE`，并通过进程环境提供 `MYSQL_PASSWORD`、`TASK_CAPABILITY_KEY`、`TASK_RECOVERY_MACHINE_KEY` 后，才能在已授权本地测试环境运行。测试会产生真实恢复审计/告警；不会创建 Task、调用模型、改原证明、改草稿或手工缩短/删除锁。必须选用冻结身份完整、原证明已过期且暂存基线真实冲突的专用样本；先确认依赖健康及既有调度器运行情况。

当前正常 Agent 执行时限上限为 3600 秒，本轮原 Task Capability 有效期为 21600 秒，因此没有通过正常执行制造原证明过期时仍运行的部署样本。异常运行超过 6 小时后的远端查询/取消完整部署演练**未执行**。维护者已明确接受上述分层验收作为 P5-01 收口方式；这不放宽运行时权限、验签或终态保护契约，不代表异常长运行绝不发生，也不声称该场景的部署 SLA 已获证明。需要该强度的环境验收时另行授权专用异常环境和自然过期样本。
