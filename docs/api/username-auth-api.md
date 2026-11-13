# 用户名与密码注册、登录对接

在已有邮箱验证码注册/登录基础上新增用户名模式，沿用同一组接口和同一套 Session/CSRF/用户/任务体系，没有创建第二套身份或JWT。邮箱接口保持兼容。当前注册页复用昵称作为登录名，见 [昵称登录对接](nickname-login-api.md)。

## 1. 数据库和启动

新增真实迁移 `src/main/resources/db/migration/V6__username_password_authentication.sql`。V6原始变更如下；V7进一步扩展登录名和回填历史昵称，按版本顺序自动执行：

- garden_user新增可为空的username及唯一索引，采用ASCII区分大小写的存储排序规则；应用统一转小写，避免大小写重复账号。
- email改为可为空，保留原唯一索引。用户名账号email=NULL，邮箱账号username=NULL，不生成虚假邮箱。
- CHECK要求至少一种身份存在。历史邮箱用户与任务归属不变，不自动给历史用户分配用户名。

本地仍使用现有MySQL与环境变量，`local-mail`可继续启用；用户名注册和登录不调用邮件服务，也不需要启动Mailpit收取验证码。邮件相关配置仍供保留的邮箱功能使用，不要删除现有HMAC/SMTP配置。生产仍按[认证总文档](auth-api.md)配置HTTPS和Secure Cookie。

## 2. 业务与校验

| 字段 | 规则 |
| --- | --- |
| username | 注册/用户名登录必填；去首尾空白；3–32位ASCII英文字母、数字或下划线，必须以字母开头；大小写不敏感，保存为小写；正则 `[A-Za-z][A-Za-z0-9_]{2,31}` |
| password | 必填，8–128个UTF-16字符，保留空格和Unicode，不trim、不截断；PBKDF2哈希 |
| nickname | 注册可选，最多30字符，可用中文；null/缺省/空白默认“花园新朋友”，保存时去首尾空白 |

V7起，邮箱注册显式填写的nickname同时生成唯一username，支持中文；未填昵称则username=null。唯一可用历史昵称会回填，冲突跳过。上述ASCII规则仅用于显式username注册/绑定；登录接受1至32位昵称或原用户名，禁止@和控制字符。旧邮箱用户可登录后通过 `PUT /api/users/me/username` 一次性绑定登录用户名；已绑定后不可更改。当前没有绑定邮箱或合并账号接口。旧邮箱用户可用邮箱登录；已生成username的也可用昵称登录。

用户名账号没有已验证邮箱，**本次不提供该账号的自助邮箱找回密码**；不要将重置请求中的邮箱随意关联到用户名账号。原邮箱验证码重置仅处理对应的邮箱账号。

注册二选一：

- 用户名方式：username + password + 可选nickname，不发email/verificationCode。
- 邮箱方式：email + password + verificationCode + 可选nickname，不发username；流程与之前相同。

登录二选一：username + password，或email + password。两者都不填或同时填写均返回400；其他未知字段（如userId/status）返回400。建议省略未使用的字段，而不是传空字符串。

## 3. Cookie与CSRF调用顺序

所有请求推荐通过Vite代理使用相同的`/api`来源；fetch用`credentials:'include'`，Axios用`withCredentials:true`。

1. GET /api/auth/csrf，保留Cookie并记录data.token、data.headerName。
2. POST /api/auth/register，带X-CSRF-TOKEN，用户名模式不需要验证码。成功201，不自动登录，跳转登录页。
3. POST /api/auth/login，带Cookie和CSRF；成功200通过HttpOnly Cookie建立MySQL Session。
4. **登录后重新GET /api/auth/csrf**，旧Token在认证时清除。
5. GET /api/users/me恢复用户信息；之后访问任务接口，写请求带新CSRF。
6. POST /api/auth/logout带CSRF，清空前端用户与任务缓存，再获取新的匿名CSRF。

Cookie空闲30分钟过期；401表示重新登录。Session ID不出现在JSON，前端不用保存或读取Cookie值。403 CSRF_INVALID时获取新Token并提示重试，不自动重放注册。原登录轮换Session ID、HttpOnly、生产Secure和任务用户隔离保持有效。

## 4. 请求与响应

### 注册

`POST /api/auth/register`，Content-Type: application/json，Cookie与X-CSRF-TOKEN必需。

```json
{
  "username": "garden_user",
  "password": "安全密码 🌱 123",
  "nickname": "小花友"
}
```

HTTP 201：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "id": "123",
    "email": null,
    "username": "garden_user",
    "nickname": "小花友",
    "avatarUrl": null,
    "lastLoginAt": null,
    "createdAt": "2026-11-12T08:00:00Z",
    "updatedAt": "2026-11-12T08:00:00Z"
  }
}
```

### 登录

`POST /api/auth/login`，Content-Type: application/json，Cookie与X-CSRF-TOKEN必需。

```json
{"username":"garden_user","password":"安全密码 🌱 123"}
```

HTTP 200，data结构与注册相同，lastLoginAt/updatedAt更新为服务端登录时间。ID仍是字符串，时间仍是ISO 8601 UTC字符串；不存在的另一种身份返回null。登录不返回密码、哈希或Session ID。

邮箱登录继续使用：

```json
{"email":"gardener@example.com","password":"安全密码 🌱 123"}
```

邮箱用户返回email字符串，username未生成时才为null。GET /api/users/me返回同一UserVO结构，刷新页面时调用它判断登录状态；未登录返回JSON401。

### 绑定登录用户名

`PUT /api/users/me/username`，需登录Cookie与X-CSRF-TOKEN，Content-Type: application/json。仅允许尚未绑定 username 的账号（通常是邮箱注册用户）一次性设置；不新建账号，邮箱与任务归属不变。绑定后可用用户名或邮箱登录。

```json
{"username":"xiye"}
```

HTTP 200，data 为更新后的 UserVO（含 email 与 username）。

### 错误

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | BAD_REQUEST | 用户名格式/密码长度错误，身份参数缺失/冲突，用户名注册却提交验证码 |
| 401 | INVALID_CREDENTIALS | 用户名不存在、密码错误或账号禁用；统一提示“用户名或密码错误” |
| 401 | UNAUTHORIZED | 未登录或会话过期 |
| 403 | CSRF_INVALID | 缺少/失效的安全Token |
| 409 | USERNAME_ALREADY_REGISTERED | 用户名已存在（注册或绑定），大小写归一后仍判重复；数据库唯一约束处理并发 |
| 409 | USERNAME_ALREADY_BOUND | 当前账号已绑定登录用户名，不可再次更改 |
| 409 | EMAIL_ALREADY_REGISTERED | 原邮箱注册冲突 |
| 429 | RATE_LIMITED | 请求限流；按Retry-After秒数等待 |

```json
{"code":"USERNAME_ALREADY_REGISTERED","message":"该用户名已重复，请更换用户名或直接登录","data":null}
```

用户名注册每用户名5次/小时、每IP20次/小时，成功和冲突都计数。登录每账号（规范化邮箱或用户名）10次/15分钟、每IP50次/15分钟，计数持久化在MySQL独立事务中；不是未接入的内存配置。

## 5. 前端TS类型与请求函数

直接复制[auth-types.ts](auth-types.ts)、[auth-client.ts](auth-client.ts)和共用的[task-types.ts](task-types.ts)。RegisterDTO/LoginDTO已经使用TS联合类型，阻止同时提交邮箱和用户名；UserVO.email已改为string|null，新增username:string|null。

```ts
const http = createGardenHttp('/api');
const auth = createAuthClient(http);
await auth.csrf();
await auth.register({ username, password, nickname });
// 注册后跳转登录，不自动登录
const user = await auth.login({ username, password }); // 内部刷新CSRF
const displayName = user.nickname || user.username || user.email || '花友';
```

如UI只有一个“账号”输入框，按@区分两种登录请求；服务端没有新增account字段：

```ts
const loginByAccount = (account: string, password: string) => {
  const identity = account.trim();
  return auth.login(identity.includes('@')
    ? { email: identity, password }
    : { username: identity, password });
};
```

只trim账号，**不要trim密码**。密码显示/隐藏、返回首页仍由前端处理。任务请求与auth必须共用同一http对象；退出和401后清除该用户任务缓存，不能回退到匿名真实数据。

## 6. 可执行示例与验证

```sh
bash docs/examples/auth-flow.sh username-register
bash docs/examples/auth-flow.sh username-login
```

脚本先获取CSRF、保存Cookie，交互输入用户名和密码；用户名注册不发邮件，注册后示范登录、刷新CSRF、me、任务列表和退出。原来的register/login/reset模式仍是邮箱流程。

本次验证覆盖用户名无邮箱注册、大小写归一、并发重复注册、非法/冲突参数、正确/错误密码、禁用用户、注册/登录限流及既有邮箱流程回归；V6已在独立MySQL测试库执行；25项无数据库测试、17项真实MySQL/HTTP集成测试全部通过，TS严格类型检查通过，用户名curl脚本全流程已实际执行成功。未修改前端业务页面，交付的是后端、文档与可复制TS对接文件。
