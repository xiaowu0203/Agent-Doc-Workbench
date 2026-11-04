# ADR-0008：OIDC 身份认证与业务授权分域

- 状态：提议（维护者已于 2026-11-02 确认 Keycloak + Gateway BFF 选型；本 ADR 其余新契约待整体评审）
- 适用版本：拟从 v0.3.0 开始
- 关联：[现状评估](../designs/auth-oidc-analysis.md)、[详细设计](../designs/v0.3.0-identity-security-design.md)、ADR-0001 至 ADR-0007

## 背景

v0.2.0 使用自定义密码登录/Refresh 协议，Auth 签发用户与多种业务 JWT。Resource Server 已采用 Spring Security，但用户 Token 缺少明确 API audience，通用链默认放行，部分内部签发入口只要求 authenticated。Task、Worker、线上保护和恢复又依赖不同的业务范围及历史证明，不能简单替换成一个宽权限服务 Token。

## 决策提案

1. Keycloak 承担 OIDC Provider/OAuth2 Authorization Server；Gateway 以授权码 + PKCE 作为机密客户端，并通过 BFF Cookie 会话服务浏览器。不给浏览器返回 Access/Refresh Token。
2. 业务服务继续作为 Resource Server 验证标准 Access Token。用户通过精确 issuer/sub 映射本地 user.id；保留本地状态、平台角色、Space RBAC、文档审批和历史审计。
3. 服务用独立 Client Credentials 客户端访问明确 internal aud/scope；人类委托、机器身份和 Task 能力分开，不信任任意用户 ID 头。
4. Auth 保留业务签发域，使用独立 issuer/密钥/验证器；签发从已提交的 Task/Run/集合权威证明推导范围，不照抄任意请求 actions。
5. 正常用户 API 不接受旧用户 JWT；旧业务凭证仅限预登记历史记录、专用验证域和截止时间。过期历史证明只用于既有恢复签发，不用于在线 API。
6. BFF Session 与 AuthorizedClient 都要多实例持久化并保护敏感值；会话固定、CSRF、并发刷新、绝对超时和注销竞争必须测试。
7. 不要求所有业务凭证本版改为 Token Exchange，也不建设 SAS 第二实现。范围和时间策略以评审后的详细设计及实际验收为准。

## 对既有 ADR 的影响

| ADR | 影响 |
| --- | --- |
| 0001–0005 | 不改 Run 身份、Replay 副作用隔离、快照/hash、审计/账本及离线 Experiment 语义 |
| 0006 | 拟仅替换恢复签发的机器密钥身份为受限 OAuth2 服务客户端，及独立业务签名域；历史证明、用途、300/60 秒、资源校验和人工兜底不变 |
| 0007 | 拟替换基础服务认证/签名信任域；真实 OWNER、CONTROL/WAIT、权威槽、冻结 binding、盲评及停止语义不变 |

本 ADR 整体采纳后，0006/0007 的身份技术选择由此向前补充，不回写历史已采纳正文隐藏变更。协议 hash/schema 不因新增认证字段悄悄改变；确有载荷变化时显式版本化。

## 取舍

- 新增身份平台部署/备份/升级，换取成熟协议和凭据生命周期维护边界。
- BFF 增加会话和 Token 存储职责，减少浏览器令牌处理；Redis 故障会影响用户入口，不能因此弱化服务端认证。
- 本地映射维持业务 ID 稳定，增加绑定/状态解析的可用性与缓存约束。
- 人类 API 可使用明确应用资源组 aud；内部域和业务用途单独限制，不强制每个微服务进程对应一个人类 aud。
- 暂保留业务 Capability 内部协议，避免破坏冻结输入、审批和恢复；它仍是受治理的业务授权，而非第二套用户登录。

## 采纳前的必要验证

- 锁定 Keycloak/数据库/Spring 兼容版本，真实 code/PKCE/回调及双实例持久化验证。
- 明确账号供应/绑定、超时、remember、禁用 SLA 和旧证明截止。
- 签发证明在各创建事务提交后可读，不引入循环授权调用或跨域 Mapper。
- 全入口主体分类和授权负例、老任务收尾与紧急密钥撤销演练。

“选型已确认”“设计已采纳”“实现通过验证”“已部署”是不同状态，不互相替代。
