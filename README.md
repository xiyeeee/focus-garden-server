# 专注花园后端

任务模块：Spring Boot 单体，本地单用户任务 API。保留现有根包 `com.xiye.garden`、Java 21、Spring Boot 4.1.2，未实现用户、专注、植物或统计模块。

## 依赖与兼容性

| 依赖 | 版本 | 依据 |
| --- | --- | --- |
| Spring Boot / Web MVC / Validation / Test / Flyway starter | 4.1.2 | 现有 parent，不升级或降级；[Boot 4 模块化说明](https://spring.io/blog/2025/10/28/modularizing-spring-boot/)要求 Flyway 自动配置使用 starter |
| MyBatis-Plus Boot 4 starter / jsqlparser 模块 | 3.5.18 | [官方安装文档](https://baomidou.com/getting-started/install/)提供的正式 Boot 4 starter；[分页文档](https://baomidou.com/plugins/pagination/)要求额外分页模块 |
| Flyway Core / MySQL 模块 | 12.4.0 | Boot BOM 管理；starter 传递引入 Core，[Flyway MySQL 文档](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)要求单独数据库模块 |
| MySQL Connector/J | 9.7.0 | Boot BOM 管理 |
| Lombok | 1.18.48 | Boot BOM 管理；[官方 changelog](https://projectlombok.org/changelog)从 1.18.30 支持 JDK 21；保留主代码和测试的 annotationProcessorPaths |

没有重复添加 MyBatis starter，没有 Redis、消息队列、Spring Cloud 或鉴权依赖。Maven Wrapper 实测 Maven 3.10.0，JDK 21.0.2。

## 本地初始化和启动

完整前端契约：[task-api.md](docs/api/task-api.md)。可复制类型：[task-types.ts](docs/api/task-types.ts)，请求函数：[task-client.ts](docs/api/task-client.ts)，前端映射：[task-adapter.ts](docs/api/task-adapter.ts)。

使用 MySQL 8.0.16+（需要执行 CHECK 约束），建议 MySQL 8.4。先由管理员执行真实建库文件，不包含账号密码：

```sh
mysql -h localhost -u <管理员账号> -p < sql/00_create_database.sql
```

再创建专用账号，以下密码占位符必须自行替换，不要提交真实值：

```sql
CREATE USER 'focus_garden'@'localhost' IDENTIFIED BY '<自行设置本地密码>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON focus_garden.* TO 'focus_garden'@'localhost';
```

账号 host 应与实际连接来源匹配。应用账号需要执行 Flyway 迁移的 DDL 权限；库由管理员创建。不要对已有业务库启用 baseline，也不要手工先建 tasks 表。

```sh
cp .env.example .env
# 编辑 .env 替换账号密码；特殊字符使用 shell 单引号包裹
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

`.env` 只作为本地 shell 配置，Spring Boot 不自动加载它。所有密码均来自环境变量，缺失时启动失败。默认监听 `127.0.0.1:8080`。`DB_HOST/DB_PORT/DB_NAME` 默认 `localhost/3306/focus_garden`；`DB_USERNAME/DB_PASSWORD` 必填。端口与监听地址可通过 `SERVER_PORT/SERVER_ADDRESS` 调整。

首次启动由 Flyway 依次执行 V1__create_tasks.sql 和 V2__complete_task_schema.sql。已有 V1 的库仅执行 V2；V1 保持原样。不要手动重复执行 Flyway 管理的 SQL；后续变更新增 V3 等文件，已执行迁移不得修改。默认禁止 Flyway clean。

跨域仅放行 `CORS_ALLOWED_ORIGINS` 指定的精确源（逗号分隔），默认 `http://localhost:5173`；也可通过前端开发代理访问 `/api`。

## 接口契约

请求示例见 [docs/requests.http](docs/requests.http)，可直接用 IntelliJ HTTP Client 或 VS Code REST Client 执行。

| 方法 | 路径 | 成功状态 |
| --- | --- | --- |
| GET | `/api/health` | 200，进程存活检查，不检查数据库实时可用性 |
| POST | `/api/tasks` | 201，包含 Location |
| GET | `/api/tasks` | 200 |
| GET | `/api/tasks/{id}` | 200 |
| PUT | `/api/tasks/{id}` | 200 |
| DELETE | `/api/tasks/{id}` | 200，data 为 null |
| POST | `/api/tasks/{id}/complete` | 200 |

统一响应为 `{ "code": "OK", "message": "成功", "data": ... }`，失败以 HTTP 400/404/405/415/500 等状态返回，code 为状态名，message 为中文。内部错误写日志，不回传数据库信息或堆栈。

新增和 PUT 使用独立 DTO：title 必填且最多 100 字符；description 可省略或为 null，统一为 `""`，最多 2000 字符；plannedFocusMinutes 必填，整数 1–1440；status 必填，只接受 `TODO/IN_PROGRESS/COMPLETED`。plantType 可省略或为 null，统一为 fern，只支持 fern/daisy/cactus/tulip。PUT 完整替换：description 缺省或 null 清空，plantType 缺省或 null 重置为 fern，其余必填项不得省略。客户端不能设置 ID、创建或完成时间。所有 JSON ID 均返回十进制字符串，前端不能转成 Number。

列表参数：page 默认 1、范围 1–1000000；size 默认 20、范围 1–100；可选 status、title（去首尾空白、字面子串匹配，`%`/`_` 不是通配符；按数据库排序规则忽略大小写）。sortBy 只接受 `id/createdAt/updatedAt/plannedFocusMinutes`，默认 createdAt；direction 只接受 asc/desc，默认 desc。次排序为 id desc，保证相同排序值时结果稳定。分页响应 data：`{page,size,total,records}`。

任务状态允许相互切换；设为 COMPLETED 时由服务端设置 completedAt，编辑已完成任务保留原完成时间；重新打开为 TODO/IN_PROGRESS 时清空完成时间。complete 接口重复调用不修改首次 completedAt 或 updatedAt。写操作在事务中对目标行加锁，串行处理并发状态变化；普通 PUT 使用后写覆盖语义。删除为逻辑删除，保留任务行；列表与操作均排除已删除任务，不级联删除历史数据。不存在或已删除任务的读、改、删、完成均返回 404。

时间策略：服务端注入 UTC Clock，MySQL DATETIME(6) 存 UTC，JDBC 会话 UTC；API 时间为 ISO 8601 UTC 字符串（例如 `2026-11-12T02:00:00Z`）。前端按用户时区显示。createdAt/updatedAt/completedAt 均由服务端维护。

本阶段没有用户归属字段或可信 userId 输入，仅面向本地单用户。多用户上线前必须完成鉴权、任务归属校验、数据隔离，并评估既有数据迁移；任务完成不等同于完成一次专注，当前不产生时长或植物。

## 测试

```sh
./mvnw test
./mvnw verify
```

默认执行健康接口、参数校验、HTTP 错误、状态转换、完成时间和不存在资源测试，不依赖数据库。原有 contextLoads 调整为 MVC 上下文检查；完整应用与数据库启动检查由独立集成测试承担。

数据库集成测试只能连接独立 `focus_garden_test` 库，并使用只有该库权限的专用账号：

```sql
CREATE DATABASE focus_garden_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'focus_garden_test'@'localhost' IDENTIFIED BY '<自行设置测试密码>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON focus_garden_test.* TO 'focus_garden_test'@'localhost';
```

设置 `TEST_DB_USERNAME/TEST_DB_PASSWORD`，可选 `TEST_DB_HOST/TEST_DB_PORT` 后执行：

```sh
./mvnw -Pintegration verify
```

集成测试使用真实 MySQL、Flyway 和 Mapper，覆盖应用启动、迁移、utf8mb4、CRUD、分页、筛选和 completedAt 清空持久化。测试事务回滚业务数据，迁移历史保留；请使用专用空测试库。测试库不可用时该命令明确失败，不跳过断言或禁用数据库初始化。

## 本次任务模块验证记录（2026-11-12）

- 项目 Maven Wrapper、Java 21 / Maven 3.10.0 执行 clean verify：22 项测试通过，0 失败、0 错误、0 跳过，完成可执行 JAR 打包。覆盖大 ID 字符串、校验、分页参数白名单、404、重复完成、逻辑删除调用与服务端时间。
- docs/api 下的 task-types.ts、task-client.ts、task-adapter.ts 使用现有前端 TypeScript 编译器 strict / noEmit 检查通过，未新增依赖。
- 全局 Maven 仓库访问受阻，本次通过临时 settings 直连 Maven Central，未修改项目或全局 Maven 配置。实际命令为 `./mvnw -s /tmp/focus-garden-maven-settings.xml -B clean verify`，正常环境使用标准 Wrapper 命令即可。
- 已尝试 `-Pintegration verify`，21 项无数据库测试通过，但独立测试库 127.0.0.1:3306/focus_garden_test 连接被拒绝，1 项数据库集成测试报错，未跳过。Docker daemon 未运行，本机没有可用 MySQL 客户端/服务端。
- **SQL 尚未在 MySQL 实际执行验证**：包括 sql/00_create_database.sql、V1、V2；实际迁移、数据库读回、分页筛选、逻辑删除隔离、锁并发及完整应用启动尚未验证，不能视为通过。
- 下一步：创建独立空测试库并执行 `./mvnw -Pintegration verify`；通过后配置开发库、启动服务并按 requests.http 对接。没有鉴权，不做跨用户权限测试，不扩展后续模块。

本次实际修改/新增文件：

- task/entity/TaskEntity.java 替换原 Task，增加植物偏好和逻辑删除映射；TaskStatus 保持原样。
- task/dto/TaskCreateDTO.java、TaskUpdateDTO.java、TaskQueryDTO.java 替换原 TaskRequest/TaskQuery，新增与修改 DTO 分开，保留既有字段校验与分页参数。
- task/vo/TaskVO.java 替换原 TaskResponse，ID 字符串和植物偏好。
- task/mapper/TaskMapper.java、task/service/TaskService.java、task/controller/TaskController.java 保持六个路径，实现逻辑删除过滤、服务端时间与完成幂等。
- sql/00_create_database.sql、src/main/resources/db/migration/V2__complete_task_schema.sql 为真实可执行 SQL；原 V1 没有改动。
- src/test/java/com/xiye/garden/task/TaskControllerTests.java、TaskServiceTests.java、src/test/java/com/xiye/garden/TaskDatabaseIT.java 更新类型，补充404、大ID、植物类型、逻辑删除及真实持久化断言。
- docs/api/task-api.md、task-types.ts、task-client.ts、task-adapter.ts、docs/requests.http 和本 README。

Java 业务文件均在 src/main/java/com/xiye/garden 下。本次未改动依赖版本、启动类、统一基础设施或前端业务代码。逻辑删除遵循 [MyBatis-Plus 官方文档](https://baomidou.com/guides/logic-delete/)，自定义锁定 SQL 显式增加 deleted=0。
