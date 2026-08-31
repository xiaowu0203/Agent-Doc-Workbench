# 鉴权与安全方案

## 用户登录流程（Authorization Code + PKCE）

```
浏览器
  → OAuth2 登录页
  → Auth Service（授权码 + PKCE 验证）
  → 返回 Access Token + Refresh Token
  → 携带 Access Token 调用 Gateway
  → Gateway 校验 JWT → 路由到业务服务
  → 业务服务再次校验 JWT
```

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
| Refresh Token 存储 | HttpOnly + Secure + SameSite Cookie |

## 前端安全规则

- Access Token 只在内存中持有，不写入 `localStorage` 或 `sessionStorage`
- Refresh Token 使用 `HttpOnly` + `Secure` + `SameSite` Cookie，前端 JavaScript 无法直接读取
- Axios 请求拦截器自动添加 `Bearer Token` 到请求头
- 遇到 `401` 响应时，自动尝试使用 Refresh Token 刷新 Access Token
- 刷新失败则跳转登录页
- 路由通过 `meta.requiresAuth` 控制登录权限
- 前端权限只用于界面控制（按钮显隐、页面访问），**最终权限必须由后端校验**

## 权限模型

| 维度 | 说明 |
| ---- | ---- |
| scope | JWT 内置 scope 声明，控制基础权限范围 |
| 平台角色 | 平台级角色，例如 `PLATFORM_SUPER_ADMIN`；不写入空间成员关系 |
| 空间角色 | 绑定到具体 Space 的 `OWNER / EDITOR / VIEWER` 或自定义角色；通过权限标识符授权 |
| 空间成员关系 | 用户通过 `member.role_id` 绑定某个 Space 的角色，才能操作该空间下的资源 |
| Agent 范围白名单 | Agent 不直接拥有用户权限，必须绑定空间、文档范围和工具白名单 |

平台角色管理接口位于 Auth Service 的 `/api/platform/roles`，列表、详情、创建、修改和删除均要求 `PLATFORM_SUPER_ADMIN`。平台超级管理员角色由数据库初始化并保持受保护，首次将用户绑定为平台超级管理员也通过数据库完成。

## 有关 Agent 的权限约束

- Agent 通过 OAuth2 Client Credentials 获取专属 Access Token
- Agent 不继承用户权限，区别于用户登录流程
- 每个 Agent 必须绑定：可操作的空间、可读写的文档范围、可调用的工具白名单
- Agent 权限变更需要空间所有者审批
