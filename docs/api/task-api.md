# 任务模块 API 对接文档

本文件是当前 `focus-garden-server` 实际任务接口契约。根包 `com.xiye.garden`；不实现专注、植物收藏和统计；用户认证接口见 [auth-api.md](auth-api.md)。已读取兄弟项目前端 `src/types/index.ts`、TaskForm、HTTP 配置及 `docs/api-contract.md`。前端建议契约仍标为“待确认”，以本文件的已实现行为为准。

仓库没有可读取的设计稿，连接页面快照也为空，因此未核对设计稿。已接入 Spring Security + MySQL Session。全部任务接口必须登录，并携带 Cookie；POST/PUT/DELETE 还必须携带 X-CSRF-TOKEN。先获取 /api/auth/csrf，再登录，登录成功后重新获取csrf。服务端从认证上下文确定用户，不接受userId输入；其他用户资源统一404。完整调用顺序见 [auth-api.md](auth-api.md)。

## 1. 初始化、启动与网络

依赖：Java 21、项目 Maven Wrapper、MySQL 8.0.16+，建议 MySQL 8.4。服务默认 `http://localhost:8080`，API 前缀 `/api`，绑定 `127.0.0.1`。

在后端项目根目录，**先手动执行建库脚本**（不含账号密码，不自动建用户）：

```sh
mysql -h localhost -u <管理员账号> -p < sql/00_create_database.sql
test -f .env || cp .env.example .env
# 编辑 .env，填写数据库、随机 AUTH_CODE_HMAC_KEY 和SMTP变量；本地Mailpit可启用local-mail，见auth-api.md
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

Spring Boot 不自动读取 `.env`，上面的 shell 命令负责导出。连接配置：`DB_HOST=localhost`、`DB_PORT=3306`、`DB_NAME=focus_garden`；`DB_USERNAME/DB_PASSWORD` 必填；账号须有该开发库的 SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/INDEX/REFERENCES 权限。账号创建参考根目录 README，密码不提交。端口可用 `SERVER_PORT` 调整。

首次启动 Flyway 执行 V1–V6：任务表、字段补全、用户/验证码/限流表、官方Session表、任务归属列与用户名登录身份。已有版本不重复执行；已执行迁移不修改，后续新增V7。禁止手动重复执行Flyway管理的脚本。历史任务user_id=NULL保持隔离，仅按核实后的显式映射由管理员迁移，见auth-api.md。

前端当前已有 Axios，设置 `VITE_API_BASE_URL=http://localhost:8080/api` 可使用后端 CORS：默认允许精确源 `http://localhost:5173`，更换前端端口后同步设置 `CORS_ALLOWED_ORIGINS`，多个源逗号分隔。该变量配置不会自动将现有 mockService 切为 HTTP。

也可在现有 `vite.config.ts` 的 defineConfig 中加入：

```ts
server: {
  proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } },
},
```

这时 Axios baseURL 保持 `/api`，浏览器同源访问；Cookie仍用于认证，所有写请求还需CSRF Token。当前仓库尚未配置这个代理；以上为可复制配置。健康探测 `GET /api/health` 为进程存活检查，不是数据库实时可用性探测。

## 2. 表结构与业务约定

表 `tasks`：MySQL InnoDB / utf8mb4_0900_ai_ci，主键 BIGINT AUTO_INCREMENT，由数据库生成；只有 TaskEntity 一个数据库映射，没有 TaskPO。V1/V2 保持原文件，V5 增量添加用户归属。

| 数据库字段 | Java / JSON | 类型、默认与含义 |
| --- | --- | --- |
| id | Long / string | BIGINT，自增，不接受请求设置 |
| user_id | Long / 不返回 | BIGINT，服务端认证身份；历史NULL任务隔离 |
| title | String / string | VARCHAR(100) NOT NULL，无默认；标题 |
| description | String / string | VARCHAR(2000) NOT NULL DEFAULT '' |
| planned_focus_minutes | Integer / number | INT NOT NULL，无默认；1–1440 整数分钟 |
| status | TaskStatus / string | VARCHAR(20) NOT NULL，无默认；TODO/IN_PROGRESS/COMPLETED |
| plant_type | String / string | VARCHAR(16) NOT NULL DEFAULT 'fern'；fern/daisy/cactus/tulip |
| created_at | LocalDateTime / string | DATETIME(6) NOT NULL，服务端生成，无数据库默认 |
| updated_at | LocalDateTime / string | DATETIME(6) NOT NULL，服务端生成，无数据库默认 |
| completed_at | LocalDateTime / string 或 null | DATETIME(6) NULL DEFAULT NULL |
| deleted | Boolean / 不返回 | TINYINT NOT NULL DEFAULT 0，0 正常、1 逻辑删除 |

CHECK 约束校验分钟数、状态、植物类型、删除标记及完成时间一致性。索引仅有主键、`(user_id,deleted,status,created_at,id)` 和 `(user_id,deleted,created_at,id)`，分别服务状态筛选与默认排序；标题为子串查询，不盲目建普通 title 索引，其他排序字段允许但不保证索引排序。

- 标题请求原始长度 1–100（Java/JS 字符串 UTF-16 长度），拒绝空白字符串，保存时去首尾空白。描述不 trim。
- 已完成任务允许编辑。状态允许相互转换，与专注会话状态没有自动绑定。客户端不能设置 completedAt。
- 创建/修改为 COMPLETED 时服务端补 completedAt；已完成任务保持 COMPLETED 编辑时保留原完成时间；改回 TODO/IN_PROGRESS 清空 completedAt。
- complete 接口只完成整个任务，不能完成一轮专注，不计时、不创建专注记录、不发放植物。重复完成原样返回，不再次写数据库、不改变首次 completedAt/updatedAt。
- plantType 仅为任务下轮专注偏好，不是已获得的植物；当前没有后端专注模块，也没有“活跃专注时禁止删除或完成”的服务端规则，不能把前端模拟规则当作已实现后端能力。
- 删除使用逻辑删除；数据库保留任务行，列表/详情/修改/完成均排除已删除任务。删除时更新 updatedAt，不清除完成时间。不提供恢复接口，重复删除返回 404。
- 当前没有专注表。未来关联必须保留专注记录，保存任务名、计划时长、植物类型快照，不使用 ON DELETE CASCADE；不依赖普通任务详情接口读取历史标题。当前改名/逻辑删除不会触发专注数据改写。历史上在 V1 物理删除的任务无法通过 V2 恢复。
- 更新/删除/完成均先在事务内对正常任务行 SELECT FOR UPDATE；并发写串行处理。普通 PUT 为后写覆盖语义，没有版本冲突检测。

## 3. 传输与统一响应

所有 task ID（含 Location 路径、前端路由参数）使用十进制字符串，范围 1–9223372036854775807。JSON `id` 即使很小也返回字符串，不要使用 Number/parseInt 转换，避免超出 JavaScript 安全整数范围后精度损失。后端路径解析为正 Long。

数据库 DATETIME(6) 和 JDBC 会话均用 UTC，服务端 UTC Clock 生成时间并截到微秒；API 为 ISO 8601 UTC 字符串，例如 `2026-11-12T03:00:00Z`（可有最多 6 位小数）。前端显示时按用户本地时区格式化。completedAt 未完成为 null；description 无内容为 `""`，响应字段不使用 undefined。

成功：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "id": "1",
    "title": "完成任务模块",
    "description": "接口联调",
    "plannedFocusMinutes": 25,
    "status": "TODO",
    "plantType": "fern",
    "createdAt": "2026-11-12T03:00:00Z",
    "updatedAt": "2026-11-12T03:00:00Z",
    "completedAt": null
  }
}
```

分页 data：`{page,size,total,records}`。page/size 是 number；total 是记录总数；records 永远是数组。

失败使用非 2xx HTTP 状态，body 为 `{code,message,data:null}`。业务未定义独立数字错误码，任务通用错误 code 复用 HTTP 状态名，认证扩展业务码见auth-api.md；前端按 HTTP status/code 判断，message 供用户展示，不应解析中文文字作为逻辑条件。

## 4. 请求字段与缺省语义

TaskCreateDTO 与 TaskUpdateDTO 是不同 Java DTO；JSON 字段相同。PUT 为可编辑字段的完整替换，**不是 PATCH**：

| 字段 | 必填 | 缺省/null/空值行为 | 校验 |
| --- | --- | --- | --- |
| title | 是 | 缺省/null/空串/纯空白拒绝 | 最多100，保存前 strip |
| description | 否 | 缺省/null/空串均保存空字符串；PUT 可据此清空 | 最多2000 |
| plannedFocusMinutes | 是 | 缺省/null拒绝，不从用户设置推断 | JSON 整数，1–1440；25.5 拒绝 |
| status | 是 | 缺省/null拒绝，无隐式默认；新增通常显式 TODO | 只接受三个大写枚举，数字枚举拒绝 |
| plantType | 否 | 缺省/null均为 fern，包括 PUT；保留原值需显式回传 | fern/daisy/cactus/tulip，空串拒绝 |

不要使用 Partial<TaskUpdateDTO> 提交 PUT。不要发送 userId、id、createdAt、updatedAt、completedAt、deleted；它们不是可写字段。

## 5. 六个接口

### 5.1 新增任务

`POST /api/tasks`，`Content-Type: application/json`，body 为 TaskCreateDTO：

```json
{
  "title": "完成任务模块",
  "description": "接口联调",
  "plannedFocusMinutes": 25,
  "status": "TODO",
  "plantType": "fern"
}
```

响应 HTTP **201**，`Location: /api/tasks/1`：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "id": "1",
    "title": "完成任务模块",
    "description": "接口联调",
    "plannedFocusMinutes": 25,
    "status": "TODO",
    "plantType": "fern",
    "createdAt": "2026-11-12T03:00:00Z",
    "updatedAt": "2026-11-12T03:00:00Z",
    "completedAt": null
  }
}
```

### 5.2 分页查询任务

`GET /api/tasks?page=1&size=20&status=TODO&title=任务&sortBy=createdAt&direction=desc`，无请求 body。

| Query 参数 | 默认 | 规则 |
| --- | --- | --- |
| page | 1 | 整数1–1000000；超过总页数返回该页码与空 records |
| size | 20 | 整数1–100；本接口名称是 size，不是 pageSize |
| title | 不筛选 | 最多100，去首尾空白；空白忽略；字面子串匹配，%/_无通配作用 |
| status | 不筛选 | TODO/IN_PROGRESS/COMPLETED；空字符串按不筛选处理，其他非法值拒绝 |
| sortBy | createdAt | 仅 id/createdAt/updatedAt/plannedFocusMinutes |
| direction | desc | 仅 asc/desc，小写 |

多个筛选为 AND。标题按数据库 collation 不区分大小写；相同排序值按 id desc 次排序，sortBy=id 时只使用请求方向。合法排序才映射为 SQL 列名；参数绑定，不拼接任意排序片段。Query 可选项不需要时省略，不发送 null/undefined 字面字符串。

响应 HTTP **200**：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "page": 1,
    "size": 20,
    "total": 1,
    "records": [
      {
        "id": "1",
        "title": "完成任务模块",
        "description": "接口联调",
        "plannedFocusMinutes": 25,
        "status": "TODO",
        "plantType": "fern",
        "createdAt": "2026-11-12T03:00:00Z",
        "updatedAt": "2026-11-12T03:00:00Z",
        "completedAt": null
      }
    ]
  }
}
```

空列表/超出总页数 HTTP **200**：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "page": 1,
    "size": 20,
    "total": 0,
    "records": []
  }
}
```

### 5.3 详情

`GET /api/tasks/1`，无请求 body，响应 HTTP **200**：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "id": "1",
    "title": "完成任务模块",
    "description": "接口联调",
    "plannedFocusMinutes": 25,
    "status": "TODO",
    "plantType": "fern",
    "createdAt": "2026-11-12T03:00:00Z",
    "updatedAt": "2026-11-12T03:00:00Z",
    "completedAt": null
  }
}
```

### 5.4 修改任务

`PUT /api/tasks/1`，`Content-Type: application/json`，body 为 TaskUpdateDTO：

```json
{
  "title": "完成任务模块与文档",
  "description": "",
  "plannedFocusMinutes": 50,
  "status": "IN_PROGRESS",
  "plantType": "daisy"
}
```

响应 HTTP **200**：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "id": "1",
    "title": "完成任务模块与文档",
    "description": "",
    "plannedFocusMinutes": 50,
    "status": "IN_PROGRESS",
    "plantType": "daisy",
    "createdAt": "2026-11-12T03:00:00Z",
    "updatedAt": "2026-11-12T03:05:00Z",
    "completedAt": null
  }
}
```

已完成任务也可使用此接口；description null/缺省会清空，plantType null/缺省会重置 fern；其余必填字段缺省返回400。保持 COMPLETED 编辑保留完成时间；主动传 TODO/IN_PROGRESS 表示重新打开任务。

### 5.5 删除任务

`DELETE /api/tasks/1`，无请求 body，不发送 JSON null。响应 HTTP **200**：

```json
{
  "code": "OK",
  "message": "成功",
  "data": null
}
```

这是逻辑删除，无 204；删除后详情/编辑/再次删除/完成返回404，其他任务和已有历史记录不受影响。

### 5.6 完成任务

`POST /api/tasks/1/complete`，无请求 body，不需要时长或完成时间。响应 HTTP **200**：

```json
{
  "code": "OK",
  "message": "成功",
  "data": {
    "id": "1",
    "title": "完成任务模块与文档",
    "description": "",
    "plannedFocusMinutes": 50,
    "status": "COMPLETED",
    "plantType": "daisy",
    "createdAt": "2026-11-12T03:00:00Z",
    "updatedAt": "2026-11-12T03:30:00Z",
    "completedAt": "2026-11-12T03:30:00Z"
  }
}
```

对同一已完成任务再次调用返回同一个任务及完成时间；没有奖励或统计副作用。若先显式重新打开任务，再次完成会生成新的完成时间。

## 6. HTTP 状态与错误例子

| HTTP | code | 场景 |
| --- | --- | --- |
| 200 | OK | 查询/修改/删除/完成成功 |
| 201 | OK | 创建成功，Location 为详情路径 |
| 400 | BAD_REQUEST | Bean Validation、非法 JSON、非正/溢出ID、非法枚举/分页/排序 |
| 404 | NOT_FOUND | 不存在或逻辑删除的任务；不存在的接口 |
| 405 | METHOD_NOT_ALLOWED | 例如 PATCH，本模块仅支持 PUT 修改 |
| 406 | NOT_ACCEPTABLE | 只接受无法生成的响应格式，例如 application/xml |
| 415 | UNSUPPORTED_MEDIA_TYPE | 非 application/json 的请求体 |
| 500 | INTERNAL_SERVER_ERROR | 未预期服务错误；不暴露数据库/堆栈 |

现已接入认证：401 UNAUTHORIZED 表示未登录/过期，403 CSRF_INVALID 表示安全令牌错误，429 RATE_LIMITED 表示认证请求限流。仍未实现409活跃专注冲突。

任务不存在、已删除或属于其他用户：

```json
{
  "code": "NOT_FOUND",
  "message": "任务不存在",
  "data": null
}
```

参数错误例：发送 `{"title":"","plannedFocusMinutes":25,"status":"TODO"}`：

```json
{
  "code": "BAD_REQUEST",
  "message": "title：标题不能为空",
  "data": null
}
```

非法排序例 `GET /api/tasks?sortBy=title`：

```json
{
  "code": "BAD_REQUEST",
  "message": "sortBy：排序字段仅支持 id、createdAt、updatedAt、plannedFocusMinutes",
  "data": null
}
```

多个字段错误按字段排序并用中文分号拼接。JSON 格式/数字枚举/小数分钟等反序列化错误返回通用中文参数错误，具体文案以运行响应为准。

## 7. 前端 TypeScript 与映射

可直接复制的完整类型见 [task-types.ts](task-types.ts)，包含 ApiResponse、ApiErrorResponse、PageResponse、TaskCreateDTO、TaskUpdateDTO、TaskQueryDTO、TaskVO 与枚举；共用Cookie/CSRF请求实现见 [auth-client.ts](auth-client.ts) 和 [task-client.ts](task-client.ts)，与现有前端的转换函数见 [task-adapter.ts](task-adapter.ts)。复制到前端 services 目录时保持三个文件相对关系或调整 import，所有前端函数均为箭头函数。不必新增请求库，现有 Axios 也可直接套用这些类型：

```ts
// Axios必须开启withCredentials，并为写请求加X-CSRF-TOKEN
const response = await http.get<ApiResponse<PageResponse<TaskVO>>>('/tasks', {
  params: { page: 1, size: 20, status: 'TODO' },
});
const page = response.data.data;
```

| 现有前端类型 | HTTP 契约 | 适配 |
| --- | --- | --- |
| id:string | id:string | 原样保留 |
| notes | description | 重命名 |
| plannedMinutes | plannedFocusMinutes | 均为整数分钟；前端表单1–180，后端可接受1–1440 |
| plantType | plantType | 原样保留，不关联收藏 |
| pending | TODO 或 IN_PROGRESS | 两者显示为pending；编辑时保留原 VO 状态 |
| completed | COMPLETED | 完成按钮调用 complete，而不是完成专注 |
| createdAt/completedAt:number | ISO UTC string/null | Date.parse 转毫秒；null原样保留 |
| 无 updatedAt | updatedAt:string | 可由请求缓存保留 |

不能将前端 pending 编辑直接回写成 TODO，否则会覆盖原 IN_PROGRESS；适配函数要求传入原始 VO。筛选“全部未完成”目前没有 OR 状态查询参数，不要只查 TODO 然后遗漏 IN_PROGRESS；可分别分页查询两种状态，或不传 status 获取全部分页后由界面分组（不要只抓第一页就当作全量）。建议前端对接时保留后端三态作为查询状态，避免双流分页混排。

当前 `GardenService.read()` 和 mutation 返回 GardenData 聚合结构，后端此阶段仅返回任务资源，不提供 GardenData、专注、收藏或设置接口。**不要直接把 task-client 替换为整套 GardenService，也不要把真实任务 ID 混入本地 mock 专注流程当作服务端完整联调。** 本次只交付任务资源对接类型和请求函数，前端页面接入另行实施。

## 8. 刷新与交互建议

- 新增成功：使用响应任务作即时反馈，将当前任务列表查询失效并重新请求第一页；新任务可能不符合当前状态筛选。
- 编辑成功：更新详情缓存，并重新请求带原筛选/排序的当前页；任务可能离开筛选结果或改变位置。
- 完成成功：刷新详情和列表，不能触发 focus complete；重复点击可禁用按钮减少请求，服务端仍保证幂等。
- 删除成功：移除详情缓存、刷新列表；若当前页为空且 page>1，回退一页再查询。
- 400保留表单输入并显示message；404清理失效选择并刷新列表；网络超时下不要自动重试创建（POST新增没有幂等键），先刷新核对。complete可重试；删除重试后404可提示已不存在。
- 不重置历史专注/植物缓存，不根据任务状态修改专注会话状态。后续模块上线后再统一协调聚合刷新。

## 9. 执行示例与验证范围

[../requests.http](../requests.http) 包含六个接口及错误请求，可用 IntelliJ HTTP Client/VS Code REST Client 顺序执行，创建后把字符串 data.id 填入 taskId。真实建库脚本：[../../sql/00_create_database.sql](../../sql/00_create_database.sql)。

`./mvnw verify` 执行不依赖数据库的 MVC/Service 测试与打包。真实数据库测试使用专用 `focus_garden_test` 空库与 TEST_DB_USERNAME/TEST_DB_PASSWORD，执行 `./mvnw -Pintegration verify`；请使用仅有测试库权限的账号，不连接生产库。

本次 SQL 执行情况和最终检查结果见根目录 README 的最新验证记录。V1–V6 已在独立MySQL测试库执行，任务和认证集成测试通过。真实SMTP投递与生产HTTPS尚未验证。项目没有OpenAPI配置，不新增框架。
