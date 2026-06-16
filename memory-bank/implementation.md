# 研伴 Agent - 实施计划(AI 开发者分步指令)

> 文档作用概述：记录**未来执行计划、阶段步骤、验证方式与执行顺序**。A/B/C 阶段后续要做什么写在这里；已完成结果不要反向堆积到这里，完成状态统一写入 `progress.md`。
>
> **文档版本**:v1.0
> **更新日期**:2026-05-24
> **文档目录**:`memory-bank/`
> **仓库根目录**:`private_helper_Agent/`
> **依据**:[`design-doucment.md`](./design-doucment.md) v0.2、[`tech-stack.md`](./tech-stack.md) v1.0
> **新代码根目录**:`private-helper-agent/`(仓库根下创建,与 `memory-bank/` 平级;**不得修改** `PaiSmart-main/`、`paper-agent/`)
> **约束**:本文档 **严禁包含任何源代码**;仅描述操作步骤与验证方式。
> **进度与架构**:步骤完成情况写入 `memory-bank/progress.md`;实现期架构细节写入 `memory-bank/architecture.md`。

---

## 0. 如何使用本文档

1. **严格按步骤顺序执行**;步骤末尾「依赖」列出必须先完成的步骤编号。
2. **每步必须先通过「验证」再进入下一步**;未通过则在本步内修复,不得跳过。
3. **验证**分为:自动化(`mvn test`、接口调用、数据库查询)与手动(浏览器、日志、文件系统);每步至少包含一项自动化验证(若该步无可测逻辑,则明确「本步仅人工检查清单」并列出勾选项)。
4. 对照遗留项目时 **只读参考**,禁止复制粘贴大段旧代码充当新实现;行为对齐以设计文档为准。
5. 所有密钥使用环境变量或 `.env`(不提交 Git);仓库须含 `.env.example`(无真实密钥)。
6. 完成阶段 A 全部步骤后,执行 **「阶段 A 门禁」**;完成阶段 B 后执行 **「阶段 B 门禁」**。

---

## 1. 全局约定(全程有效)

| 项 | 约定 |
|----|------|
| 产品名 | 研伴 Agent(Yanban Agent) |
| 数据库名 | `yanban_agent`(可配置,全文以此为例) |
| API 前缀 | `/api/v1` |
| 后端默认端口 | `8080`(若冲突在 `application-dev.yml` 调整并全文统一) |
| 前端开发端口 | `5173`(Vite 默认) |
| 表命名 | `sys_` 用户、`agent_` 会话、`kb_` 知识库、`paper_` 论文 |
| Git 提交 | 每完成 3~5 个小步骤提交一次,提交信息说明步骤编号(如 `step A-07: jwt login`) |

---

## 2. 前置准备(步骤 P-01~P-03)

### 步骤 P-01:创建新工程根目录与文档

**目标**:存在符合设计的多模块空骨架目录。

**指令**:

1. 在 `private_helper_Agent/` 下创建目录 `private-helper-agent/`。
2. 在其中创建子目录:`yanban-core`、`yanban-knowledge`、`yanban-paper`、`yanban-mcp`、`yanban-skills`、`yanban-api`、`yanban-cli`、`frontend`、`docs`、`skills/builtin/code-review`、`skills/user`(`user` 下放 `.gitkeep`)。
3. 从 `memory-bank/tech-stack.md`(§16 环境检查清单)摘录内容,写入 `private-helper-agent/docs/SETUP.md`,并补充 JDK、Maven、Docker、pnpm、Node 的最低版本说明。
4. 创建 `.gitignore`:排除 `target/`、`node_modules/`、`.env`、`storage/`、`logs/`、IDE 配置。
5. 创建 `.env.example`:列出 `DEEPSEEK_API_KEY`、`GLM_API_KEY`、`DASHSCOPE_API_KEY`、`GITHUB_TOKEN`、`JWT_SECRET` 等变量名,值为占位符。

**验证**:

- 目录树与 `memory-bank/tech-stack.md` §4.2 模块列表一致(人工核对清单 10 项)。
- 运行 `git status`,确认未改动 `PaiSmart-main/`、`paper-agent/` 下任何文件。
- `.env.example` 中无真实密钥(人工打开文件确认)。

**依赖**:无。

---

### 步骤 P-02:阶段 A 基础设施(MySQL + Redis)

**目标**:Docker 提供 MySQL 与 Redis,供认证与会话使用。

**指令**:

1. 在 `private-helper-agent/docs/` 创建 `docker-compose.yml`,至少包含 **MySQL 8** 与 **Redis 7** 服务;MySQL 暴露端口与后续 `private-helper-agent/yanban-api` 中 `application-dev.yml` 将使用的端口一致;字符集 `utf8mb4`。
2. 创建数据库 `yanban_agent` 的初始化说明(可在 `private-helper-agent/docs/SETUP.md` 中写清 root 密码与库名)。
3. 启动 Compose:在 `private-helper-agent/` 下执行 `docker compose -f docs/docker-compose.yml up -d`。
4. 记录连接信息到 `private-helper-agent/yanban-api` 的 `application-dev.yml` 占位(下一步由 A-01 写入正式配置)。

**验证**:

- `docker compose ps` 显示 MySQL、Redis 均为 running。
- 使用 MySQL 客户端执行 `SELECT 1`,返回成功。
- 使用 `redis-cli ping`(带密码若已配置)返回 `PONG`。

**依赖**:P-01。

---

### 步骤 P-03:阶段 B 基础设施清单(仅文档标记,可选提前启动)

**目标**:为阶段 B 预留 Compose 服务定义,避免 B 阶段临时改网络。

**指令**:

1. 在 `private-helper-agent/docs/docker-compose.yml` 中 **追加**(可不启动):Elasticsearch 8.10.4、Kafka(单节点)、MinIO;端口与仓库根下 `PaiSmart-main/docs/docker-compose.yaml` 对齐,对照表写入 `private-helper-agent/docs/SETUP.md`。
2. 在 `private-helper-agent/docs/SETUP.md` 增加章节「阶段 B 中间件」,说明启动命令与 healthcheck 等待时间(Kafka 建议等待 60s)。

**验证**:

- `docker compose config` 无语法错误。
- 文档中包含 ES、Kafka、MinIO 三者的 host、port、bucket 名占位说明。
- 阶段 A 仍可仅启动 MySQL+Redis 而不启动 ES/Kafka/MinIO(`docker compose up mysql redis -d` 成功)。

**依赖**:P-02。

---

# 阶段 A - 主链路闭环(约 3~4 天)

> **阶段 A 交付目标**:用户可注册登录;Web 对话页流式聊天;Harness 多轮 + DeepSeek;会话与消息落 MySQL;默认 RAG(KB 可先用简化索引);设置页配置 DeepSeek 与 max_steps;核心单元测试通过。

---

### 步骤 A-01:Maven 父工程与模块聚合

**目标**:父 POM 统一管理 Spring Boot 3.4.2、Java 17、模块依赖关系。

**指令**:

1. 在 `private-helper-agent/pom.xml` 创建父工程 `com.yanban:yanban-parent`。
2. 声明子模块:`yanban-core`、`yanban-knowledge`、`yanban-paper`、`yanban-mcp`、`yanban-skills`、`yanban-api`、`yanban-cli`(`frontend` 不由 Maven 聚合)。
3. 在 `dependencyManagement` 中锁定:`spring-boot-starter-parent` 3.4.2、Lombok、JUnit 5。
4. 各子模块创建最小 `pom.xml`,仅声明 `artifactId` 与父引用;`yanban-api` 依赖 `yanban-core`(其余模块依赖在后续步骤按需添加)。

**验证**:

- 在 `private-helper-agent/` 执行 `mvn -q validate`,退出码 0。
- 执行 `mvn -q dependency:tree -pl yanban-api`,能解析 `yanban-core` 且无循环依赖报错。

**依赖**:P-01。

---

### 步骤 A-02:启动模块与健康检查

**目标**:`yanban-api` 可启动并暴露健康端点。

**指令**:

1. 在 `yanban-api` 添加 Spring Boot 启动类,应用名 `yanban-api`。
2. 添加 `application.yml` 与 `application-dev.yml`:数据源指向 P-02 的 MySQL;Redis 连接;服务端口。
3. 引入 `spring-boot-starter-actuator`,暴露 `health`(仅 `health` 即可)。
4. 配置 `spring.jpa.hibernate.ddl-auto=validate` 或关闭 ddl-auto 直至 Flyway 就绪(若尚无迁移脚本,本步可暂用 `none` 并仅测无 JPA 启动)。

**验证**:

- `mvn -pl yanban-api spring-boot:run -Dspring-boot.run.profiles=dev` 启动成功,日志无 ERROR。
- `curl http://localhost:8080/actuator/health` 返回 JSON 且 status 为 UP。
- 停止应用后端口释放。

**依赖**:A-01、P-02。

---

### 步骤 A-03:数据库迁移框架与系统用户表

**目标**:版本化 DDL;存在 `sys_users` 表。

**指令**:

1. 在 `yanban-api` 引入 Flyway(或 Liquibase,与 `memory-bank/tech-stack.md` 一致任选其一并在 `private-helper-agent/docs/SETUP.md` 注明)。
2. 创建首个迁移脚本:表 `sys_users`(字段至少:id、username 唯一、password_hash、created_at、updated_at)。
3. 将 JPA `ddl-auto` 设为 `validate`(或关闭自动 DDL)。
4. 重启应用,确认迁移自动执行。

**验证**:

- 启动日志含 Flyway migrate 成功信息。
- 查询 MySQL:`SHOW TABLES LIKE 'sys_users'` 存在。
- `flyway_schema_history` 表存在且有一条版本记录。
- 再次启动应用,Flyway 显示 already up to date,无重复迁移错误。

**依赖**:A-02。

---

### 步骤 A-04:用户注册与密码存储

**目标**:提供注册 API;密码不以明文存储。

**指令**:

1. 在 `yanban-api` 实现 `POST /api/v1/auth/register`:接收用户名与密码;使用 BCrypt(或 Spring Security 推荐方式)哈希后写入 `sys_users`。
2. 校验用户名唯一;非法输入返回 4xx 与明确错误信息。
3. 禁止在日志中打印密码或哈希。

**验证**:

- 使用 HTTP 客户端向 register 发送新用户名,返回 201 或 200(与实现一致,文档化)。
- MySQL 查询该用户:`password_hash` 非空且不等于明文密码。
- 重复同用户名注册返回 409 或 400(行为写入 `docs/API-smoke.md` 一条记录)。
- 编写 `@WebMvcTest` 或集成测试:注册成功 1 例、重复用户名 1 例;`mvn -pl yanban-api test` 通过。

**依赖**:A-03。

---

### 步骤 A-05:JWT 登录、刷新与 Security 过滤链

**目标**:无状态认证;受保护接口需 Bearer Token。

**指令**:

1. 实现 `POST /api/v1/auth/login`:校验用户名密码,返回 access token 与 refresh token(或设计文档约定的一种)。
2. 实现 `POST /api/v1/auth/refresh`(若采用 refresh)。
3. 配置 Spring Security:放行 `/api/v1/auth/**`、`/actuator/health`;其余 `/api/v1/**` 需认证。
4. Token 签名密钥从环境变量 `JWT_SECRET` 读取;Redis 可选存储 refresh token 黑名单(本步可仅实现 access)。
5. 提供获取当前用户的接口(如 `GET /api/v1/users/me`)。

**验证**:

- 未带 Token 访问 `/api/v1/users/me` 返回 401。
- login 后带 `Authorization: Bearer <token>` 访问 `/api/v1/users/me` 返回 200 且用户名正确。
- 过期或伪造 Token 返回 401。
- `mvn -pl yanban-api test` 中 Security 相关测试至少 3 例通过。

**依赖**:A-04。

---

### 步骤 A-06:`yanban-core` 领域模型 - 会话与消息

**目标**:定义 Harness 持久化实体与 Repository(不含 Harness 逻辑)。

**指令**:

1. 在 `yanban-core` 添加迁移脚本(或由 `yanban-api` 统一管理迁移并在文档说明):表 `agent_sessions`、`agent_messages`(字段含:session_id、user_id、role、content、tool_calls_json、paper_task_id 可空、created_at)。
2. 可选表 `agent_tool_runs`(tool 名、input/output、status)。
3. 创建 JPA Entity 与 Repository;`agent_sessions` 含 model_provider_snapshot、max_steps、rag_disabled 等会话级标志字段。
4. `yanban-api` 依赖并扫描 `yanban-core` 实体。

**验证**:

- 应用启动且 Flyway 迁移成功,三张 agent 表存在。
- 编写 Repository 层测试(`@DataJpaTest`):插入一条 session 与两条 message,按 session_id 查询条数为 2;`mvn -pl yanban-core test` 通过。

**依赖**:A-03、A-01。

---

### 步骤 A-07:DeepSeek ModelProvider(非流式)

**目标**:能以 OpenAI 兼容格式调用 DeepSeek 完成单次 chat。

**指令**:

1. 在 `yanban-core` 定义接口 `ChatModelProvider` 与请求/响应 DTO(messages、model、temperature 等)。
2. 实现 `DeepSeekModelProvider`:从配置读取 `api-url`、`api-key`、`model`;使用 WebFlux WebClient 或 RestClient 发送 HTTP 请求。
3. 在 `yanban-api` 的 `application-dev.yml` 增加 deepseek 配置块;密钥仅来自环境变量。
4. **暂不实现** GLM、MCP。

**验证**:

- 编写单元测试:使用 MockWebServer 或 Mockito 模拟 HTTP 响应,断言 Provider 解析出 assistant 文本;不发起真实外网(CI 友好)。
- 本地可选人工冒烟:配置真实 `DEEPSEEK_API_KEY`,通过临时 CommandLineRunner 或测试 profile 发一条「你好」,日志中出现合理回复(该用例标为 `@Tag("manual")` 可不进 CI)。
- `mvn -pl yanban-core test` 通过。

**依赖**:A-01。

---

### 步骤 A-08:DeepSeek 流式输出

**目标**:Provider 支持 stream,逐 chunk 回调。

**指令**:

1. 扩展 `DeepSeekModelProvider`:支持 `stream: true`,解析 SSE 或 chunked 响应(与 DeepSeek 文档一致)。
2. 定义 `ChatStreamListener` 或 `Flux` 消费接口,供 Harness/WebSocket 使用。
3. 超时与 IO 异常映射为领域异常,不吞掉根因。

**验证**:

- Mock 流式响应单元测试:至少收到 2 个 chunk,最终完成事件 1 次。
- 人工冒烟(manual tag):流式打印至少 10 个 token;日志无解析异常。

**依赖**:A-07。

---

### 步骤 A-09:Tool 抽象与注册表

**目标**:统一 Tool 定义与执行入口,为 Function Calling 做准备。

**指令**:

1. 在 `yanban-core` 定义 `ToolDefinition`(name、description、parameters JSON Schema)、`ToolCall`、`ToolResult`、`ToolExecutor` 接口。
2. 实现 `ToolRegistry`:注册、按名查找、列出 OpenAI tools 格式列表。
3. 实现一个内置测试工具 `echo`(入参 message,原样返回),用于后续 Harness 测试。

**验证**:

- 单元测试:注册 `echo` 后,执行 `ToolCall(name=echo)` 返回相同 message。
- `listToolsForModel()` 输出非空 JSON,且包含 `echo` 的 name 与 description。

**依赖**:A-01。

---

### 步骤 A-10:Harness 最小循环(无 RAG、无 MCP)

**目标**:多轮直到模型不再返回 tool_calls 或达到 max_steps。

**指令**:

1. 在 `yanban-core` 实现 `HarnessEngine`:输入为 session 历史 + 用户新消息;每轮调用 `ChatModelProvider`(先非流式即可)。
2. 若响应含 tool_calls:经 `ToolRegistry` 执行,将 tool 结果以 role=tool 消息追加;继续下一轮。
3. 若无 tool_calls:结束,返回最终 assistant 内容。
4. 尊重 `max_steps`(默认 20);超限返回明确错误并写日志。
5. 每步写入 `agent_tool_runs`(若表已建)或日志行(stepIndex、toolName、durationMs、success)。

**验证**:

- 单元测试(Mock Provider):
  - 场景1:模型第一次返回 tool_call `echo`,第二次返回纯文本 → 共 2 轮,最终文本非空。
  - 场景2:模型连续返回 tool_call 直到超过 max_steps=2 → Harness 终止并标记失败。
- 无真实 API 的测试在 CI 必须全绿。

**依赖**:A-07、A-09。

---

### 步骤 A-11:Harness 与会话持久化集成

**目标**:用户消息触发 Harness,结果写入 `agent_messages`。

**指令**:

1. 在 `yanban-api` 实现 `POST /api/v1/agent/sessions`(创建会话,保存 provider 快照与 max_steps)。
2. 实现 `GET /api/v1/agent/sessions`(当前用户分页列表)。
3. 实现 `POST /api/v1/agent/sessions/{id}/messages`:持久化 user 消息;调用 `HarnessEngine`;持久化 assistant 与 tool 消息。
4. 校验 session 归属当前登录用户。

**验证**:

- 集成测试(`@SpringBootTest` + Mock Provider 或 test profile):创建用户 → 创建 session → 发送消息 → 查询 messages 至少 2 条(user+assistant)。
- 用户 A 的 token 访问用户 B 的 sessionId 返回 403 或 404。
- `mvn -pl yanban-api test` 通过。

**依赖**:A-05、A-06、A-10。

---

### 步骤 A-12:知识库简化实现(阶段 A)- 内存或 MySQL 全文占位

**目标**:在 ES 未就绪前,支持 `search_knowledge` 返回可预测结果。

**指令**:

1. 在 `yanban-knowledge` 创建模块;`yanban-api` 依赖它。
2. 创建表 `kb_documents`(id、user_id、filename、status、is_public、created_at)与 `kb_chunks`(document_id、chunk_text、可选 embedding 占位)。
3. 实现 **简化入库**:`POST /api/v1/kb/documents/simple-upload`(阶段 A 临时 API,可在文档标记 deprecated)- 单文件上传,同步 Tika 提取文本,按固定长度分块写入 `kb_chunks`,status=READY。
4. 实现 `KnowledgeSearchService.search(query, userId, topK)`:先用 SQL `LIKE` 或简单关键词匹配返回 topK 块(明确在 README 标注为临时实现)。
5. 在 `yanban-core` 注册 tool `search_knowledge`,内部调用上述 service。

**验证**:

- 集成测试:上传一个小型 `.txt` 或 `.md` 文件 → `search_knowledge` 查询文件内唯一关键词 → ToolResult 包含该关键词。
- 私有文档:用户 A 上传;用户 B 搜索不应命中(返回空或仅公开文档)。
- `mvn -pl yanban-api test` 相关用例通过。

**依赖**:A-09、A-03。

---

### 步骤 A-13:默认 RAG 与「禁用知识库」开关

**目标**:对话默认检索 KB;会话或单次请求可关闭 RAG。

**指令**:

1. 在 `agent_sessions` 或单次消息请求体支持字段 `ragDisabled`(与前端「本次不使用知识库」对应)。
2. Harness 在每轮用户消息前:若未禁用,则调用 `KnowledgeSearchService`(或 tool,二选一但行为一致),将检索结果注入 system 或 tool 上下文(策略写入 `docs/ARCHITECTURE.md` 一段说明)。
3. 禁用时跳过检索,且本轮不自动调用 `search_knowledge`,除非模型主动通过 FC 调用。

**验证**:

- 单元测试 Harness:同一问题,ragDisabled=false 时 Mock SearchService 被调用 1 次;true 时 0 次。
- 集成测试:上传含「阿尔法」的文档;提问「阿尔法」;禁用 RAG 时回答不应包含检索片段(或 tool 未自动触发);启用时应包含。

**依赖**:A-10、A-12。

---

### 步骤 A-14:WebSocket 流式对话

**目标**:前端通过 WebSocket 接收 token 流与完成事件。

**指令**:

1. 在 `yanban-api` 配置 WebSocket 端点(路径建议 `/ws/chat` 或 `/api/v1/ws/chat`,全文统一)。
2. 连接时校验 JWT(query token 或 STOMP 头,方式写入文档)。
3. 客户端发送 JSON:sessionId、content、ragDisabled、skillId(阶段 A skillId 可为空)。
4. 服务端边调用 `DeepSeekModelProvider` 流式边推送 chunk;Harness 若多轮,在非最终轮可推送 `tool_start`/`tool_end` 事件(JSON 类型字段定义写入 `docs/WEBSOCKET.md`)。
5. 完成后持久化消息并推送 `done`。

**验证**:

- 使用 `docs/WEBSOCKET.md` 中的示例消息格式,用 `websocat` 或前端 dev 工具连接:发送「数到3」应收到多个 chunk 且最终 `done`。
- 断开重连后,同一 session 历史通过 REST `GET messages` 仍可查到刚才会话内容。
- 无 Token 连接被拒绝(关闭码或错误帧)。

**依赖**:A-08、A-11、A-13。

---

### 步骤 A-15:用户设置 API(DeepSeek + max_steps)

**目标**:CLI 与 Web 共用配置落库。

**指令**:

1. 创建表 `sys_user_settings`(user_id、default_provider、deepseek 相关字段密文或脱敏存储、max_steps、rag_default_enabled 等)。
2. 实现 `GET/PUT /api/v1/settings`:仅操作当前用户;API Key 写入时加密或仅存「是否已配置」标志+密文(禁止 GET 返回明文 key)。
3. Harness 创建 session 时读取用户 max_steps 与 provider 快照。

**验证**:

- PUT max_steps=5 后,新 session 的 Harness 在 Mock 连续 tool_call 场景下第 6 轮前终止。
- GET settings 不返回明文 apiKey。
- 集成测试 2 例通过。

**依赖**:A-05、A-11。

---

### 步骤 A-16:Vue 工程初始化与认证页

**目标**:前端可注册、登录、携带 Token 访问 API。

**指令**:

1. 在 `frontend/` 使用 Vite + Vue 3 + TypeScript + Pinia + Vue Router + Naive UI 初始化(pnpm)。
2. 配置 dev proxy 将 `/api` 转发到后端 8080。
3. 实现 `/login`、`/register` 页面;Pinia `auth` store 保存 token;Axios 拦截器附加 Bearer。
4. 路由守卫:未登录跳转 login。

**验证**:

- `pnpm install && pnpm dev` 无编译错误。
- 浏览器手动:注册新用户 → 登录 → 进入受保护路由不被踢回 login。
- 退出或清除 token 后访问 `/chat` 重定向 login。

**依赖**:A-04、A-05。

---

### 步骤 A-17:Vue 对话页(流式 + RAG 开关)

**目标**:`/chat` 完成会话列表、发消息、WebSocket 流式展示。

**指令**:

1. 实现 `/chat`:左侧会话列表(调用 sessions API);右侧消息区。
2. 勾选框「本次不使用知识库」绑定 `ragDisabled`。
3. 建立 WebSocket;显示流式 token;完成后刷新历史消息。
4. 新建会话按钮;切换会话加载历史。

**验证**:

- 手动:登录后发送消息,界面逐字/逐段显示回复;勾选禁用 KB 后行为与 A-13 验证一致(可问需知识库的问题对比)。
- 刷新页面后历史消息仍在。
- 浏览器 Network 面板可见 WS 帧与 200 REST。

**依赖**:A-14、A-16。

---

### 步骤 A-18:Vue 设置页(阶段 A 范围)

**目标**:`/settings` 可配置 DeepSeek 与 max_steps。

**指令**:

1. 实现 `/settings`:表单字段 provider(仅 DeepSeek)、apiKey(密码框)、model、temperature、max_steps、默认启用 RAG。
2. 保存调用 `PUT /api/v1/settings`;加载调用 GET。
3. 页面文案说明密钥仅存本地服务端、不会回显。

**验证**:

- 修改 max_steps 保存后,新开会话验证与 A-15 一致(可让后端返回 session 配置供 UI 显示只读确认)。
- apiKey 保存后 GET 不显示明文(UI 显示「已配置」)。

**依赖**:A-15、A-16。

---

### 步骤 A-19:阶段 A 中文 README 与 API 冒烟文档

**目标**:他人可按文档启动阶段 A。

**指令**:

1. 在 `private-helper-agent/README.md` 写中文:项目简介、Docker 启动、后端启动、前端启动、环境变量。
2. 创建 `docs/API-smoke.md`:列出 auth、chat、settings 的 curl 示例(无密钥)。
3. 创建 `docs/WEBSOCKET.md`:消息格式说明。

**验证**:

- 按 README 从零执行一遍(另一台机器或清空环境模拟):30 分钟内能登录并在 `/chat` 收到 DeepSeek 回复(允许使用 manual 密钥)。
- README 中端口与 `application-dev.yml` 一致。

**依赖**:A-17、A-18。

---

### 阶段 A 门禁(全部 A 步骤完成后执行)

**必须全部通过**:

| # | 门禁项 | 验证方式 |
|---|--------|----------|
| G-A1 | 构建 | `mvn clean test` 在父工程退出码 0(manual 测试可排除) |
| G-A2 | 认证 | 注册→登录→受保护 API 成功 |
| G-A3 | 对话 | Web `/chat` 流式收到合理回复 |
| G-A4 | RAG | 上传文档后提问命中;禁用 RAG 行为符合预期 |
| G-A5 | 持久化 | 重启后端后历史 session 仍在 |
| G-A6 | 安全 | 仓库无 `.env` 提交;`.env.example` 无真实密钥 |
| G-A7 | 遗留隔离 | `PaiSmart-main`、`paper-agent` git diff 为空 |

---

# 阶段 B - 功能齐套(约 3~4 天)

> **阶段 B 交付目标**:Docker 全栈;KB 分片+Kafka+ES+DashScope;论文全流程+SSE;GLM;MCP GitHub+filesystem;code-review Skill;Vue 全页面;CLI;补充集成测试。

---

### 步骤 B-01:启动 ES、Kafka、MinIO

**目标**:中间件就绪且应用可连接。

**指令**:

1. 启动 `docs/docker-compose.yml` 中 ES、Kafka、MinIO(若 P-03 已定义)。
2. 在 `application-dev.yml` 填写连接信息;MinIO 创建 bucket(名写入 `docs/SETUP.md`)。
3. Kafka 创建 topic `file-processing`(与 PaiSmart 一致或文档化新名称)。
4. ES 创建索引模板或初始化服务(dense_vector 维度与 DashScope 一致,维度值查 DashScope 文档写入配置)。

**验证**:

- `curl ES/_cluster/health` 为 yellow 或 green。
- `kafka-topics.sh --list` 含 `file-processing`。
- MinIO 控制台可登录并见到 bucket。
- Spring Boot 启动日志无 ES/Kafka/MinIO 连接失败(可 health indicator)。

**依赖**:P-03、阶段 A 门禁。

---

### 步骤 B-02:Flyway 扩展 - KB 与论文表

**目标**:KB、论文相关表就绪。

**指令**:

1. 新增迁移:`kb_documents` 扩展字段(public/private、status 枚举 UPLOADING/PROCESSING/READY/FAILED)、`kb_chunks`(含 es_doc_id 可选)、`chunk_upload` 分片表(对齐 PaiSmart 思路)。
2. 新增迁移:`paper_tasks`、`paper_task_rounds`(字段参考 paper-agent TaskEntity/TaskRoundEntity 语义)。
3. 更新 JPA 实体与 Repository。

**验证**:

- Flyway migrate 成功;表存在。
- `@DataJpaTest` 各插入一条 KB 文档与一条 paper_task 成功。

**依赖**:B-01、A-03。

---

### 步骤 B-03:MinIO 分片上传与合并

**目标**:大文件分片上传、合并入 MinIO。

**指令**:

1. 在 `yanban-knowledge` 实现 `POST /api/v1/upload/chunk`、`POST /api/v1/upload/merge`(路径可与设计文档统一)。
2. MD5 校验、分片记录写 MySQL;合并后对象写入 MinIO。
3. 合并完成后发送 Kafka 消息(payload 含 fileId、userId、objectKey)。

**验证**:

- 手动:上传大于 2 个分片的文件,merge 成功;MinIO 存在对象;Kafka topic 收到 1 条消息(可用控制台消费者验证)。
- 集成测试(Testcontainers Kafka 可选):merge 后 mock 消费者被调用或消息写入测试 topic。

**依赖**:B-01、B-02。

---

### 步骤 B-04:Kafka 消费者 - 解析与分块

**目标**:异步处理文件;状态 PROCESSING → READY/FAILED。

**指令**:

1. 实现 `FileProcessingConsumer`:消费 `file-processing`;调用 Tika 解析 PDF/Word/Markdown。
2. 文本分块写入 `kb_chunks`;更新 `kb_documents.status`。
3. 失败时记录错误信息字段并置 FAILED。

**验证**:

- 上传 sample.pdf(fixtures 放 `yanban-knowledge/src/test/resources`)→ 最终 status=READY,`kb_chunks` 行数大于 0。
- 损坏文件上传 → status=FAILED 且 error 非空。
- 消费者单元测试:Mock Tika 仍验证状态流转。

**依赖**:B-03。

---

### 步骤 B-05:DashScope Embedding 与 ES 写入

**目标**:向量化并写入 ES;支持混合检索。

**指令**:

1. 实现 `EmbeddingClient` 调用 DashScope;密钥来自环境变量。
2. 实现 `VectorizationService`:读 `kb_chunks`,写 ES 文档(含 vector、text、userId、isPublic、documentId)。
3. 在消费者链路中向量化步骤位于解析之后(可同 consumer 或第二 topic,须在文档说明)。

**验证**:

- 处理完成后 `curl ES index/_search` 能按 documentId 查到文档。
- 单元测试 Mock EmbeddingClient:固定向量写入逻辑被调用。
- 维度与索引 mapping 一致,否则 ES 拒绝写入(人工确认无 400 错误)。

**依赖**:B-04。

---

### 步骤 B-06:混合检索与权限

**目标**:`HybridSearchService` 仅返回当前用户有权文档。

**指令**:

1. 实现混合检索(BM25 + 向量,参考 PaiSmart `HybridSearchService` 行为)。
2. 过滤规则:自己的私有 + 所有人公开;不包含他人私有。
3. 替换阶段 A 简化 `search_knowledge` 实现为正式检索;删除或关闭 `simple-upload` 临时 API(迁移脚本或文档说明测试数据迁移)。

**验证**:

- 集成测试:用户 A 私有文档;用户 B 搜索相同关键词无结果;设公开后 B 可搜到。
- 检索调试 API `POST /api/v1/search` 返回 scored 列表且 score 降序。

**依赖**:B-05、A-12。

---

### 步骤 B-07:Vue 知识库管理页

**目标**:`/knowledge-base` 上传、列表、删除、处理状态。

**后端前置补齐说明**:

- 本步开始前,后端除 `upload/chunk`、`upload/merge`、`search` 外,还应补齐知识库页面所需支撑接口,至少包括:
  - `GET /api/v1/kb/documents`(当前用户可见或当前用户拥有的文档列表,需返回 status)
  - `DELETE /api/v1/kb/documents/{documentId}`(删除文档,并按策略清理 MySQL / MinIO / ES)
- 若实现中采用轮询展示状态,则列表接口需稳定返回 `UPLOADING / PROCESSING / READY / FAILED`。
- 这些接口虽服务于前端页,但实现时机应早于或并行于 B-07,而不是在前端完成后再补。

**指令**:

1. 先补齐上述列表 / 删除后端接口,并完成最小接口验证。
2. 实现分片上传 UI(进度条、失败重试);调用 chunk + merge API。
3. 列表展示 status(处理中轮询或 WebSocket 推送,择一并在文档固定)。
4. 删除文档:调用 DELETE API,并确认 ES 与 MinIO 对象按策略删除(策略写入 ARCHITECTURE)。

**验证**:

- 手动:上传 PDF → 状态从 PROCESSING 变 READY → 列表可见。
- 删除后列表无该条;ES 搜索该 documentId 无结果。

**依赖**:B-03、B-06、A-16。

---

### 步骤 B-08:Vue 检索调试页

**目标**:`/knowledge-base/search-debug` 可手动 query。

**指令**:

1. 页面输入 query、topK;调用 `POST /api/v1/search`。
2. 展示 chunk 文本、score、document 文件名。

**验证**:

- 手动:对已知关键词搜索,命中内容与 KB 文档一致。
- topK=1 时只显示 1 条(或不足时显示实际条数)。

**依赖**:B-06、B-07。

---

### 步骤 B-09:`yanban-paper` 模块 - 领域与存储

**目标**:论文任务可创建、MinIO 存 docx。

**指令**:

1. 创建 `yanban-paper` 模块;迁移表已存在;实现 `PaperStorageService`(MinIO + 本地 fallback)。
2. 实现 `POST /api/v1/paper/process`:接收 docx、参数(scoreThreshold、maxRounds、innerMaxAttempts、literatureCount、targetLanguage zh/en)。
3. 创建 `paper_tasks` 记录,状态 PENDING。

**验证**:

- 上传 sample.docx 后 DB 有 task 行;MinIO(或 fallback 目录)存在 original 对象。
- 非法扩展名拒绝 400。

**依赖**:B-01、B-02。

---

### 步骤 B-10:PaperOrchestrator 全流程(新写)

**目标**:对齐设计文档 PA-01~PA-03;SSE 事件类型对齐附录 B。

**指令**:

1. 在 `yanban-paper` **新写** Orchestrator(参考 `paper-agent` 只读):Summary → 内外层润色/审查 → PaperReview → Abstract → OpenAlex 文献推荐。
2. Prompt 放 `yanban-paper/src/main/resources/prompts/`(可从 paper-agent 改写拷贝文本,但包名与类必须新写)。
3. 异步线程池执行;`GET /api/v1/paper/events?taskId=` 推送 SSE(事件 type 与 `memory-bank/design-doucment.md` 附录 B 一致)。
4. 支持 pause/resume/stop(与 paper-agent 语义一致)。
5. 完成后写入 final.docx 路径并更新 COMPLETED。

**当前实现备注(2026-06-12)**:

- 当前已完成"可演示骨架版":
  - 异步任务执行
  - SSE 推送与历史回放
  - pause / resume / stop
  - task 查询与结果下载链路
  - 事件类型已基本对齐附录 B
- 当前**尚未完成**的部分:
  - 真实 Summary / 分章润色 / 跨章审查 / Abstract / OpenAlex 逻辑
  - 基于真实内容生成 final docx
  - Prompt 资源体系化落盘
- 因此,当前 B-10 可作为 B-11 前端联调基础;真实论文内容处理留待后续优化时继续推进。

**验证**:

- 使用小 docx fixture(2~3 段)跑通:SSE 依次出现 `summary_ready`、`sections`、`complete`(允许跳过 literature 若 Introduction 不存在,行为写入说明)。
- DB `paper_task_rounds` 至少 1 行。
- 下载 API 返回 docx 且 HTTP Content-Type 正确。
- **不要求** 润色质量达标(第一期)。

**依赖**:B-09、A-07(LLM 调用)。

---

### 步骤 B-11:Vue 论文三步页

**目标**:`/paper` 上传 → 进度 → 结果。

**指令**:

1. 步骤1:表单含 targetLanguage 中英切换、阈值、轮次等;上传 docx。
2. 步骤2:订阅 SSE,展示日志与章节进度(类型映射附录 B)。
3. 步骤3:下载最终文件;可选预览链接若后端提供。
4. 支持从路由 query 打开已有 taskId(供对话跳转)。

**验证**:

- 手动端到端:上传 → 看到进度 → 下载文件可打开。
- 浏览器断开后重连 SSE 或刷新任务详情仍可显示最终状态。

**依赖**:B-10、A-16。

---

### 步骤 B-12:对话跳转论文页

**目标**:Harness 识别润色意图并返回论文页深链。

**指令**:

1. 注册 tool 或意图处理:`suggest_paper_revision`(返回前端 URL `/paper?taskId=` 或仅 `/paper` 提示上传)。
2. 若对话创建占位 task:写入 `paper_task_id` 到 `agent_messages`;助手消息含可点击链接文案。
3. 系统 prompt 说明:实际 docx 上传在论文页完成。

**验证**:

- 在 `/chat` 发送「帮我润色论文」类消息,回复含论文页链接;点击跳转 `/paper`。
- 消息记录含 `paper_task_id` 或等价字段(数据库可查)。

**依赖**:B-10、A-17。

---

### 步骤 B-13:GLM ModelProvider

**目标**:设置页可切换 GLM;Harness 使用所选 Provider。

**指令**:

1. 实现 `GlmModelProvider`(OpenAI 兼容端点,按智谱文档)。
2. `sys_user_settings` 与 settings API 增加 glm 字段。
3. 创建 session 时快照 provider;流式与非流式与 DeepSeek 接口一致。

**验证**:

- Mock 单元测试 GLM provider 解析响应。
- 手动(manual):settings 选 glm 且配置有效 key,新 session 对话收到回复。
- DeepSeek 与 GLM 切换后互不影响旧 session 快照(旧 session 仍用原 provider)。

**依赖**:A-07、A-15。

---

### 步骤 B-14:Vue 设置页扩展(GLM + MCP + Skills 列表)

**目标**:设置页覆盖阶段 B 配置项。

**指令**:

1. 增加 GLM 密钥与模型字段;provider 默认选项 deepseek/glm。
2. MCP 配置:GitHub PAT、filesystem 允许根目录(多行文本或列表)。
3. Skills 列表:扫描 builtin + user;启用/禁用(写 DB 或 settings JSON,不删文件夹);**无在线编辑器**;说明「请往 `skills/user/` 添加目录」。
4. max_steps 与 RAG 默认保持。

**验证**:

- 禁用某 user skill 后,`GET /api/v1/skills` 不返回该 skill;对话页下拉不可选。
- MCP 配置保存后重启应用仍生效(来自 DB 或配置文件,与架构一致)。

**依赖**:B-13、B-20(Skills 列表 API 可先 stub 后本步对接)。

---

### 步骤 B-15:`yanban-mcp` - stdio Client 基础

**目标**:可启动子进程、list_tools、call_tool。

**指令**:

1. 在 `yanban-mcp` 实现 JSON-RPC stdio 客户端:启动、握手、初始化、tools/list、tools/call、关闭。
2. 进程命令与白名单从配置读取;禁止任意命令注入。
3. 超时与进程崩溃恢复策略(记录日志并返回 ToolResult 错误)。

**验证**:

- 单元测试:对假想的 JSON-RPC 输入输出进行解析(无需真实 Node)。
- 手动:配置 echo 或官方最小 MCP server(若环境有 Node),list_tools 非空。

**依赖**:A-01。

---

### 步骤 B-16:连接 GitHub MCP Server

**目标**:Harness 可调用 GitHub 相关 tools。

**指令**:

1. 在配置中增加 GitHub MCP 启动命令(npx 包名写入 `docs/SETUP.md`)。
2. 将 GitHub tools 注册到 `ToolRegistry`,名称加前缀 `mcp_github__`。
3. 文档说明 `GITHUB_TOKEN` 权限范围(repo read 等)。

**验证**:

- 手动:在对话中要求「搜索 GitHub 上 topic 为 rag 的仓库」,模型触发 tool 后返回 JSON 含 repo 信息(或明确错误若 rate limit)。
- 无 Token 时启动失败有友好错误,不导致整个应用崩溃。

**依赖**:B-15、A-10。

---

### 步骤 B-17:连接 filesystem MCP + 路径白名单

**目标**:仅允许读取配置目录下的文件。

**指令**:

1. 配置 filesystem MCP 的 root 路径列表(默认项目下 `workspace/`)。
2. 注册 tools,前缀 `mcp_fs__`。
3. 调用前校验路径规范化后落在白名单内。

**验证**:

- 手动:请求读取白名单内文本文件成功。
- 请求读取 `/etc/passwd` 或 `../` 逃逸路径失败且日志记录拒绝原因。
- 单元测试路径校验函数至少 4 例。

**依赖**:B-15。

---

### 步骤 B-18:`yanban-skills` - 加载与注入

**目标**:扫描 SKILL.md;合并 system prompt;tools 白名单。

**指令**:

1. 实现 `SkillLoader`:加载 `skills/builtin/**`、`skills/user/**`;解析可选 `skill.yaml`。
2. `SkillRegistry` 提供列表、按 id 获取、启用/禁用状态(持久化)。
3. Harness 在会话开始时若指定 skillId:注入 SKILL 内容;过滤 ToolRegistry 仅 allowed_tools(及 MCP 前缀匹配规则写入文档)。

**验证**:

- 单元测试:加载 `code-review` skill 后 system prompt 含 SKILL 中唯一关键词句。
- 白名单外 tool 不可被 Harness 执行(Mock 模型请求disallowed tool 应失败)。
- 用户目录新增 skill 后调用 refresh API 后出现。

**依赖**:A-09、P-01。

---

### 步骤 B-19:内置 Skill `code-review` 与对话集成

**目标**:对话页可选 code-review;可读本地文件。

**指令**:

1. 完善 `skills/builtin/code-review/SKILL.md` 与 `skill.yaml`(allowed_tools 仅 filesystem 读相关)。
2. `GET /api/v1/skills` 返回 builtin。
3. `/chat` 增加 Skill 下拉;选中后带 skillId 发 WS 消息。

**验证**:

- 手动:选中 code-review,提供白名单内文件路径,要求审查;回复为结构化问题列表(与 SKILL 要求一致)。
- 未选 skill 时行为与阶段 A 一致。
- 选用 code-review 但路径非法时 tool 错误被模型消化并回复用户可读提示。

**依赖**:B-17、B-18、A-17。

---

### 步骤 B-20:Skills 列表 API 与启用/禁用

**目标**:与 B-14 设置页联动。

**指令**:

1. `GET /api/v1/skills` 返回 id、name、description、builtin、enabled。
2. `PUT /api/v1/skills/{id}/enabled` 仅 user skill 或全部可禁用 builtin(产品决策:builtin 可禁用,写入 ARCHITECTURE)。
3. 禁用后 Harness 拒绝该 skillId 并提示。

**验证**:

- 集成测试:禁用后 WS 请求带该 skillId 返回 400。
- 启用后恢复成功。

**依赖**:B-18。

---

### 步骤 B-21:OCR 可插拔接口(最小实现)

**目标**:图片入库时走 OCR。

**指令**:

1. 定义 `OcrProvider` 接口;配置项选择实现类与 endpoint。
2. 实现一个 `HttpOcrProvider`(调用外部 HTTP 网关);未配置时图片标记 FAILED 并提示。
3. 在解析流水线中:mime 为 image 时走 OCR,文本进入分块。

**验证**:

- Mock 测试:OCR 返回固定文本后 `kb_chunks` 含该文本。
- 未配置 OCR 时上传 png:status=FAILED 且 error 指明 OCR 未配置。

**依赖**:B-04。

---

### 步骤 B-22：`yanban-cli` — login、chat、config

**目标**：CLI 与 Web 共用后端 API 与配置。

**指令**：

1. 使用 Picocli；子命令 `login`（交互输入用户名密码，保存 token 到用户目录配置文件）、`chat`（REPL 流式打印）、`config list/set`。
2. 配置文件路径与 `docs/SETUP.md` 一致；调用相同 REST/WS。
3. 除 Java 主类外，还应补齐**终端命令化入口**（至少 Windows 下可运行的 `yanban.bat` 或等价脚本 / 启动方式），避免只能通过 IDEA Program arguments 或手工 `java ... MainClass ...` 调试。

**验证**：

- 手动：`yanban login` 后 `yanban chat` 发送消息有输出。  
- `yanban config set max-steps 10` 后 Web 新 session 生效（共用 DB settings）。  
- 终端侧应可直接通过命令入口运行，而不只是把 `YanbanCli` 当作 IDE 中的 Java 主类。  

**依赖**：A-05、A-14、A-15。

---

### 步骤 B-23：`yanban-cli` — kb 与 paper 子命令

**目标**：CLI 覆盖知识库与论文状态查询。

**指令**：

1. `yanban kb list`、`yanban kb upload <file>`（可走简单单文件 API 或分片，与 Web 一致）。  
2. `yanban paper status <taskId>` 输出任务状态与最近日志一行。  
3. 若当前仅完成了 Picocli 主类逻辑而没有终端命令入口，则本步骤**不视为完全完成**；需要补齐脚本化运行方式后，才能按真正 CLI 门禁验证。  

**验证**：

- 手动：CLI 上传与 Web 列表可见同一文档。  
- `paper status` 在任务 RUNNING/COMPLETED 时字段正确。
- 终端中可直接通过 CLI 命令入口执行，而不是仅在 IDEA 中调试 Java 主类。

**依赖**：B-07、B-10、B-22。

---

### 步骤 B-24:集成测试与 CI 友好

**目标**:核心路径有自动化回归。

**指令**:

1. 父 POM 配置 `mvn test` 聚合所有模块。
2. 必须包含:Harness 多轮、RAG 开关、Skill 白名单、JWT、KB 权限检索(Mock ES 或 Testcontainers 二选一,在 README 说明)。
3. 所有依赖真实外网(DeepSeek、DashScope、GitHub)的测试标 `@Tag("manual")` 排除默认 CI。
4. 可选:GitHub Actions 仅跑 H2/ Mock 测试。

**验证**:

- `mvn clean test` 默认 profile 全绿。
- `mvn test -Dgroups=manual` 仅在有密钥环境执行通过。

**依赖**:阶段 B 各核心步骤。

---

### 步骤 B-25:文档定稿与开源准备

**目标**:满足开源与设计文档要求。

**指令**:

1. 更新 `README.md`:阶段 B 能力、全 Docker 启动、MCP 与 GLM 配置、Skills 目录说明。
2. `docs/SETUP.md` 补充 ES/Kafka/MinIO、Node 版本、GitHub PAT。
3. 添加 `LICENSE`(MIT 或 Apache-2.0,择一并在 README 声明)。
4. 扫描仓库:确保无 `.env`、无真实密钥、无 `target/` 提交。

**验证**:

- 新克隆仓库按 README 可在 2 小时内跑通 G-B 门禁(允许 manual 密钥)。
- `git grep -i "sk-"` 或常见密钥模式无命中(人工+工具)。

**依赖**:B-24。

---

### 阶段 B 门禁(全部 B 步骤完成后执行)

| # | 门禁项 | 验证方式 |
|---|--------|----------|
| G-B1 | 构建 | `mvn clean test` 默认 profile 全绿 |
| G-B2 | 知识库 | Web 分片上传 PDF → READY → 检索调试命中 |
| G-B3 | 对话+RAG | `/chat` 默认 RAG;禁用开关有效 |
| G-B4 | 论文 | Web 三步完成;SSE 有 complete;可下载 docx |
| G-B5 | GLM | settings 切换 glm 后新会话可对话(manual) |
| G-B6 | MCP | GitHub 搜索 tool 成功;filesystem 白名单生效 |
| G-B7 | Skill | code-review 选中后行为符合 SKILL;user skill 放文件夹可加载 |
| G-B8 | CLI | login + chat + kb list + paper status 成功 |
| G-B9 | 开源卫生 | LICENSE、.env.example、无密钥提交 |
| G-B10 | 遗留隔离 | `PaiSmart-main`、`paper-agent` 仍无改动 |

---

## 3. 阶段 C：剩余功能补全 + 前端体验细化

> 说明：C 阶段承接阶段 B 编码与手测后的遗留问题，重点是“功能链路补齐、CLI 交付完善、前端视觉细化、门禁记录固化”。

### 步骤 C-01：修复 `/chat` 普通 WebSocket 路径未接 RAG

**目标**：让聊天页默认对话真正复用知识库检索能力。

**指令**：

1. 统一普通 WebSocket 对话路径与 Harness/RAG 注入链路。
2. 第一版可接受牺牲 token 级实时流式，优先保证功能链路统一。
3. 修复后验证默认 RAG 与“本次不使用知识库”开关都生效。

**验证**：

- 手动：上传已知知识库文档后，在 `/chat` 默认提问能命中；关闭知识库后回答变差或无法命中。
- 自动化：补覆盖 WS 普通路径能接入 RAG 的测试。

**依赖**：G-B3 当前待修问题。

---

### 步骤 C-01A：将 `search_knowledge` 改为通过执行上下文隐式获取当前用户

**目标**：避免模型在知识库工具调用时继续向用户索要 `userId`。

**指令**：

1. 从 `search_knowledge` 的工具 schema 中移除显式 `userId` 参数。
2. 工具执行时通过 `ToolExecutionContext` 获取当前用户 ID。
3. 若缺少上下文，则工具返回失败而不是误查全库。

**验证**：

- 单元测试：有当前用户上下文时可正常检索。
- 单元测试：缺少上下文时返回失败。
- 手动：聊天页不再向用户索要 `userId`。

**依赖**：C-01。

---

### 步骤 C-02A：排查并修复 MCP / Skill 的结构化工具调用兼容性

**目标**：确认模型为什么输出伪工具调用文本，而不是真正返回结构化 `tool_calls`。

**指令**：

1. 在 Harness / Provider 侧补充诊断日志。
2. 记录：
   - 当前轮模型可见工具名
   - finishReason
   - 是否出现结构化 `tool_calls`
   - 若无 `tool_calls` 但内容像伪工具调用，则输出预警日志
3. 基于诊断结果再决定是模型兼容性问题、prompt 干扰，还是 provider/tool-calling 对齐问题。

**验证**：

- 自动化：相关测试仍通过。
- 手动：复现 `code-review` 场景时，后端日志可用于判断模型是否真正返回了 `tool_calls`。

**依赖**：C-02。

---

### 步骤 C-02：优化 Skill / MCP 中间处理内容展示

**目标**：减少 Skill prompt、处理中说明、原始代码片段直接暴露给最终用户。

**指令**：

1. 第一轮先只修前端展示结构，不改后端事件分层。
2. 将中间处理性内容与最终回答在 UI 上做明显区隔，必要时弱化或折叠显示。
3. 保持现有 MCP / Skill 功能链路不变。

**验证**：

- 手动：`code-review` Skill 仍可工作，但用户看到的聊天结果更聚焦最终结论。

**依赖**：G-B7 当前体验问题。

---

### 步骤 C-03：补齐 GitHub MCP 真实联调

**目标**：完成 G-B6 剩余手测。

**指令**：

1. 配置 GitHub PAT。
2. 启用 GitHub MCP。
3. 在真实会话中验证 GitHub 搜索 / 工具调用结果。

**验证**：

- 手动：GitHub MCP 工具可发现、可调用、返回结果可用。

**依赖**：B-16、B-17。

---

### 步骤 C-04：补齐 CLI 终端命令化入口

**目标**：让 CLI 不只是 Java 主类可运行，而是可在终端中直接执行。

**指令**：

1. 第一版先补 Windows 下可用的 `yanban.bat` 或等价脚本入口。
2. 让 `yanban login`、`yanban kb list`、`yanban paper status <taskId>` 可以在终端中直接执行。
3. 再补真实 CLI 手测记录。

**验证**：

- 手动：终端中直接运行命令，而不依赖 IDEA Program arguments。

**依赖**：B-22、B-23、G-B8。

---

### 步骤 C-05：补齐论文链路最终手测记录

**目标**：把论文页完整通过结果固化到门禁记录中。

**指令**：

1. 复测论文上传、任务状态流转、SSE、下载。
2. 将结果回填到 `test-checklist.md` 与 `progress.md`。

**验证**：

- 手动：论文链路完整通过，并形成留档记录。

**依赖**：G-B4。

---

### 步骤 C-06：前端视觉细化第一批（ChatGPT 风格）

**状态说明**：已完成，不再重复执行。

---

### 步骤 C-07：前端视觉细化第二批（论文 / 知识库 / 设置）

**目标**：将核心工作台页面统一到新视觉语言。

**指令**：

1. 继续按 `memory-bank/design.md` 与其中合并的实施清单执行。
2. 改造：
   - `frontend/src/views/PaperPage.vue`
   - `frontend/src/views/KnowledgeBasePage.vue`
   - `frontend/src/views/KnowledgeSearchDebugPage.vue`
   - `frontend/src/views/SettingsPage.vue`
3. 只改视觉，不动功能。

**验证**：

- 手动：浅色 / 深色切换可用，页面风格统一，主工作区层级清晰。

**依赖**：C-06。

---

### 步骤 C-08：前端视觉细化第三批（认证页 / 收尾）

**目标**：统一登录注册页与辅助页面风格。

**指令**：

1. 改造：
   - `frontend/src/views/LoginPage.vue`
   - `frontend/src/views/RegisterPage.vue`
2. 必要时微调 `router/index.ts` 文案与导航细节。
3. 不引入新依赖。

**验证**：

- 手动：登录 / 注册页风格与主应用一致。

**依赖**：C-07。

---

### 步骤 C-09：补齐阶段 B 门禁剩余记录

**目标**：把已完成但未固化的手测结果落档，并明确剩余未通过项。

**指令**：

1. 回填：
   - G-B4 论文
   - G-B5 GLM
   - G-B6 GitHub MCP（若完成）
   - G-B8 CLI（若完成）
2. 明确保留仍未通过项。

**验证**：

- `progress.md` 与 `test-checklist.md` 内容一致。

**依赖**：C-03、C-04、C-05。

---

### 步骤 C-10：开源前最终复核（待环境允许）

**目标**：完成 `.git` 可见环境下的最终隔离与卫生核验。

**指令**：

1. 执行 git-diff 级遗留隔离检查。
2. 再做一轮密钥 / `.env` / 忽略目录复核。

**验证**：

- `private-helper-agent/` 外无变更；开源卫生最终通过。

**依赖**：G-B9、G-B10。

---

## 4. 第二期实施计划：面向 LaTeX 论文的 AI 审稿改稿 + 文献补全

> 说明：第二期主线为「面向 LaTeX 论文的、有据可查的 AI 审稿/改稿 + 文献补全助手」。输入输出绑定 LaTeX（`.tex` + 本地 `.bib`），产出可直接在 VSCode 编译。原 docx 解析路线作废。完整设计见 `discussion_about_fix_paper_20260615.md`（共 15 节），产品范围见 `second-phase-design.md`，质量专项见 `paper-quality-plan.md`。GitHub MCP 与 CLI 已有代码保留，但不作为第二期门禁，统一放入后续拓展。
>
> 能力分层（详见讨论纪要第 2 节）：L0 LaTeX 解析+结构分析、L1 语言润色、L2 结构审查、L3 文献地基、L4 研究级建议、L5 多模态图 critique（后置）。
>
> 防幻觉铁律（不可破坏）：任何被推荐论文必须来自检索 API 真实返回（有可解析 DOI / arXiv id），模型只能从候选列表选并解释，绝不凭空写引用。

---

# 阶段 D：论文专项前置澄清与技术边界（LaTeX）

> 目标：正式编码前固定数据模型、LaTeX 解析与占位保护边界、角色识别、Prompt 输出格式、文献检索边界、编排状态机，避免 E 阶段返工。所有决策已在讨论纪要中对齐，本阶段是把决策落到 `architecture.md` 与各设计文档。

### 步骤 D-01：论文专项数据模型与存储策略确认

**目标**：固定 L0–L4 全流程的数据落点（对应讨论纪要第 12 节）。

**指令**：

1. 复用/新建混合：扩展 `paper_tasks`（加 `input_format`(tex/zip)、`mode`(basic/advanced)、`main_entry` 等）、复用 `paper_task_rounds` 作事件日志。
2. 新增结构化表：`paper_sections`、`paper_task_analysis`、`suggestions`、`suggestion_evidence`、`paper_task_literature`、`paper_task_clarifications`、`paper_task_artifacts`；全局表 `literature_cards`。
3. 公私分离：`literature_cards` 全局共享、无 userId；其余按任务带 userId/taskId。
4. 文件/产物走 MinIO（沿用 object_key 模式），库内只存 object_key + 元数据；完整解析模型细节存 MinIO JSON 产物。
5. 文献身份去重：`literature_cards` 用显式 id 列（doi/arxiv_id/openalex_id/s2_id 各可空唯一索引）+ title_hash，配 upsert-by-any-id 合并流程。
6. 将最终表结构与字段同步到 `architecture.md`。

**验证**：

- 人工检查：E 阶段每个阶段需要的数据都有明确落点。
- 新增迁移需补 Repository 测试。

**依赖**：B-10。

---

### 步骤 D-02：LaTeX 解析（L0）与占位保护边界确认

**目标**：固定务实分词器范围、文档模型字段、占位保护机制、解析边界（对应讨论纪要第 7、10 节）。

**指令**：

1. 务实分词（非完整解析）：只认分节命令、`\begin/\end` 环境、受保护内联（`$...$`/数学环境/`\cite`系/`\ref`系/`\label`/`\includegraphics`/`\input`/`\include`）、注释；其余当不透明正文。
2. 文档模型字段集（A–E 补充）：preamble（只读但抽取 title/authors/keywords）、sections[level/title/role/rawRange/blocks]、protectedSpans、floats[含 rawContent 表格原文]、citeUsage[认全部引用命令变体]、crossRef[认全部 `\ref` 家族]、bib[支持外部 `.bib` 与内联 `\begin{thebibliography}`]、sourceMap。
3. 占位保护（mask）：送模型前把受保护元素换占位符（⟦MATH_1⟧等）；返回后校验占位符集合 ⊆ 输入集合再原样换回。
4. 边界：verbatim/lstlisting/minted 整块不透明；自定义重定义分节命令第一版不支持；`\section*` 标 unnumbered；appendix 标 Appendix；支持 ctex/CJK 中文。
5. 第一版不装 TeX、不真编译，只做静态结构分析；真编译 latexmk 后置。
6. 同步边界说明到 `paper-quality-plan.md`。

**验证**：

- 中文 / 英文 fixture 可解析出章节与受保护元素。
- 含 verbatim / 自定义命令文档不崩溃，按边界规则处理。

**依赖**：D-01。

---

### 步骤 D-03：角色识别与结构确认检查点边界确认

**目标**：固定章节角色识别三级方案与交互检查点（对应讨论纪要第 10.3、11.9 节）。

**指令**：

1. 角色枚举：Intro / RelatedWork / Method / Experiments / Results / Discussion / Conclusion / Abstract / References / Appendix / unknown。
2. 三级识别：启发式（关键词中英双语 + 位置 + 内容信号）→ LLM 复核（只发标题/信号，限定枚举内选，不许发明角色）→ 用户可改（第一版必须有，用户最终权威）。
3. 结构确认检查点：按内容位置判断相关工作等是否真缺失（避免「揉进引言」误报）；检测到歧义不下结论，批量问一次 + 默认「保持原样」+「全部保持」一键通过；区分阻塞类（先问）vs 提示类（带默认继续）。
4. 默认尊重用户结构，只有明确同意才重构。
5. 同步到 `paper-quality-plan.md`。

**验证**：

- 人工检查：识别结果可被用户覆盖；歧义可批量确认。

**依赖**：D-02。

---

### 步骤 D-04：Prompt 变量与模型 JSON 输出格式规范

**目标**：固定 Prompt 模板变量与结构化输出（对应讨论纪要第 11、14 节）。

**指令**：

1. 定义 prompt 变量表：`targetLanguage`、`paperTitle`、`researchProfile`、`sectionTitle`、`sectionText`、`scoreThreshold`、`reviewComments`、`literatureCandidates` 等。
2. 角色复核 JSON：每节 role（限定枚举）+ 缺失标准角色列表。
3. 研究画像 JSON：problem、method、contributions、datasets、baselines、metrics、tasks、keywords。
4. section-polish 输出：标签式 `<output>` / `<explanation>`，占位符必须保留。
5. section-review JSON：score、passed、issues[severity]、suggestions。
6. gap/suggestion JSON：track(advocacy/critique)、category、severity(blocker/minor)、statement、evidence[卡片 id]、applicable、patch。
7. 文献抽取 JSON（L3c）：problem、method、datasets、baselines、metrics、contributions、tasks、keywords、role(background/competitor/baseline)。
8. 明确 JSON 解析失败的降级策略。
9. 同步到 `paper-quality-plan.md`。

**验证**：

- Prompt 渲染测试覆盖所有必填变量。
- Mock 模型返回 JSON 可解析，非 JSON 有明确降级。

**依赖**：D-03。

---

### 步骤 D-05：文献检索源与防幻觉边界确认

**目标**：固定 L3 文献检索的可复用抽象与防幻觉边界（对应讨论纪要第 8、9 节）。

**指令**：

1. `LiteratureSource` provider 抽象：第一版 OpenAlex + arXiv 免费源；Semantic Scholar 做可插拔 provider，填 key 即启用。
2. `LiteratureService` 通用层：只做「查 + 去重 + 缓存 + 卡片 + 排序」，分 LIGHT / DEEP 两档；阶梯/剧本逻辑留论文模块。
3. 读取深度：只读摘要（用户 `.bib` 已有 + 各源检索回来的相关论文摘要）。
4. identity 主键：DOI > arXiv id > OpenAlex/S2 id > 规范化标题哈希；三层缓存 raw（可选）/card（必存）/analysis（必存，永久复用）。
5. 存储分工：MySQL 当账本，ES 后置但留排序/召回抽象入口；第一版 MySQL + 内存向量 cosine 排序。
6. 防幻觉铁律：推荐论文必须来自检索 API 真实返回，模型只能从候选选并解释。
7. 缓存只存公开文献，不存用户私有论文；文献语料上 ES 用独立索引与私有 KB 分开。
8. 同步到 `architecture.md`、`paper-quality-plan.md`。

**验证**：

- 人工检查：抽象层可插拔，对话与论文模块复用同一 `LiteratureService`。

**依赖**：D-01。

---

### 步骤 D-06：编排状态机与可恢复阶段步确认

**目标**：固定任务状态机、阶段链、交互检查点、SSE 事件（对应讨论纪要第 13 节）。

**指令**：

1. 阶段链：PARSE → ROLE_RECOGNITION → STRUCTURE_CHECK → PROFILE → RETRIEVE → GAP_ANALYSIS → POLISH → ASSEMBLE → COMPLETE（GAP 先 POLISH 后）。
2. 新增状态 `WAITING_INPUT`：交互检查点释放线程（不忙等），用户答后从下一阶段续。
3. 可恢复阶段步重构：每阶段从持久化状态读输入、写持久化输出；dispatcher 按 current_stage 续跑。
4. 崩溃恢复 v1 做方案 A（阶段可恢复 + 等用户续跑；服务器崩溃后手动重启），自动续跑 B 后置。
5. 长阶段频繁 checkpoint：RETRIEVE 每批文献、POLISH 每章每轮。
6. SSE 事件清单扩充（保留旧事件兼容）：parse_start/parse_done、roles_ready、clarification_needed/clarification_resolved、profile_ready、retrieve_start/retrieve_progress/cards_ready/ranking_done/selection_done、gap_analysis_done/suggestions_ready、section_polish_start/section_polished/polish_done、assemble_done/artifact_ready、complete、error、paused/resumed/stopped。
7. 同步到 `architecture.md`。

**验证**：

- 人工检查：每阶段输入输出有持久化落点，WAITING_INPUT 可续跑。

**依赖**：D-01。

---

### 步骤 D-07：论文页第二期展示范围确认

**目标**：明确第二期论文页展示范围（对应讨论纪要第 7 节）。

**指令**：

1. 在线预览：第一版为源码级高亮 + 逐条勾选采纳（非渲染 PDF）。
2. 两个入口按钮：基础版（不改原文/原 bib，只推荐）/进阶版（改原文 `.tex` + `.bib` 自动补 `\cite`）。
3. 展示：当前阶段、章节进度、角色识别结果（可改）、结构确认问答、审查报告（每条挂真实引用）、suggested.bib、下载三件套。
4. 新增内容诚实分级展示：A 类（相关工作/引用，真实检索）可插真内容；B 类（实验/小节）只插骨架 + 占位 + 理由。
5. 与 F 阶段前端目标保持一致。

**验证**：

- 在 F 阶段中保持前端目标与本步骤一致。

**依赖**：D-01。

---

# 阶段 E：LaTeX 论文真实处理流水线

> 目标：实现 L0–L4 真实处理流水线。专项设计见 `paper-quality-plan.md` 与讨论纪要。

### 步骤 E-01：LaTeX 解析与文档模型（L0 / PARSE）

**目标**：从 `.tex` + `.bib` 构建文档模型，并早暴露致命错误。

**指令**：

1. 输入形态：第一版同时支持 zip + 单文件，内部拍平成单一逻辑文档，要求唯一 main 入口。
2. 实现务实分词器与文档模型构建（按 D-02 字段集），bib 解析支持外部 `.bib` 与内联 `\begin{thebibliography}`。
3. 构建 protectedSpans 与占位保护数据。
4. 硬性 lint 早在本阶段就跑（悬空 `\cite`、断 `\ref`/`\eqref`、括号/环境不配平、bib 重复 key），blocker 级问题即时上报。
5. 将文档模型 JSON 产物写入 MinIO，sections 写入 `paper_sections`。

**验证**：

- 单元测试：fixture 可解析出章节、受保护元素、bib 条目（含内联）。
- 单元测试：含致命错误的 fixture 能被硬 lint 捕获。
- 手动：上传真实 LaTeX 项目，日志显示章节数、受保护元素数、bib 条目数。

**依赖**：D-02、D-06。

---

### 步骤 E-02：角色识别 + 结构确认检查点（L0 / ROLE_RECOGNITION + STRUCTURE_CHECK）

**目标**：识别章节角色并在结构歧义时与用户确认。

**指令**：

1. 启发式角色识别（关键词/位置/内容）→ LLM 复核（限定枚举）→ 写入 `paper_sections.role` 与 role_confidence/role_source。
2. 按内容位置判断相关工作等是否真缺失。
3. 检测到阻塞类歧义：写 `paper_task_clarifications`（pending），状态置 `WAITING_INPUT`，发 `clarification_needed`，释放线程。
4. 用户答复 API 写回答案，dispatcher 从下一阶段续跑；提示类歧义带默认继续。
5. 用户可改角色 API（最终权威）。

**验证**：

- 单元测试：歧义触发 WAITING_INPUT，答复后续跑。
- 手动：相关工作揉进引言的样例不误报「缺失」。

**依赖**：E-01、D-03。

---

### 步骤 E-03：Prompt 资源体系化

**目标**：将论文处理 prompt 抽离为可维护资源。

**指令**：

1. 在 `yanban-paper/src/main/resources/prompts/` 建立 prompt 文件。
2. 至少包含：role-confirm、research-profile、section-polish、section-review、literature-extract、gap-analysis、relatedwork-gen、contribution-gen、abstract。
3. 支持 `targetLanguage=zh/en` 差异化变量。
4. 增加 Prompt 渲染服务与单元测试。

**验证**：

- 单元测试：变量渲染正确，缺必填变量报错。
- 人工检查：prompt 无真实密钥与环境特定路径。

**依赖**：D-04。

---

### 步骤 E-04：结构化研究画像（L3 种子 / PROFILE）

**目标**：抽取结构化研究画像，作为检索 query 种子与 gap 分析基准。

**指令**：

1. 调模型抽取：problem、method、contributions、datasets、baselines、metrics、tasks、keywords。
2. 优先要求 JSON 输出；解析失败保留原文并标记 degraded。
3. 写入 `paper_task_analysis.research_profile`。
4. SSE 输出 `profile_ready`。

**验证**：

- Mock LLM 测试：画像 JSON 被解析并持久化。
- 手动：画像与论文主题相关。

**依赖**：E-03。

---

### 步骤 E-05：文献检索与卡片地基（L3 / RETRIEVE）

**目标**：多源检索、去重缓存、读摘要抽取、排序选片，产出概念阶梯。

**指令**：

1. 实现 `LiteratureSource`（OpenAlex + arXiv）与 `LiteratureService`（查+去重+缓存+卡片+排序，DEEP 档）。
2. 从研究画像生成多个检索 query（多轴：体制/任务/方法/特征）；引言驱动 + 内容驱动召回。
3. identity 去重 + 与用户 `.bib` 去重标记；查 `literature_cards` 缓存命中取卡片+分析，未命中才读摘要 + LLM 抽取（L3c）并落库。
4. 相关性打分（语义相似度为主 + 引用重叠 + 关键词重叠 + 时效性/影响力轻权 + 已在 .bib 降权）。
5. 分层配额选片（每层保底含奠基作）+ 层内 MMR 去冗余；产出概念阶梯（优势组/劣势组各挂文献）+ gap。
6. 写入 `paper_task_literature`（含 relevance_score、narrative_role、ladder_node）与 `paper_task_analysis.concept_ladder`。
7. 立场铁律：引言/相关工作站用户这边（找现有方法局限反衬）；审稿意见站审稿人这边。
8. 长阶段每批文献 checkpoint；SSE 输出 retrieve_start/retrieve_progress/cards_ready/ranking_done/selection_done。

**验证**：

- Mock HTTP 测试：OpenAlex / arXiv 响应解析正确。
- 单元测试：identity 去重与缓存命中生效；推荐均来自真实返回。
- 手动：真实论文返回相关文献与概念阶梯。

**依赖**：E-04、D-05。

---

### 步骤 E-06：gap 分析与建议生成（L4 / GAP_ANALYSIS）

**目标**：把概念阶梯 + 局限 + gap 翻译成可采纳的 Suggestion。

**指令**：

1. 计算对比矩阵（任务/问题、数据集、baseline/对比方法、消融、评价指标、相关工作覆盖）。
2. 生成 Suggestion：track(advocacy/critique)、category、severity、statement、evidence[真实卡片]、applicable、patch。
3. A/B 判据按「能否 grounding」：A 类可插真内容（相关工作段落/引用/靠真实局限反衬的贡献陈述）；B 类只插骨架（实验/数据，绝不编造）。
4. 立场分流：辩护轨进 tex 补丁；批评轨进审查报告（可带 B 骨架）。
5. 诚实闸门：吹不动就转批评，绝不编造别人局限。
6. L2 软审查汇入 suggestions（缺章节检查在 STRUCTURE_CHECK 之后）。
7. 锚点靠 L0 角色，缺失角色→新建节补丁；grounding 校验每个 `\cite` key 可解析到 suggested.bib。
8. 优先级 + 分组 + 配额（每类约 5 条可展开）；写入 `suggestions` 与 `suggestion_evidence`。
9. SSE 输出 gap_analysis_done / suggestions_ready。

**验证**：

- Mock LLM 测试：Suggestion JSON 解析并落库；evidence 均挂真实卡片。
- 单元测试：无真实支撑的贡献被转为批评，不进 tex。
- 手动：输出至少一类有据可查的相关工作建议与一类审查建议。

**依赖**：E-05、D-04。

---

### 步骤 E-07：占位保护分章润色（L1 / POLISH）

**目标**：对每章 mask → 润色 → 校验 → unmask → lint → review → retry。

**指令**：

1. 逐章（preamble、References 除外）执行 mask → section-polish（允许实质重写，必须保留占位符）→ 校验占位符 ⊆ 输入集合 → unmask → 静态 lint。
2. section-review 返回 score + issues[severity] + suggestions；score 不达标且 attempt < maxAttempts(=2) 带审查意见重试。
3. 掉占位符：拒绝该次输出并重试；仍丢则保留原章节不润色。
4. 超长章节按子节/段落切块；Abstract 用摘要专用 prompt；References 不润色。
5. 后端自算词级 diff；写入 `paper_sections`（polish_status / 润色文本 / 审查 / diff）。
6. 长阶段每章每轮 checkpoint；SSE 输出 section_polish_start/section_polished/polish_done。

**验证**：

- Mock LLM 测试：低分触发重试，掉占位被拒绝。
- 单元测试：lint 捕获不配平/残留占位符。
- 手动：小型 LaTeX 逐章输出事件与 diff。

**依赖**：E-02、E-03。

---

### 步骤 E-08：三件套产出（ASSEMBLE）

**目标**：产出润色后 `.tex` + 审查报告 + suggested.bib，支持基础/进阶两模式。

**指令**：

1. 基础版：不改原文/原 bib，只产出 suggested.bib 与审查报告。
2. 进阶版：按用户逐条采纳的 patch 改写 `.tex` + `.bib`，自动补 `\cite`；采纳后过静态 lint（cite key 存在、括号/环境配平、受保护元素数量一致、无残留占位符）。
3. 审查报告每条挂真实引用，结尾免责声明（AI 自查、非同行评审）。
4. 三件套写入 `paper_task_artifacts`（polished_tex / suggested_bib / review_report），更新下载链路。
5. SSE 输出 assemble_done / artifact_ready / complete。

**验证**：

- 自动化：进阶版产出的 `.tex` 受保护元素数量与输入一致、无残留占位符。
- 手动：suggested.bib 可被 LaTeX 识别；进阶版可在 VSCode 编译（人工）。

**依赖**：E-06、E-07。

---

### 步骤 E-09：论文质量样例集与评价记录

**目标**：形成可重复对比的验收方式。

**指令**：

1. 准备中文 / 英文小型 LaTeX 样例（含 `.tex` + `.bib`，及一份无 `.bib`/内联 bibliography 样例）。
2. 记录原文、三件套结果、人工评价（含「推荐文献是否真实可溯源」核对）。
3. 在 `test-checklist.md` 增加论文质量专项条目。

**验证**：

- 至少 1 篇中文样例与 1 篇英文样例完成端到端记录。

**依赖**：E-08。

---

### 阶段 E 门禁

| # | 门禁项 | 验证方式 |
|---|---|---|
| G-E1 | LaTeX 解析 | fixture 可解析章节/受保护元素/bib（含内联），硬 lint 捕获致命错误 |
| G-E2 | 角色识别 + 检查点 | 歧义触发 WAITING_INPUT，用户可改/续跑，揉进引言不误报 |
| G-E3 | 研究画像 | 真实论文生成相关画像 |
| G-E4 | 文献地基 | 多源检索返回真实可溯源文献 + 概念阶梯，identity 去重生效 |
| G-E5 | gap 分析 | 产出有据可查的 Suggestion（evidence 挂真实卡片），诚实闸门生效 |
| G-E6 | 分章润色 | 占位保护 + 重试 + diff 生效，掉占位被拒 |
| G-E7 | 三件套 | 进阶版受保护元素一致、无残留占位、可编译 |

---

# 阶段 F：论文页体验完善与验收固化

> 目标：围绕真实流水线完善前端展示与手测记录。GitHub MCP 与 CLI 不进入第二期门禁。

### 步骤 F-01：阶段进度增强

**目标**：让用户清楚看到处理到哪一步。

**指令**：

1. 论文页展示当前阶段（新阶段链）、章节总数、当前章节、当前尝试次数。
2. 对新 SSE 事件做友好中文映射。
3. 保留原始事件日志作折叠调试信息。

**验证**：

- 手动：真实任务执行时页面能看出阶段与章节进度。

**依赖**：E-07。

---

### 步骤 F-02：结构确认检查点交互 UI

**目标**：把角色识别歧义做成用户可确认的交互。

**指令**：

1. 收到 `clarification_needed` 时展示批量问题 + 选项，默认高亮「保持原样」，提供「全部保持」一键通过。
2. 提交答复调用续跑 API；展示角色识别结果并允许用户手动改。
3. 区分阻塞类（必须答）与提示类（可跳过）。

**验证**：

- 手动：歧义可批量确认，默认保持不误重构。

**依赖**：E-02。

---

### 步骤 F-03：在线预览 + 逐条采纳

**目标**：源码级高亮预览 + 勾选采纳。

**指令**：

1. 展示章节 diff（源码级高亮，受保护部分不变）+ 逐条勾选采纳。
2. 两个入口：基础版（只推荐）/进阶版（改原文 + 自动补 `\cite`）。
3. 新增内容诚实分级展示：A 类真内容、B 类骨架 + 占位 + 理由。

**验证**：

- 手动：可逐条采纳并生成改后 tex；B 类只见骨架不见编造数据。

**依赖**：E-07、E-08。

---

### 步骤 F-04：审查报告 + suggested.bib 展示

**目标**：展示有据可查的审查结果与推荐文献。

**指令**：

1. 展示审查报告：每条 severity、statement、挂的真实引用；结尾免责声明。
2. 展示 suggested.bib 列表（标题/年份/作者/DOI/URL，可点击）。
3. 无结果时给明确提示。

**验证**：

- 手动：能看到审查建议与可点击的真实文献。

**依赖**：E-06、E-08。

---

### 步骤 F-05：端到端手测记录

**目标**：把第二期论文质量链路固化为验收记录。

**指令**：

1. 用中文 LaTeX 与英文 LaTeX 各跑完整流程（基础版 + 进阶版）。
2. 下载三件套并人工核对（含进阶版编译）。
3. 将结果写入 `test-checklist.md` 与 `progress.md`。

**验证**：

- `test-checklist.md` 中有中文 / 英文各一条完整记录。

**依赖**：F-01、F-02、F-03、F-04、E-08。

---

# 阶段 G：部署与拓展预留（不阻塞论文专项）

> 目标：保留部署与扩展规划，但不让 GitHub MCP / CLI 阻塞第二期论文主线。

### 步骤 G-01：部署文档草案维护

**目标**：保持部署文档可持续演进。

**指令**：

1. 维护 `docs/DEPLOYMENT.md`。
2. 记录 jar-first 与 Dockerfile-first 取舍，第二期默认不强制完成生产部署。
3. 补充未来 Nginx / HTTPS / 备份恢复待办。

**验证**：

- 文档中无过期路径、无真实密钥。

**依赖**：B-25。

---

### 步骤 G-02：GitHub MCP 后置拓展记录

**目标**：明确 GitHub MCP 暂缓，不阻塞第二期。

**指令**：

1. 在 `progress.md` 或后续计划中标记 GitHub MCP 为 deferred。
2. 保留已有代码，不删除。
3. 后续恢复时从 C-03 继续。

**验证**：

- 第二期门禁不再包含 GitHub MCP。

**依赖**：无。

---

### 步骤 G-03：CLI 后置拓展记录

**目标**：明确 CLI 命令化入口暂缓，不阻塞第二期。

**指令**：

1. 保留已有 CLI 代码。
2. 后续拓展中继续维护 `yanban.bat` / PATH 分发计划。
3. 第二期不要求 CLI 手测通过。

**验证**：

- 第二期门禁不再包含 CLI。

**依赖**：无。

---

### 步骤 G-04：第二期文档与门禁定稿

**目标**：完成论文专项交付记录。

**指令**：

1. 更新 README、SETUP、论文相关说明。
2. 更新 `progress.md` 与 `test-checklist.md`。
3. 明确 GitHub MCP / CLI 为后续拓展项。

**验证**：

- 第二期门禁全部围绕 LaTeX 论文主线。

**依赖**：阶段 E/F 已完成项。

---

## 第二期总门禁

| # | 门禁项 | 验证方式 |
|---|---|---|
| G-2-1 | LaTeX 解析 | 中文/英文 + 内联 bibliography fixture 可解析，硬 lint 捕获致命错误 |
| G-2-2 | 角色识别 + 检查点 | 歧义触发 WAITING_INPUT，用户可改/续跑 |
| G-2-3 | 研究画像 | 真实论文生成相关画像 |
| G-2-4 | 文献地基 | 多源检索真实可溯源 + 概念阶梯，identity 去重生效 |
| G-2-5 | gap 分析 | Suggestion 有据可查（evidence 挂真实卡片），诚实闸门生效 |
| G-2-6 | 分章润色 | 占位保护 + 重试 + diff，掉占位被拒 |
| G-2-7 | 三件套 | 进阶版受保护元素一致、无残留占位、可编译 |
| G-2-8 | 论文页体验 | 展示阶段进度、检查点问答、预览采纳、审查报告、suggested.bib |
| G-2-9 | 基础质量 | 默认测试通过，无真实密钥泄露 |

---

## 后续拓展池（第二期不阻塞）

- 真编译 latexmk 与 PDF 渲染预览。
- 多文件写回（需细粒度 source map）。
- Semantic Scholar provider 启用。
- 文献语料独立 ES 索引与向量召回（替换内存 cosine）。
- 自动崩溃续跑（编排方案 B）。
- L4 B 类实验骨架自动插入增强；equations[] 一等引用实体（L0 的 F 项）。
- L5 多模态图 critique。
- GitHub MCP 真实联调、CLI 终端命令化入口、Skill 自动推荐、OCR Provider 增强。
- 服务器生产部署与 Nginx / HTTPS 完整落地、更多 OpenAI Compatible Provider。

---

## 5. 步骤总览索引

| 阶段 | 步骤编号 | 数量 |
|------|----------|------|
| 前置 | P-01~P-03 | 3 |
| A | A-01~A-19 + 门禁 G-A | 19 |
| B | B-01~B-25 + 门禁 G-B | 25 |
| C | C-01~C-10 | 10 |
| D | D-01~D-07 | 7 |
| E | E-01~E-09 + 门禁 G-E | 9 |
| F | F-01~F-05 | 5 |
| G | G-01~G-04 + 第二期总门禁 | 4 |

**合计**:82 个执行步骤 + 阶段门禁。

---

*实施时若与设计或 tech-stack 冲突,以 `memory-bank/design-doucment.md` 为准;步骤完成情况更新 `memory-bank/progress.md`,架构变更更新 `memory-bank/architecture.md`。*
