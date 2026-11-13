# 邮箱注册复用昵称作为登录名

前端保持当前邮箱验证码注册表单，不需要增加单独的用户名注册页面。从 V7 开始，邮箱注册时显式填写的 `nickname` 同时生成唯一登录名 `username`，该账号可使用邮箱或昵称加同一密码登录。

## 请求与字段规则

先 `GET /api/auth/csrf`，保存 Cookie 和 CSRF Token。写请求携带 Cookie、`X-CSRF-TOKEN` 和 `Content-Type: application/json`。

1. `POST /api/auth/email-codes`：`{"email":"gardener@example.com","purpose":"REGISTER"}`。
2. 收到验证码后，`POST /api/auth/register`：

```json
{
  "email": "gardener@example.com",
  "verificationCode": "012345",
  "password": "示例密码 🌱 123",
  "nickname": "小花友"
}
```

HTTP 201 的 `data` 为现有 `UserVO`，其中 `email="gardener@example.com"`、`nickname="小花友"`、`username="小花友"`，其余字段、统一响应和时间格式见 [认证总文档](auth-api.md)。注册不自动登录。

- `nickname` 仍为选填，最多 30 个 UTF-16 字符，支持中文、Emoji 和内部空格；不能包含 `@` 或 ASCII 控制字符。
- 去首尾空白后为空，按未填写处理：显示昵称为“花园新朋友”，`username=null`，仅能邮箱登录。
- 显式昵称保留展示大小写；登录名由服务端去首尾空白并整体转小写。例如 ` Xiye ` 保存为 `nickname="Xiye"`、`username="xiye"`。
- 非空昵称生成的登录名不得与任何既有 `username` 冲突，包含旧用户名注册账号。MySQL 唯一约束处理并发抢占；冲突返回 HTTP 409、`USERNAME_ALREADY_REGISTERED`，提示“该昵称或用户名已重复，请更换昵称或直接登录”。
- 注册 DTO 仍只传 `email`、`password`、`verificationCode`、`nickname`，不要同时额外发送 `username`。
- 保留原先仅传 `username/password` 的注册接口兼容性，显式 username 注册及绑定仍遵循原 ASCII 3～32 位规则；当前页面不必使用该流程。

3. `POST /api/auth/login`：

```json
{"username":"小花友","password":"示例密码 🌱 123"}
```

或者邮箱方式：

```json
{"email":"gardener@example.com","password":"示例密码 🌱 123"}
```

昵称登录仍使用字段 `username`，不发送 `nickname`。登录名允许 1～32 个 UTF-16 字符，不能包含 `@` 或 ASCII 控制字符，以兼容昵称生成的登录名和旧英文账号。邮箱与 username 必须且只能传一个。密码不 trim、不修改大小写。

登录成功后刷新 CSRF Token；刷新页面用 `GET /api/users/me` 恢复身份。HTTP 401 的处理、Cookie 和 Session 机制不变。邮箱注册的账号可以通过其已验证邮箱找回密码。

## TypeScript

```ts
export interface EmailNicknameRegisterDTO {
  email: string;
  verificationCode: string;
  password: string;
  nickname?: string | null;
}

export type LoginDTO = { password: string } & (
  | { username: string; email?: never }
  | { email: string; username?: never }
);
```

完整请求/响应定义见 [auth-types.ts](auth-types.ts)。前端注册字段标注为“昵称 / 登录用户名（选填）”，明确不可重复；返回 409 时展示服务端错误，允许修改昵称重试（必要时重新获取验证码）。

## 历史数据与部署

新增可执行 Flyway 迁移：`src/main/resources/db/migration/V7__nickname_login_username.sql`，不修改 V1～V6。

- username 改为 utf8mb4 / utf8mb4_bin，支持中文，并保留唯一索引。
- 已有非空 username 不改写，用户 ID、密码、Session、邮箱和任务归属不变。
- 仅为唯一、非空、非默认、格式有效且未被他人占用的历史昵称回填 username；按去首尾普通空格、转小写后判重。
- 重复昵称、默认“花园新朋友”、无效昵称或已被其他登录名占用的昵称跳过。此类用户继续邮箱登录，并可使用现有 `PUT /api/users/me/username` 绑定一个可用英文用户名。没有按昵称模糊查找、任选一人或按密码逐个试配的兜底。
- 发版前备份数据库；部署新后端后由 Flyway 执行 V7，再发布前端。不得手工重复运行生产 Flyway 脚本。

可通过 Navicat 只读确认：

```sql
SELECT id, email, nickname, username
FROM garden_user
WHERE username IS NULL
ORDER BY id;
```

可执行请求示例：`bash docs/examples/auth-flow.sh register`（交互输入昵称、验证码和密码）；验证昵称登录运行 `bash docs/examples/auth-flow.sh username-login`，输入注册时填写的昵称。

## 本次验证（2026-11-13）

- Maven Wrapper `-Pintegration verify`：28个单元测试和21个数据库集成测试全部通过，使用独立库 `focus_garden_auth_test` 和邮件替身。
- V7在独立MySQL临时测试库验证了11条历史记录：中文、短昵称、大小写重名、已有用户名占用、默认昵称、无效昵称及已有绑定保留等情况全部符合预期。
- 前端TypeScript检查通过；41个单元测试通过；注册、重置和认证相关6个桌面浏览器测试通过（接口替身）。
- 尚未发布生产镜像或执行线上V7迁移；本次没有使用真实SMTP发送验证邮件。
