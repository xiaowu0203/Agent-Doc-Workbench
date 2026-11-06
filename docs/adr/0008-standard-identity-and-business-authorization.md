# ADR-0008：OIDC 身份认证与业务授权分域

- 状态：提议（维护者于 2026-11-04 确认单实例 Keycloak、薄 Gateway BFF、业务服务授权及可选 local 账号模式；本 ADR 其余新契约待整体评审）
- 适用版本：拟从 v0.3.0 开始
- 关联：[现状评估](../designs/auth-oidc-analysis.md)、[详细设计](../designs/v0.3.0-identity-security-design.md)、ADR-0001 至 ADR-0007

## 背景

v0.2.0 使用自定义密码登录/Refresh 协议，Auth 签发用户与多种业务 JWT。Resource Server 已采用 Spring Security，但用户 Token 缺少明确 API audience，通用链默认放行，部分内部签发入口只要求 authenticated。Task、Worker、线上保护和恢复又依赖不同的业务范围及历史证明，不能简单替换成一个宽权限服务 Token。

## 决策提案

1. oidc 模式由单实例 Keycloak 承担身份账号、登录、密码策略、MFA、SSO 及 OAuth 令牌签发/吊销；开发/测试用内置 H2，正式持久部署用独立 PostgreSQL。只设一个应用 realm，保留必要客户端和声明，不建设集群、复杂联邦或业务权限 mapper。
2. Gateway 是薄 BFF，仅做会话/Cookie/CSRF、Code + PKCE、授权码换 Token、刷新、登出转发和受控凭据转发，不向浏览器返回 Access/Refresh Token，不判断租户/Space/Capability/Skill/ACL 权限。首版单 Gateway，不承诺多实例 HA。
3. 所有业务权限由对应业务服务裁决，包括租户成员、平台角色、Space RBAC、Capability、Skill、资源 ACL 和审批。OIDC 用户通过精确 issuer/sub 映射稳定本地 user.id；本地角色不从 Keycloak realm role 推导。OAuth scope 仅约束协议/API 用途，不代替资源授权。
4. Auth 保留业务签发域，使用独立 issuer/密钥/验证器；签发从已提交的 Task/Run/集合权威证明推导范围，不照抄任意请求 actions。
5. 正常用户 API 不接受旧用户 JWT；旧业务凭证仅限预登记历史记录、专用验证域和截止时间。过期历史证明只用于既有恢复签发，不用于在线 API。
6. BFF Session 与 AuthorizedClient 持久化并保护敏感值；验证单实例重启、会话固定、CSRF、多标签并发刷新、绝对超时和注销竞争；不提前建设分布式刷新锁。
7. 不要求所有业务凭证本版改为 Token Exchange，也不建设 SAS 第二实现。范围和时间策略以评审后的详细设计及实际验收为准。
8. 增加显式 local 模式，Auth 提供基本账号密码认证，复用业务 MySQL/Redis，不依赖 Keycloak/PG。两模式共用会话形状、稳定 user.id 和业务授权代码；local 不另建 OIDC Provider、MFA 或 SSO。内部人类/服务证明在 P0 冻结，仍须用途分离和最小权限，不恢复旧的任意签发。
9. 部署配置选择模式并重启生效；oidc 使用独立 Client Credentials，local 使用受限服务凭据及证明。每次只接受当前模式的人类/服务凭据，不自动故障回退，不按邮箱/同名迁移账号。模式切换须准备目标账号、撤销旧模式会话并验证业务身份不变。

实施顺序补充：为不等待目标模式迁移而放任已复现缺口，P0先在3个已知入口采用独立Task HTTP Basic凭据直连，公共网关拒绝转发；具体边界见[设计7.1](../designs/v0.3.0-identity-security-design.md#71-p0过渡止损与目标契约的区别)。它是过渡止损，不是第二套身份产品；P1/P3接入目标服务Token时替换，不永久同时接受Basic/OAuth，也不因此省略资源权威证明。

## 对既有 ADR 的影响

| ADR | 影响 |
| --- | --- |
| 0001–0005 | 不改 Run 身份、Replay 副作用隔离、快照/hash、审计/账本及离线 Experiment 语义 |
| 0006 | 拟替换恢复签发的机器身份为当前模式的受限服务证明，及独立业务签名域；oidc 使用 OAuth2 客户端；历史证明、用途、300/60 秒、资源校验和人工兜底不变 |
| 0007 | 拟替换基础服务认证/签名信任域；真实 OWNER、CONTROL/WAIT、权威槽、冻结 binding、盲评及停止语义不变 |

本 ADR 整体采纳后，0006/0007 的身份技术选择由此向前补充，不回写历史已采纳正文隐藏变更。协议 hash/schema 不因新增认证字段悄悄改变；确有载荷变化时显式版本化。

## 取舍

- oidc 新增单实例身份服务/数据库的部署、备份和升级，换取成熟协议和凭据生命周期；local 保留简易账号维护成本，换取无外部 IdP 的部署入口。
- 两种认证入口增加配置、账号切换和回归成本；只在认证适配处区分模式，不复制业务权限规则，也不同时开放两套登录协议。
- BFF 增加会话和 Token 存储职责，减少浏览器令牌处理；Redis 故障会影响用户入口，不能因此弱化服务端认证。
- 本地映射维持业务 ID 稳定，增加绑定/状态解析的可用性与缓存约束。
- 人类 API 可使用明确应用资源组 aud；内部域和业务用途单独限制，不强制每个微服务进程对应一个人类 aud。
- 暂保留业务 Capability 内部协议，避免破坏冻结输入、审批和恢复；它仍是受治理的业务授权，而非第二套用户登录。

## 采纳前的必要验证

- 锁定 Keycloak/H2/PG/Spring 兼容版本，验证单 realm 最小声明、真实 code/PKCE/回调及单 Gateway 持久化恢复。
- 无 Keycloak/PG 时 local 可完成用户及后台全链路；模式切换/错误配置/跨模式 Token 拒绝，两模式业务权限负例一致。
- 明确账号供应/绑定、超时、remember、禁用 SLA 和旧证明截止。
- 签发证明在各创建事务提交后可读，不引入循环授权调用或跨域 Mapper。
- 全入口主体分类和授权负例、老任务收尾与紧急密钥撤销演练。
- Keycloak 吊销由应用通过已冻结的检查/通知机制执行，离线 JWT 验签不承诺即时感知；7 天 Access 既定政策不因本次范围调整而静默改变。

“选型已确认”“设计已采纳”“实现通过验证”“已部署”是不同状态，不互相替代。
