# 用户名、邮箱与 Session 认证接口

用户名快速接入见 [username-auth-api.md](username-auth-api.md)，用户名注册不需要邮箱或验证码，原邮箱流程保留。当前注册页复用昵称为登录名，见 [昵称登录对接](nickname-login-api.md)。

本文件对应实际 Spring Boot 4.1.2 代码，根包 com.xiye.garden。已检查现有代码、V1/V2、响应/异常处理及前端任务模型。没有可访问的登录设计稿文件，连接页面为空；按需求中的邮箱/密码/登录/忘记密码/注册流程实现。密码显示/隐藏、返回首页、页面跳转由前端处理，没有对应接口。

## 1. 方案、依赖与部署边界

使用 Spring Security + Spring Session JDBC + SMTP。没有 JWT、Redis、第三方登录、手机号或角色后台。所有任务接口必须登录，不再存在单用户兜底。

| 依赖 | 实际版本 | 选择依据 |
| --- | --- | --- |
| spring-boot-starter-security / session-jdbc / mail / security-test | 4.1.2 | 沿用现有 Boot parent，BOM 管理，不手写不同大版本依赖 |
| Spring Security | 7.1.2 | Boot BOM 管理 |
| Spring Session JDBC | 4.1.1 | Boot BOM 管理；V4 使用该 JAR 内官方 MySQL schema |
| PasswordEncoder | Spring Security PBKDF2 v5.8 参数集 | 310000次、HMAC-SHA256、随机盐；编码串带算法标识，支持后续迁移 |

依据：[Boot Session](https://docs.spring.io/spring-boot/reference/web/spring-session.html)、[Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)、[Security 会话持久化](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)、[CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)、[密码存储](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)。测试 starter 与 Boot 4 模块化保持一致。

默认本地 http://localhost:8080，API 前缀 /api；优先同源反向代理，开发用 Vite 代理。生产必须 HTTPS，激活 prod profile，Cookie Secure=true；不得同时启用 prod 与 local-mail/integration（启动会拒绝）。反向代理默认不信任客户端 X-Forwarded-For，限流取真实连接 remoteAddr；若经反向代理部署，代理IP将形成共享限制，须在可信网络边界正确设计代理/IP策略，不直接信任公网请求头。

## 2. SQL、历史任务与运行环境

手动先执行 sql/00_create_database.sql 创建 focus_garden（无密码、无默认账号），配置数据库账号后启动，由 Flyway 按顺序执行：

1. V1__create_tasks.sql
2. V2__complete_task_schema.sql
3. V3__users_and_authentication.sql：garden_user、garden_email_verification、garden_auth_rate_limit
4. V4__spring_session_jdbc.sql：SPRING_SESSION、SPRING_SESSION_ATTRIBUTES
5. V5__task_user_ownership.sql：任务 user_id 外键与按用户查询的组合索引
6. V6__username_password_authentication.sql：用户名唯一索引、email可空、至少一种身份的CHECK
7. V7__nickname_login_username.sql：支持中文登录名，回填唯一可用历史昵称

已有版本不会重复执行；V1–V5 没有修改。禁止删库重建或手动重复执行迁移。Spring Session initialize-schema=never，只有 Flyway 建表。会话表采用 Spring Session JDBC 4.1.1 JAR 的 schema-mysql.sql 字段、键与级联关系，额外补充表注释和默认 utf8mb4。

用户名账号的email可为null；邮箱注册填写昵称时生成username，未填才为null。V7仅给唯一可用的历史昵称回填登录名。用户主键 MySQL BIGINT AUTO_INCREMENT，所有 JSON ID 为十进制字符串；数据库业务时间 UTC DATETIME(6)，API ISO 8601 UTC string，可带小数，未设置的 avatarUrl/lastLoginAt 为 null。Session 表时间采用官方的 epoch 毫秒。

历史任务 user_id=NULL，保留但从全部正常用户 API 隔离，绝不分给第一个注册者。管理员使用 [sql/10_assign_legacy_tasks.sql](../../sql/10_assign_legacy_tasks.sql)，逐条填入经核实的 task_id/user_id 映射，再手动执行；默认空映射不分配任何数据。任务删除仍为逻辑删除，不级联删除历史数据。

运行变量（本地 .env 被 Git 忽略，Spring Boot/IDEA 不自动加载 .env）：

| 环境变量 | 必填/默认 | 用途 |
| --- | --- | --- |
| DB_HOST / DB_PORT / DB_NAME | localhost / 3306 / focus_garden | MySQL |
| DB_USERNAME / DB_PASSWORD | 必填 | 数据库凭据；应用需迁移DDL和业务DML权限 |
| AUTH_CODE_HMAC_KEY | 必填 | Base64随机密钥，解码至少32字节；openssl rand -base64 32；各实例一致并持久保管 |
| SMTP_HOST | 正常配置必填 | SMTP服务地址 |
| SMTP_PORT | 587 | 587一般使用STARTTLS，465一般使用SSL |
| SMTP_USERNAME / SMTP_PASSWORD | 默认空 | SMTP账号与授权码；不是数据库密码 |
| SMTP_FROM | 正常配置必填 | 发件地址，应与服务商允许的发件身份一致 |
| SMTP_AUTH | true | SMTP认证 |
| SMTP_STARTTLS | true | 同时控制STARTTLS enable/required |
| SMTP_SSL | false | 隐式TLS；使用465时通常设true、STARTTLS=false |
| SESSION_COOKIE_SECURE | 本地false，prod强制true | 本地HTTP调试与生产HTTPS |
| CORS_ALLOWED_ORIGINS | http://localhost:5173 | 精确来源列表，逗号分隔；禁止通配符 |
| SPRING_PROFILES_ACTIVE | 正常为空，生产prod | 本地捕获邮件显式使用local-mail |

SMTP连接、读、写超时均5秒。不配置或连接失败不会假装发送成功，返回503，不打印验证码。生产必须使用真实 SMTP + TLS，不能启用邮件替身。

本地可使用独立 Mailpit 捕获邮件（只是本地收件箱，不会发送到真实公网邮箱）：

```sh
docker run -d --name focus-garden-mailpit \
  -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 axllent/mailpit
# 在 .env 中填入数据库配置和随机 AUTH_CODE_HMAC_KEY
set -a
. ./.env
set +a
SPRING_PROFILES_ACTIVE=local-mail ./mvnw spring-boot:run
```

访问 http://localhost:8025 查看本地邮件内容。local-mail 显式覆盖 SMTP 为 localhost:1025，无认证/TLS，仅本地使用。镜像与端口参考 [Mailpit 官方文档](https://mailpit.axllent.org/docs/install/docker/)。IDEA 点击启动类时，在 GardenApplication 运行配置填入环境变量；本地 Mailpit 需激活 local-mail profile。不要提交真实 SMTP 密码、HMAC密钥或本地运行配置。

## 3. Cookie、CSRF 与前端调用顺序

会话 Cookie 名 GARDEN_SESSION，HttpOnly、Path=/、SameSite=Lax，不能由 JS 读取；生产 Secure。服务端空闲30分钟过期，每次有效访问刷新空闲计时；Cookie 为浏览器会话 Cookie，不承诺关闭浏览器后保留登录。Session 与 SecurityContext 存 MySQL，应用重启不依赖单机内存保存身份。

登录由 AuthenticationManager 验证邮箱密码，服务端轮换 Session ID，显式保存 SecurityContext。Session 仅保存用户ID/凭据版本及框架必要状态，不保存密码或验证码。重置密码递增凭据版本并删除该用户所有 MySQL Session；每次认证请求还核对状态/版本，拒绝并发旧会话回写。退出仅使当前会话失效并清理Cookie；禁用账号后原会话也会失效。

所有 POST/PUT/DELETE（包括匿名注册、登录、发码和密码重置）都需要 CSRF；GET/HEAD/OPTIONS不需要。先 GET /api/auth/csrf，浏览器保存响应 Cookie，同时把 data.token 存在内存，写请求带 data.headerName 指定的 X-CSRF-TOKEN。该端点保留默认 XOR/BREACH 保护，Token 不是 Session ID，不写入 localStorage。

- 首次打开：GET csrf → GET users/me；401为未登录，显示登录页。
- 用户名注册：GET csrf → POST register(username/password) → 跳转登录，无邮件步骤。
- 邮箱注册：GET csrf → POST email-codes(REGISTER) → 用户填写邮件验证码 → POST register → 跳转登录；注册不自动登录。
- 登录：GET csrf → POST login → **重新GET csrf** → GET users/me或受保护任务接口。旧Token在登录时清除，不能继续复用。
- 重置：GET csrf → POST email-codes(RESET_PASSWORD) → POST password/reset → **重新GET csrf** → 回登录页。
- 退出：POST logout带CSRF → 清理前端用户/任务缓存 → **重新GET csrf**，供下一次登录使用。匿名退出可幂等返回200，但仍需合法CSRF。
- 页面刷新：GET csrf + GET users/me恢复身份，不读取或手动保存Cookie值。
- 会话过期：受保护请求401，清除前端用户和任务缓存，跳转登录；刷新csrf后重新登录。公开认证写接口遇到过期CSRF可能返回403，刷新Token并提示用户重试，不自动重放注册/发码等写操作。

Vite现有配置可加：

```ts
server: { proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } } },
```

浏览器请求 /api。若直连后端跨源，Axios withCredentials=true 或 fetch credentials='include'，后端只允许明确来源且 allowCredentials=true，允许 X-CSRF-TOKEN，暴露 Retry-After。localhost:5173 与 localhost:8080 是跨源但同站；不要混用 localhost 与127.0.0.1导致Cookie或SameSite问题。真正跨站部署不在本期默认Lax策略范围，应优先通过同源代理解决。

## 4. 字段规则

| 字段 | 规则 |
| --- | --- |
| username | 显式注册/绑定采用3至32位英文字母开头的字母数字下划线；登录接受1至32位昵称或原用户名，禁止@和控制字符；去首尾空白并转小写，与email二选一 |
| email | 邮箱方式必填；ASCII邮箱、最多254字符；去首尾空白后整个地址转小写，注册/登录/发码/重置一致；不删除点或加号；数据库唯一 |
| password/newPassword | 必填，8–128个UTF-16字符；允许Unicode和空格，原样编码，不trim、不截断；PBKDF2没有bcrypt的72字节截断问题 |
| verificationCode | 邮箱注册/重置必填；用户名注册不得提交；6位ASCII数字字符串，保留开头0 |
| nickname | 注册可省略/null/空白，默认花园新朋友且不生成登录名；显式填写最多30字符，禁止@和控制字符；邮箱注册时同时生成唯一登录名，保存前strip |
| purpose | 必填，REGISTER或RESET_PASSWORD，大写枚举 |

未知请求字段返回400；请求不能修改用户status/credentialVersion/avatarUrl，也不能传userId切换身份。注册和登录都要求用户名/邮箱二选一。用户名注册不需要验证码，不发送email/verificationCode。未绑定邮箱的用户名账号本期无自助邮箱重置流程。独立 RegisterDTO/LoginDTO/EmailCodeDTO/ResetPasswordDTO；响应只返回 UserVO，无passwordHash。

## 5. 接口、JSON 示例与响应

统一成功 `{code:"OK",message:"成功",data:...}`；错误非2xx，`{code,message,data:null}`。ID始终string，时间ISO UTC，Cookie由HTTP头管理，不出现在JSON。

### GET /api/auth/csrf

无请求体，允许未登录。

响应 HTTP 200：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "headerName": "X-CSRF-TOKEN",
    "token": "从实际响应读取的令牌，不要硬编码"
  }
}
```

### POST /api/auth/email-codes

需Cookie与CSRF；注册和重置统一发送响应，不查询邮箱是否已注册。

请求 Content-Type: application/json：

```json
{
  "email": "gardener@example.com",
  "purpose": "REGISTER"
}
```

响应 HTTP 200：

```json
{
  "code": "OK",
  "message": "如可处理，验证码将发送至该邮箱，请查收",
  "data": null
}
```

### POST /api/auth/register

也支持用户名注册，不需要邮箱验证码：

```json
{"username":"garden_user","password":"示例密码 🌱 123","nickname":"小花友"}
```

下面是继续兼容的邮箱注册示例：

需Cookie与CSRF；注册成功不自动登录。

请求 Content-Type: application/json：

```json
{
  "email": "gardener@example.com",
  "password": "示例密码 🌱 123",
  "verificationCode": "012345",
  "nickname": "花园新朋友"
}
```

响应 HTTP 201：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "id": "1",
    "email": "gardener@example.com",
    "nickname": "花园新朋友",
    "avatarUrl": null,
    "lastLoginAt": null,
    "createdAt": "2026-11-12T06:00:00Z",
    "updatedAt": "2026-11-12T06:00:00Z",
    "username": "花园新朋友"
  }
}
```

### POST /api/auth/login

用户名方式：

```json
{"username":"garden_user","password":"示例密码 🌱 123"}
```

也可继续使用下面的邮箱方式；不能在同一请求同时传username和email。

需Cookie与CSRF；成功后重新获取csrf。

请求 Content-Type: application/json：

```json
{
  "email": "gardener@example.com",
  "password": "示例密码 🌱 123"
}
```

响应 HTTP 200：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "id": "1",
    "email": "gardener@example.com",
    "nickname": "花园新朋友",
    "avatarUrl": null,
    "lastLoginAt": "2026-11-12T06:05:00Z",
    "createdAt": "2026-11-12T06:00:00Z",
    "updatedAt": "2026-11-12T06:05:00Z",
    "username": null
  }
}
```

### GET /api/users/me

需登录Cookie；无请求体。

响应 HTTP 200：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "id": "1",
    "email": "gardener@example.com",
    "nickname": "花园新朋友",
    "avatarUrl": null,
    "lastLoginAt": "2026-11-12T06:05:00Z",
    "createdAt": "2026-11-12T06:00:00Z",
    "updatedAt": "2026-11-12T06:05:00Z",
    "username": null
  }
}
```

### PUT /api/users/me/username

需登录Cookie与CSRF。给尚未绑定登录用户名的账号一次性设置 username，不新建账号；已绑定返回409 `USERNAME_ALREADY_BOUND`，被他人占用返回409 `USERNAME_ALREADY_REGISTERED`。

```json
{"username":"xiye"}
```

响应 HTTP 200，data 为更新后的 UserVO（同时保留原 email）。

### POST /api/auth/logout

需Cookie与CSRF；无请求体；由Spring Security LogoutFilter处理，清理当前Cookie。

响应 HTTP 200：

```json
{
  "code": "OK",
  "message": "成功",
  "data": null
}
```

### POST /api/auth/password/reset

需Cookie与CSRF；重置后所有旧会话失效。

请求 Content-Type: application/json：

```json
{
  "email": "gardener@example.com",
  "verificationCode": "654321",
  "newPassword": "新的示例密码 🌱 456"
}
```

响应 HTTP 200：

```json
{
  "code": "OK",
  "message": "成功",
  "data": null
}
```

邮箱验证码发送和校验失败不会返回验证码明文。登录身份不存在、密码错误或账号禁用均返回401；邮箱方式为“邮箱或密码错误”，用户名方式为“用户名或密码错误”。邮箱是否已注册只在验证邮箱所有权后通过注册409告知。

## 6. 验证码、并发与限流

- SecureRandom生成6位数字，有效10分钟。存储HMAC-SHA256(email+purpose+code)，不保存明文；密钥至少32字节随机值。密钥轮换将使现有验证码不可用，并改变限流桶命名，应作为受控变更。
- 每邮箱每用途唯一一个验证码槽，新签发原子替换旧码并清零失败次数；旧码立即失效。新码若随机重复会重抽，防止旧码值重新有效。
- 单码最多5次错误尝试。校验锁定槽位；成功消费与用户插入/密码修改同事务。错误次数即使返回400也提交；并发注册同码仅一次成功。重复消费、用途不符、过期、次数耗尽统一400，不透露具体内部原因。
- 同邮箱同用途至少间隔60秒，事务内按上次实际签发时间校验，不仅依赖固定窗口计数。
- 发码每邮箱每用途5次/小时、每IP合计20次/小时；登录每账号（邮箱或用户名）10次/15分钟、每IP50次/15分钟；验证码校验（注册+重置合计）每邮箱10次/15分钟、每IP30次/15分钟。计数包含成功和失败请求，参数结构通过校验后才计数。
- 限流是固定UTC窗口，MySQL唯一键与原子递增，独立事务提交，重启/多实例共享状态；不同实例需共享MySQL、HMAC密钥并保持时间同步。60秒最小发码间隔另用验证码槽行锁保证。
- 用户名注册每用户名5次/小时、每IP20次/小时，包含成功与重复冲突请求；不需要邮件验证码，但仍保护CSRF并使用MySQL限流。
- 返回429 RATE_LIMITED与Retry-After秒数；前端禁用按钮并倒计时，等待后由用户重试。发信失败同样消耗发送限额，避免SMTP故障时被无限重试。
- SMTP接受之前保持验证码槽锁，发送失败回滚新挑战，保留旧码状态；新槽失败后不留下可用验证码。MySQL提交与SMTP没有分布式事务：若邮件已被SMTP接受但后续提交失败，可能收到不可用验证码，应等待间隔后重发，不能承诺“恰好一次邮件投递”。
- 每60秒最多清理1000条已结束限流窗口和1000条过期超过24小时的验证码槽，积压由后续批次消化。Session使用框架默认每分钟清理过期会话；读取时也检查过期，不依赖清理任务及时运行。

## 7. HTTP/业务错误码与前端处理

| HTTP | code | 处理 |
| --- | --- | --- |
| 400 | BAD_REQUEST | 参数/格式错误，保留表单输入 |
| 400 | VERIFICATION_INVALID | 验证码无效/过期/已用/用途不符/次数耗尽，提示重新获取 |
| 401 | INVALID_CREDENTIALS | 统一不区分身份不存在、密码错误或禁用；按方式提示邮箱/用户名或密码错误 |
| 401 | UNAUTHORIZED | 未登录、会话过期、禁用或版本失效，清理用户缓存并登录 |
| 403 | CSRF_INVALID | Token缺失/错误，重新获取csrf，提示重试 |
| 403 | FORBIDDEN | 无权限；不跳HTML页面 |
| 404 | NOT_FOUND | 任务不存在或属于其他用户，统一不存在 |
| 409 | USERNAME_ALREADY_REGISTERED | 用户名冲突（注册或绑定），提示更换或登录 |
| 409 | USERNAME_ALREADY_BOUND | 当前账号已绑定登录用户名，不可更改 |
| 409 | EMAIL_ALREADY_REGISTERED | 邮箱冲突，提示转登录 |
| 429 | RATE_LIMITED | 按Retry-After等待，不立即重试 |
| 503 | MAIL_UNAVAILABLE | SMTP未接受邮件，不显示发送成功 |
| 503 | SERVICE_UNAVAILABLE | 当前身份核对所需数据库不可用 |
| 500 | INTERNAL_SERVER_ERROR | 未预期错误，内部信息不回传 |

示例：

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "邮箱或密码错误",
  "data": null
}
```

```json
{
  "code": "VERIFICATION_INVALID",
  "message": "验证码无效或已过期，请重新获取",
  "data": null
}
```

```json
{
  "code": "RATE_LIMITED",
  "message": "请求过于频繁，请稍后重试",
  "data": null
}
```

受保护接口未认证优先返回401；公开认证写接口缺失CSRF返回403。请求体/查询参数校验错误使用现有中文message，不解析中文文本作为业务分支；使用status/code。405/406/415沿用任务模块契约。不返回密码、验证码、Session ID或数据库异常正文。

## 8. TypeScript 与任务接口接入

可直接复制：[auth-types.ts](auth-types.ts)、[auth-client.ts](auth-client.ts)、[task-types.ts](task-types.ts)、[task-client.ts](task-client.ts)。使用同一个请求实例：

```ts
const http = createGardenHttp('/api');
const auth = createAuthClient(http);
const tasks = createTaskClient(http);
await auth.csrf();
// 刷新页面时：me 的401表示未登录，交给页面显示登录表单
const currentUser = await auth.me();
// 登录成功后auth.login内部自动刷新csrf
await auth.login({ email, password });
const page = await tasks.list({ page: 1, size: 20 });
```

request层统一credentials/include、CSRF、JSON错误与Retry-After；Token只在内存。网络失败不自动重试写请求。若登录已成功但随后的csrf获取失败，先调用me确认身份，再刷新csrf，避免误显示密码错误。

已有Axios也可继续使用，但必须 withCredentials=true，并通过共享拦截器/请求层加X-CSRF-TOKEN；不能只改baseURL继续使用旧的不带CSRF的任务请求。所有写操作后按任务文档刷新缓存；登出/401时清空跨用户任务缓存。不会直接改前端mockService，避免与尚未实现的专注/收藏聚合模型混用。

## 9. 可执行示例与验证

[../examples/auth-flow.sh](../examples/auth-flow.sh) 使用curl Cookie jar，并真实获取/携带CSRF Token，交互输入密码与邮件验证码，不把密码放进命令参数或源码：

```sh
bash docs/examples/auth-flow.sh username-register # 用户名注册→登录，无邮件步骤
bash docs/examples/auth-flow.sh username-login    # 用户名登录
bash docs/examples/auth-flow.sh register  # 发注册码→注册→登录→刷新csrf→me→任务列表→退出
bash docs/examples/auth-flow.sh login     # 已注册用户登录与退出
bash docs/examples/auth-flow.sh reset     # 发重置码→改密→新密码登录与退出
```

需要实际SMTP或显式local-mail + Mailpit。用户从邮箱/本地捕获收件箱取得验证码。脚本不会绕过邮件步骤；Cookie jar仅临时保存，退出后删除，不打印Session ID。现有requests.http也已补充CSRF说明；需要完整流程优先用该脚本。

无数据库测试：`./mvnw test`。真实MySQL：专用空库focus_garden_auth_test（或其他独立测试库），配置TEST_DB_NAME、TEST_DB_USERNAME、TEST_DB_PASSWORD，运行 `./mvnw -Pintegration verify`。测试profile覆盖SMTP，且仅测试代码以Mockito捕获JavaMailSender；生产代码不存在“打印验证码”的替身。测试通过真实HTTP服务器、Cookie jar、CSRF与真实MySQL校验会话/业务行为。

本次V6与用户名/邮箱流程的结果见根目录README最新验证记录。真实邮件服务商投递、生产HTTPS反向代理与实际浏览器跨域尚未验证。没有读取设计稿或宣称页面视觉已核对，没有接入OpenAPI框架。
