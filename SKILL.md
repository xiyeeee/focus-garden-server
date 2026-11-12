---
name: focus-garden-backend
description: 为专注花园现有 Spring Boot 项目配置依赖、数据库和后端基础设施，并分阶段实现任务、专注记录、植物收藏和统计接口。
---

# 专注花园后端开发

## 项目背景

- 项目已经创建，直接在现有项目上开发。
- 项目目录名：focus-garden-server。
- Maven Group：com.xiye。
- 推荐根包：com.xiye.garden，以实际代码为准。
- Java：21。
- Spring Boot：以 pom.xml 实际版本为准，创建时选择了 4.1.2。
- 数据库：MySQL。
- 前端：React + TypeScript，前后端分离。
- 架构：Spring Boot 单体，按业务模块分包。

## 开始前检查

1. 阅读现有 pom.xml、配置文件、启动类和测试类。
2. 确认实际 Java、Spring Boot、Maven 和包名。
3. 保留用户已创建的文件与代码，避免重新初始化项目。
4. 检查启动类与测试类包路径是否一致。
5. 从官方文档核实第三方依赖对当前 Spring Boot 的支持。
6. 不猜测版本号，不混用不同 Spring Boot 大版本的 starter。
7. 不擅自升级或降级 Spring Boot。
8. 若存在兼容性阻塞，说明具体依赖和可行方案。

## 依赖配置

检查并按需补充：

- Spring Web / Spring Web MVC
- Validation
- Lombok
- MySQL Connector/J
- 与当前 Spring Boot 大版本匹配的 MyBatis-Plus starter
- Flyway Core
- Flyway 对应的 MySQL 数据库支持模块
- Spring Boot Test

要求：

- 优先使用 Spring Boot BOM 管理的依赖版本。
- 第三方依赖选择经过官方兼容性核实的正式版本。
- 使用 MyBatis-Plus 时，避免重复引入 MyBatis starter。
- 分页需要额外模块时，按所选 MyBatis-Plus 版本配置。
- 核查 Java 21 下 Lombok 注解处理配置。
- 暂不引入 Redis、消息队列、Spring Cloud、网关和注册中心。
- 登录鉴权在实现用户模块时再配置，不提前添加整套框架。

## 代码组织

在实际根包下按业务分包：

- common：统一响应、异常处理、分页结构
- config：Web、MyBatis-Plus 等配置
- task：任务
- focus：专注会话
- plant：植物及用户收藏
- statistics：统计
- user：用户，后续阶段实现

每个业务模块按需建立：
controller、service、mapper、entity、dto、vo。

保持实现简单，不创建空模块占位，不添加无实际用途的抽象层。
Controller 负责参数与响应，Service 负责业务规则，
Mapper 负责数据库访问。

## 第一阶段：基础设施和任务接口

本次默认只完成第一阶段。

1. 配置并验证 Maven 依赖。
2. 配置 application.yml，使用环境变量读取数据库账号密码。
3. 提供本地运行所需环境变量说明，不提交真实凭据。
4. 使用 Flyway 编写初始建表迁移，采用 utf8mb4。
5. 已执行的迁移文件不得修改，后续变更新增迁移。
6. 建立统一响应结构和全局异常处理。
7. 保留正确 HTTP 状态码，不把所有失败都返回 HTTP 200。
8. 实现参数校验，并返回清晰的中文错误消息。
9. 提供 GET /api/health 健康检查接口。
10. 实现任务新增、分页查询、详情、修改、删除和完成接口。
11. 提供 curl 或 .http 请求示例。
12. 编译并运行相关测试。

任务建议字段：

- id
- title
- description
- plannedFocusMinutes
- status：TODO、IN_PROGRESS、COMPLETED
- createdAt
- updatedAt
- completedAt

实现要求：

- 校验标题长度、专注分钟数和状态值。
- 查询支持分页，以及按状态、标题筛选。
- 排序字段使用白名单。
- 实体类不直接作为新增、修改请求体。
- 完成任务时维护 completedAt。
- 返回不存在资源时使用 404。
- 时间格式与时区策略保持一致并写明。
- 第一阶段作为本地单用户开发版本。
- 不把请求传入的 userId 当作可信身份。
- 多用户上线前必须完成鉴权和数据归属校验。

## 后续阶段

第一阶段验收后，再逐步实现：

1. 用户注册、登录、鉴权及用户数据隔离。
2. 专注会话开始、暂停、恢复、完成和放弃。
3. 专注完成后生成植物收藏。
4. 今日、每周专注统计和成长记录。
5. 用户专注与休息时长设置。

专注模块必须遵循：

- 服务端记录时间并计算有效专注时长。
- 不直接相信前端提交的累计时长或完成状态。
- 使用明确的状态转换规则。
- 同一用户最多存在一个进行中或暂停的专注会话，
  并处理并发请求。
- 刷新页面后能通过接口恢复会话。
- 重复完成请求不得重复累计时长或发放植物。
- 会话完成、统计更新和植物发放保持事务一致性。
- 区分“完成一次专注”和“完成整个任务”。
- 明确跨天统计和暂停时间的计算规则。

## 验证与交付

- 使用项目自带 Maven Wrapper 执行检查。
- 对参数校验、任务状态变化和不存在资源编写必要测试。
- 数据库集成测试使用独立测试数据库。
- 不通过禁用测试或删除断言掩盖失败。
- 数据库不可用时，明确哪些检查未执行。
- 输出实际变更文件、依赖版本和选择依据。
- 给出数据库初始化、环境变量配置和启动步骤。
- 汇报已验证结果、未验证事项及下一步。
- 完成代码和验证，不只输出建议或伪代码。
