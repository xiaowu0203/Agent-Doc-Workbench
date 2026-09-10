# 鉴权与安全方案

## 用户登录流程（v0.1 账号密码）

```
浏览器
  → 工作台账号登录页
  → Auth Service 校验用户名和密码
  → 响应正文返回 Access Token；Refresh Token 写入 HttpOnly Cookie
  → 携带 Access Token 调用 Gateway
  → Gateway 校验 JWT → 路由到业务服务
  → 业务服务 Resource Server 再次校验 JWT
  → Controller 的 @PreAuthorize 校验接口权限
```

OAuth2 / Authorization Code + PKCE 登录能力尚未实现；前端保留 OAuth2、第三方登录和找回密码入口作为产品界面占位，点击后明确提示“即将支持”，不会发起伪认证请求。

## 外部 Agent 接入流程（Client Credentials）

```
外部 Agent / MCP Client
  → OAuth2 Client Credentials
  → 获取 Agent 专属 Access Token
  → 携带 Agent Token 调用 Gateway API
  → Gateway 校验 JWT → 鉴权（scope + 角色 + 空间成员关系）
  → 业务服务再次校验
```

## Token 配置

| 参数 | 值 |
| ---- | --- |
| 签名算法 | RSA（建议 RS256） |
| 密钥管理 | JWK Set 分发公钥，Gateway 与业务服务通过 JWK Set 获取 |
| Access Token 有效期 | 30 分钟 |
| Refresh Token 有效期 | 7 天 |
| Access Token 存储 | 仅浏览器内存，不放 localStorage |
| Refresh Token 存储 | HttpOnly + SameSite=Strict Cookie；生产环境开启 Secure |

生产部署必须启用 `prod` Profile：

```text
SPRING_PROFILES_ACTIVE=prod
```

`auth-service` 的 `application-prod.yml` 会将 Refresh Token Cookie 固定配置为 `Secure` 和 `SameSite=Strict`。这要求外部访问入口使用 HTTPS；本地默认配置仍保留 `Secure=false`，以支持 `http://127.0.0.1` 开发调试。

## 前端安全规则

- Access Token 只在内存中持有，不写入 `localStorage` 或 `sessionStorage`
- Refresh Token 使用 `HttpOnly` + `SameSite=Strict` Cookie，生产环境通过 `prod` Profile 启用 `Secure`，前端 JavaScript 无法直接读取
- Axios 请求拦截器自动添加 `Bearer Token` 到请求头
- 遇到 `401` 响应时，自动尝试使用 Refresh Token 刷新 Access Token
- 刷新失败则跳转登录页
- 路由通过 `meta.requiresAuth` 控制登录权限
- 前端权限只用于界面控制（按钮显隐、页面访问），**最终权限必须由后端校验**

## 权限模型

| 维度 | 说明 |
| ---- | ---- |
| scope | JWT 内置 scope 声明，控制基础权限范围 |
| 平台角色 | 平台级角色，例如 `PLATFORM_SUPER_ADMIN`；写入用户 JWT 的 `platformRoles`，不写入空间成员关系 |
| 空间角色 | 绑定到具体 Space 的 `OWNER / EDITOR / VIEWER` 或自定义角色；通过权限标识符授权 |
| 空间成员关系 | 用户通过 `member.role_id` 绑定某个 Space 的角色，才能操作该空间下的资源 |
| Agent 范围白名单 | Agent 不直接拥有用户权限，必须绑定空间、文档范围和工具白名单 |

平台角色管理接口位于 Auth Service 的 `/api/platform/roles`，列表、详情、创建、修改和删除均要求 `PLATFORM_SUPER_ADMIN`。当前产品只开放数据库初始化且受保护的 `PLATFORM_SUPER_ADMIN`，不在前端开放自定义平台角色 CRUD。平台用户管理通过 `/api/platform/users` 提供查询、创建、资料、状态、密码重置和超级管理员绑定；移除或禁用超级管理员时必须至少保留一名启用的超级管理员。

平台用户和部门都属于平台组织域。部门只维护组织树、负责人和用户归属，不进入 Space 权限计算；用户仍需通过 `member` 关系加入具体 Space。平台超级管理员拥有约定的跨空间只读权限，但不会因此获得 Space 写权限。

## 方法级权限入口

业务服务通过 Spring Security `@EnableMethodSecurity` 启用 `@PreAuthorize`。Controller 负责声明接口所需的权限标识符，例如空间角色读取使用 `role:read`，角色维护使用 `role:manage`；`/api/document/spaces/{spaceId}/permissions` 是按空间权限保护的权限目录接口。注解只负责进入权限判定，空间成员、角色绑定和平台超级管理员特例由 `SpacePermissionService` 统一判断。

平台超级管理员可用于平台角色管理及约定的跨空间读取场景，但不会自动获得所有空间的写权限；空间写入仍需满足对应空间权限。

## 有关 Agent 的权限约束

- Agent 通过 OAuth2 Client Credentials 获取专属 Access Token
- Agent 不继承用户权限，区别于用户登录流程
- 每个 Agent 必须绑定：可操作的空间、可读写的文档范围、可调用的工具白名单
- Agent 权限变更需要空间所有者审批
