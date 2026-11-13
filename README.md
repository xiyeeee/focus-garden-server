# 专注花园后端

自动测试、推送阿里云镜像和SSH发布配置见 [GitHub Actions部署说明](docs/deployment/github-actions.md)。需在仓库配置ACR和SSH Secrets后才能部署。

Java 21 / Spring Boot 4.1.2 / MySQL / MyBatis-Plus。已实现任务、用户名密码注册/登录、邮箱验证码注册/登录、当前用户、退出和邮箱验证码密码重置；使用 Spring Security + MySQL Session + CSRF，没有 JWT 或 Redis。保留根包 com.xiye.garden，未实现专注、植物收藏、统计与角色后台。

前端交付入口：

- [用户名快速对接](docs/api/username-auth-api.md)：无邮箱/验证码注册、登录、请求与响应示例。
- [认证接口文档](docs/api/auth-api.md)：全部接口、JSON示例、Cookie/CSRF、SMTP、本地运行与错误处理。
- [任务接口文档](docs/api/task-api.md)：字段、分页、状态与用户隔离。
- [认证TS类型](docs/api/auth-types.ts)、[任务TS类型](docs/api/task-types.ts)、[共用请求层](docs/api/auth-client.ts)、[任务请求](docs/api/task-client.ts)、[现有前端映射](docs/api/task-adapter.ts)。
- [完整curl交互脚本](docs/examples/auth-flow.sh)：保存Cookie，获取并携带CSRF，注册/登录/改密/退出。密码与验证码在终端输入，不打印Session ID。

## 依赖与兼容性

| 依赖 | 版本 | 依据 |
| --- | --- | --- |
| Spring Boot及Web MVC/Validation/Flyway/Security/Session JDBC/Mail/Test starter | 4.1.2 | 保持原parent，BOM管理；不混用Boot大版本 |
| Spring Security | 7.1.2 | Boot BOM；使用框架PBKDF2编码器和SecurityContext |
| Spring Session JDBC | 4.1.1 | Boot BOM；V4使用该版本JAR内官方MySQL schema |
| MyBatis-Plus Boot 4 starter / jsqlparser | 3.5.18 | [官方安装](https://baomidou.com/getting-started/install/)和[分页文档](https://baomidou.com/plugins/pagination/) |
| Flyway Core/MySQL | 12.4.0 | Boot BOM；[独立MySQL模块](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql) |
| MySQL Connector/J | 9.7.0 | Boot BOM |
| Lombok | 1.18.48 | Boot BOM；保留Java 21注解处理 |

认证兼容性与实现依据见[认证文档](docs/api/auth-api.md#1-方案依赖与部署边界)，包括Spring官方Session、CSRF与密码存储说明。Maven Wrapper实测Maven 3.10.0、Java 21.0.2。

## 建库和迁移

使用MySQL 8.0.16+，本次验证为8.4.10。先由管理员执行真实建库文件：

```sh
mysql -h localhost -u <管理员账号> -p < sql/00_create_database.sql
```

脚本只创建focus_garden、utf8mb4，不创建账号或默认密码。应用使用已配置的数据库账号；专用账号示例中的密码由你自行填写：

```sql
CREATE USER 'focus_garden'@'localhost' IDENTIFIED BY '<自行设置本地密码>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON focus_garden.* TO 'focus_garden'@'localhost';
```

账号host应与实际连接来源一致。启动时Flyway依次执行：

1. V1__create_tasks.sql：原有任务表。
2. V2__complete_task_schema.sql：原有字段注释、植物偏好、逻辑删除。
3. V3__users_and_authentication.sql：用户、HMAC验证码槽、持久化限流。
4. V4__spring_session_jdbc.sql：官方Session及属性表。
5. V5__task_user_ownership.sql：用户归属外键与组合索引。
6. V6__username_password_authentication.sql：唯一用户名、可空邮箱、身份CHECK。

V1–V5未修改。V6保留现有邮箱账号，不自动分配用户名；用户名账号email=NULL。已执行版本不会重复运行；后续新增V7。禁止手工重复执行Flyway管理的脚本、删库重建或启用baseline/clean绕过错误。Spring Session自动建表已关闭。

历史任务user_id=NULL保留并隔离，所有新任务从认证上下文设置归属。只有管理员核实后才能使用[逐项归属脚本](sql/10_assign_legacy_tasks.sql)填入映射，不自动分给新用户；脚本默认空映射不修改任务。

## 环境变量和本地启动

```sh
test -f .env || cp .env.example .env
# 编辑.env，保留现有数据库配置，填写SMTP凭据
# 生成一次并保存到.env的AUTH_CODE_HMAC_KEY（至少32字节随机数的Base64）
openssl rand -base64 32
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

默认127.0.0.1:8080，API前缀/api。DB_USERNAME/DB_PASSWORD、AUTH_CODE_HMAC_KEY、SMTP_HOST、SMTP_FROM必须配置；SMTP_PORT默认587，SMTP_AUTH/SMTP_STARTTLS默认true，SMTP_SSL默认false。不要用数据库密码代替邮箱SMTP授权码。全部变量说明见[认证文档](docs/api/auth-api.md#2-sql历史任务与运行环境)。

`.env`和`.idea`被Git忽略，Spring Boot/IDEA不自动读取.env。IDEA点击GardenApplication前需在运行配置里设置这些变量。

若没有真实SMTP，可显式启用local-mail并运行本地Mailpit，操作见认证文档。该profile将邮件发送到localhost:1025捕获，查看收件箱localhost:8025；不会向公网邮箱投递，也不会打印验证码。生产禁止local-mail/integration，启用prod、HTTPS和Secure Cookie。

开发优先Vite代理/api；跨源需显式来源和credentials，不能使用通配符。登录前先GET /api/auth/csrf；所有写请求携带X-CSRF-TOKEN。登录/退出/改密后重新获取Token。原先无需认证的单用户任务API已关闭。

## 测试

```sh
./mvnw test
```

默认测试不依赖数据库或SMTP，不禁用Security过滤器。真实集成测试使用独立测试库（绝不能指向开发或生产库）：

```sql
CREATE DATABASE focus_garden_auth_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'focus_garden_test'@'localhost' IDENTIFIED BY '<自行设置测试密码>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON focus_garden_auth_test.* TO 'focus_garden_test'@'localhost';
```

配置TEST_DB_NAME=focus_garden_auth_test、TEST_DB_USERNAME/TEST_DB_PASSWORD，可选TEST_DB_HOST/TEST_DB_PORT，再运行：

```sh
./mvnw -Pintegration clean verify
```

AuthDatabaseIT启动真实HTTP服务器，用Cookie jar及真实CSRF交互，Session和业务数据均落MySQL；邮件只在测试代码中由Mockito捕获。测试使用随机邮箱，认证测试数据保留在独立测试库；TaskDatabaseIT事务回滚任务数据。测试库不可用时明确失败，不跳过。

## 最新验证记录（2026-11-12）

- 真实MySQL 8.4.10的独立focus_garden_auth_test库已执行V1–V6，应用成功启动；未启动或迁移用户开发库。
- 25项无数据库测试、17项MySQL/HTTP集成测试通过，0失败、0错误、0跳过；完成可执行JAR打包。
- 新增用户名注册/登录、无邮件调用、大小写归一、并发唯一冲突、非法/混合身份参数、禁用用户、用户名注册及登录限流；保留邮箱全部测试。
- 覆盖注册、邮箱规范化/重复冲突、错码/过期/用途/重复消费/并发消费、失败次数落库、SMTP失败回滚、登录/错误密码/禁用、会话轮换及MySQL持久化、CSRF、退出、过期、改密后旧密码和所有旧会话失效、用户任务隔离、历史任务隔离、邮箱/IP限流、Cookie与明确来源CORS。
- docs/api全部TS文件严格类型检查通过；curl脚本bash语法检查通过，新增用户名注册→登录→CSRF刷新→me→任务列表→退出流程已通过真实HTTP执行；无邮件调用。邮箱脚本完整交互未实际向SMTP发送，真实邮件服务商投递、生产HTTPS代理及浏览器跨域未验证。没有可读设计稿，未核对视觉设计。
- Maven全局仓库访问受阻，实际使用临时Central settings执行 `./mvnw -s /tmp/focus-garden-maven-settings.xml -B -Pintegration clean verify`；未改全局Maven配置，普通环境使用标准Wrapper命令。

## 变更范围

新增auth模块：controller、独立DTO、验证码Entity/Mapper、HMAC保护、SMTP发送、限流、注册/登录/重置服务、认证身份/会话校验与JSON安全响应。新增user模块：UserEntity、UserMapper、UserVO、当前用户接口。没有重复PO，没有角色后台。

修改SecurityConfig/WebConfig、统一异常处理、TaskEntity/TaskMapper/TaskService、application配置、pom及.env.example。新增V3/V4/V5/V6、历史归属SQL、prod/local-mail配置。更新原有测试并新增PasswordAndCodeTests、AuthDatabaseIT；新增认证文档、TS类型/请求层与交互脚本，并同步任务文档/请求。前端业务组件、mockService和后续模块未改动。

本次用户名扩展修改RegisterDTO、LoginDTO、AuthService、AuthenticationManager和登录Controller；EmailAddress规范化帮助类改为AccountIdentifier，供两种身份共用。UserEntity/UserMapper/UserVO补充username，UserVO.email可为null。AuthRateLimiter新增用户名注册限流，登录改为账号限流；不变更依赖版本。前端新增username-auth-api.md，并同步总认证文档、TS联合类型、curl示例和README。

## 查看接口是否到达后端

新增RequestAccessLogFilter，在Security过滤器外层记录/api请求的开始与结束，401/403/429也可见。停止并重新启动GardenApplication后，IDEA控制台会显示：

```text
[API] -> POST /api/auth/email-codes requestId=...
[API] <- POST /api/auth/email-codes status=429 durationMs=3 requestId=...
```

开始日志表示请求到达；结束日志的status为200表示接口成功、429表示限流、503表示邮件服务暂时不可用。SMTP接受邮件与收件箱最终投递不是同一件事。响应头X-Request-ID与日志requestId一致，跨域已暴露此头，便于从Network定位同一请求。访问日志不记录请求体、查询参数、Cookie或Authorization，避免泄露密码、验证码和Session ID。

涉及类重命名或删除时，应先停止旧JVM再重新构建启动，不能只依赖热更新。
