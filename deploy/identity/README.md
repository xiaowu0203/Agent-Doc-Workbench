# Keycloak + PostgreSQL 本机P0部署

本目录供维护者自行启动两个独立容器：Keycloak 26.8.1、PostgreSQL 17.11 Alpine 3.24。模板固定版本及多架构镜像digest；同版本用户容器已用于真实协议、TOTP与隔离BFF/Redis验证，结果见[公开P0基线](../../docs/designs/v0.3.0-p0-auth-baseline.md#7-真实idp与隔离bff原型)。当前运行容器使用版本tag，不能据此声称已按模板digest启动。现有MySQL/Redis保持原状，不需要新增Redis；本目录不包含业务权限或业务库迁移。

这是本机开发/测试配置，使用HTTP和`start-dev`；正式部署另配置HTTPS和生产启动模式。两个端口仅绑定宿主机回环地址。本目录不是集群/联邦配置。

## 1. 一次性准备

在项目根目录的PowerShell执行：

```powershell
if (-not (Test-Path -LiteralPath .\deploy\identity\postgres\010-keycloak.sh -PathType Leaf)) { throw '请先切到项目根目录并确认初始化脚本存在' }
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\identity\Initialize-LocalEnv.ps1
```

脚本生成六份独立随机凭据到`deploy/identity/.env`，不输出凭据、不启动服务，也不覆盖已有文件。该文件已由仓库现有忽略规则排除；不要把内容发到聊天或提交仓库。也可从本目录`.env.example`自行创建`.env`并填写全部空值。

## 2. 启动

自行打开Docker Desktop，确认使用Linux容器后，在项目根目录执行：

```powershell
docker compose --env-file .\deploy\identity\.env -f .\deploy\identity\docker-compose.yml config --quiet
docker compose --env-file .\deploy\identity\.env -f .\deploy\identity\docker-compose.yml up -d
docker compose --env-file .\deploy\identity\.env -f .\deploy\identity\docker-compose.yml ps
```

`config --quiet`只校验配置，不输出展开后的密码。首次拉取/初始化需要等待两个容器变为healthy；Keycloak在PG就绪后启动，并导入一个应用realm。助手不会代为运行上述命令。

| 连接 | 地址/账号 |
| --- | --- |
| Keycloak管理控制台 | `http://localhost:18090/admin/`；账号`p0-admin`，密码取本地`.env`的`KEYCLOAK_ADMIN_PASSWORD` |
| 应用realm | `agent-doc-workbench-p0`，独立测试realm，不迁移现有业务用户 |
| Discovery | `http://localhost:18090/realms/agent-doc-workbench-p0/.well-known/openid-configuration` |
| PostgreSQL | 宿主机`localhost:15432`，容器内5432，数据库`keycloak` |
| Keycloak数据库用户 | `keycloak`，非超级用户；密码为`KEYCLOAK_DB_PASSWORD` |
| PG维护用户 | `postgres`；独立密码为`POSTGRES_ADMIN_PASSWORD` |
| 登录测试用户 | `p0-user`；密码为`KEYCLOAK_POC_USER_PASSWORD`；只存在于测试realm |
| PoC BFF回调 | `http://localhost:18091/login/oauth2/code/workbench`；当前原型使用应用上下文内WebTestClient，不启动18091监听服务 |

PG数据保存在本Compose项目独立的`postgres-data`命名卷，不连接Workbench业务MySQL。Keycloak使用独立PG账户维护自身表；PG超级用户只供维护，不注入Keycloak进程。

手动`docker run`同样要先确认初始化脚本的宿主路径是文件；短格式`-v`遇到不存在的路径可能创建目录，导致首次初始化失败。已有卷能运行不代表该挂载已修好；重建空卷前先修正路径，不通过删除现有卷解决路径问题。

统一通过`localhost`访问，避免与`127.0.0.1`混用导致issuer/Cookie不一致。若修改Keycloak端口，`.env`中的`KEYCLOAK_HTTP_PORT`与`KEYCLOAK_PUBLIC_URL`应同步；BFF端口通过`KEYCLOAK_POC_BFF_URL`单独设置。远程服务器部署需要另定实际域名、HTTPS和访问入口，本配置默认仅本机。

## 3. 已导入的最小身份配置

- 单应用realm；关闭公开注册、直接密码授权及隐式授权；不配置用户联邦、业务角色、租户/空间/Skill/ACL映射或Keycloak业务授权服务。
- `workbench-web`为机密客户端：授权码+PKCE S256、精确回调、`workbench:access`及`workbench-api`受众。
- `workbench-task`只支持Client Credentials：`capability:task:issue`及`workbench-auth-internal`受众，机器Token暂按300秒做PoC（正式机器TTL待验证冻结，不改人类7天策略或业务Capability）。scope是协议/API用途上限，不授予任意Task资源或actions。
- 按已确认策略设置7天Access/SSO窗口并保留remember；刷新轮换开启、旧Refresh不复用。密码策略最少12字符，开启暴力破解保护。短密码拒绝、临时用户TOTP绑定及后续挑战已通过HTTP样本验证；默认`p0-user`未启用OTP，前端真实浏览器MFA仍待验收。
- 最小`basic` scope仅保留`sub/auth_time`，避免裁剪默认scope时丢失身份；不引入`realm_access/resource_access`业务角色映射。
- SMTP尚未配置，测试realm暂不开放邮件找回；测试邮箱为保留的`.invalid`地址。该用户不成为Workbench业务管理员，不自动绑定业务库账号。
- Realm JSON仅有环境变量占位符；客户端/用户密码由容器环境替换，没有公共默认密码。首次启动导入；已有同名realm时启动导入会跳过，不会覆盖管理侧修改。

## 4. 自检与后续验证

```powershell
Invoke-RestMethod http://localhost:18090/realms/agent-doc-workbench-p0/.well-known/openid-configuration | Select-Object issuer, authorization_endpoint, token_endpoint, jwks_uri
docker compose --env-file .\deploy\identity\.env -f .\deploy\identity\docker-compose.yml exec postgres pg_isready -h 127.0.0.1 -U postgres -d keycloak
```

本次已检查数据库版本/角色和Discovery/JWKS，并完成第6节的隔离验证。尚未重启用户容器，不将“容器healthy”或本批PoC视为P0全部通过；生产HTTPS、持久化部署恢复及真实浏览器仍待验收。无需发送任何密码。

当前Workbench正式入口尚未切换OIDC，启动这两个容器不会自动改变现有登录方式；18091是隔离原型使用的逻辑回调地址。

本地`.env`供本机验证读取；它不自动注入宿主机Java进程，也不替代P0过渡调用所需的`TASK_INTERNAL_AUTH_SECRET`。测试realm的OAuth客户端密钥与过渡Basic机器凭据是不同用途，不能混用。

## 5. 查看日志与停止

```powershell
docker compose --env-file .\deploy\identity\.env -f .\deploy\identity\docker-compose.yml logs --tail 100 keycloak postgres
docker compose --env-file .\deploy\identity\.env -f .\deploy\identity\docker-compose.yml down
```

`down`保留数据库卷。修改`.env`不会自动更改已有数据库角色、管理员或客户端密码；轮换/重新导入需要单独处理。数据库卷保留时不能靠重新运行初始化脚本重置密码。初始化脚本保持LF换行。

配置依据：[Keycloak容器](https://www.keycloak.org/server/containers)、[数据库支持](https://www.keycloak.org/server/db)、[Realm导入及变量](https://www.keycloak.org/server/importExport)、[PostgreSQL官方镜像版本](https://github.com/docker-library/official-images/blob/master/library/postgres)。

## 6. P0隔离验证工具

以下工具供当前独立P0夹具验证，均不启动/重建/重启容器或修改业务库。现有环境需安装Python的`requests`、`cryptography`和JDK21；本次已复用现有依赖，不自动安装软件。Redis使用现有实例，测试每次生成随机namespace并只清理自身键，禁止清库。

- `Prepare-PocRealm.py`读取名为`adw-identity-keycloak`的本地容器bootstrap管理员环境，只在内存获取管理员Token；首次在空实例创建本模板realm、随机客户端/用户凭据，并将夹具配置保存到被忽略的`.codex-validation-store/v0.3.0-p0/runtime/oidc-poc.json`。不会输出凭据，不导入业务账号。
- 已有realm只有在带本模板的`adw.p0.fixture=v1`标记且本机私有夹具配置存在时才复用；不覆盖现有密码或客户端。通过Compose启动导入的realm不自动生成该私有JSON；该情形不能直接运行自动准备命令，需要先受控准备匹配的夹具配置，工具会拒绝静默接管。同名非测试realm绝不复用。
- `Test-PocIdentity.py`验证真实协议/签名/claims/刷新/吊销和负例；`Test-PocMfa.py`在确认fixture标记后创建临时用户，验证密码策略与TOTP，结束时仅删除本次创建且由服务端Location返回ID的用户。OTP密钥不落盘。
- `PocBrowser.py`为Java测试的HTTP驱动，通过子进程stdin/stdout传递重定向，Cookie/授权码只在内存中流转。驱动仅对`localhost`复现[浏览器Secure Cookie例外](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie)，不移除Keycloak Cookie的Secure属性；不代表真实浏览器、跨站或HTTPS验收。TOTP表单按[固定版本Keycloak模板](https://github.com/keycloak/keycloak/blob/26.8.1/themes/src/main/resources/theme/base/login/login-config-totp.ftl)验证。
- Java原型及OAuth Client/Session新增依赖仅在test scope；密码校验和业务用户预绑定以独立Auth夹具提供，BFF不写领域权限。Redis加密适配复用`AesGcmCrypto`；测试用JDK对象序列化不是正式存储格式决策。

对于由该工具首次准备且私有夹具配置仍在的测试实例，在项目根目录执行：

```powershell
python .\deploy\identity\Prepare-PocRealm.py
python .\deploy\identity\Test-PocIdentity.py
python .\deploy\identity\Test-PocMfa.py

$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21' # 按本机JDK21安装路径调整
$env:RUN_AUTH_P0_OIDC_POC = 'true'
$env:RUN_AUTH_P0_REDIS_POC = 'true'
# 默认Redis localhost:6379；需要时通过POC_REDIS_HOST/PORT/PASSWORD进程环境传入，不写测试源码。
.\backend\mvnw.cmd -f backend/pom.xml -B -ntp -pl gateway-service -am '-Dtest=P0LocalBffPocTest,P0KeycloakBffPocTest' '-Dsurefire.failIfNoSpecifiedTests=false' test

# 结束后只清理固定测试用户的IdP会话，不删除realm或客户端。
python .\deploy\identity\Prepare-PocRealm.py --logout-fixture-user
Remove-Item Env:RUN_AUTH_P0_OIDC_POC -ErrorAction SilentlyContinue
Remove-Item Env:RUN_AUTH_P0_REDIS_POC -ErrorAction SilentlyContinue
```

未显式设置`RUN_AUTH_P0_OIDC_POC=true`时，5项真实IdP测试默认跳过；local6项默认执行，不请求IdP。Redis恢复用例还要求`RUN_AUTH_P0_REDIS_POC=true`。失败时日志只显示状态/错误类别，禁止记录请求体、认证头或重定向查询参数；测试配置JSON、真实凭据和原始Token均不得提交。
