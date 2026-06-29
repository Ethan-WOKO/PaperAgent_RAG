# 研伴 Agent — 进度记录

> 文档作用概述：只记录**已完成事项、已验证结果、当前已确认问题**，不承载未来执行计划；后续执行计划统一写入 `implementation.md`，手测安排写入 `test-checklist.md`，具体改动流水写入 `revision-log.md`。
>
> 更新时间：2026-06-14

## 已完成

### P-01：创建新工程根目录与文档

- 已创建 `private-helper-agent/` 新工程根目录。
- 已创建模块目录：
  - `yanban-core`
  - `yanban-knowledge`
  - `yanban-paper`
  - `yanban-mcp`
  - `yanban-skills`
  - `yanban-api`
  - `yanban-cli`
  - `frontend`
  - `docs`
  - `skills/builtin/code-review`
  - `skills/user`
- 已创建 `private-helper-agent/.gitignore`。
- 已创建 `private-helper-agent/.env.example`，无真实密钥。
- 已创建 `private-helper-agent/docs/SETUP.md`。

### P-02 / P-03：Docker Compose 基础设施文件

- 已创建 `private-helper-agent/docs/docker-compose.yml`。
- 已包含：MySQL 8、Redis 7、Elasticsearch 8.10.4、Kafka、MinIO。
- 已用 Windows Docker CLI 执行 `docker compose config`，语法通过。

- 已在 Docker Desktop 启动后执行 `docker compose -f docs/docker-compose.yml up -d mysql redis`。
- 因本机 `3306` 已被占用，已将本项目 MySQL 映射端口调整为 `3307:3306`，并同步 `application-dev.yml` 与 `docs/SETUP.md`。
- 已验证 MySQL `SELECT 1`、数据库 `yanban_agent`、Redis `PONG`。

### A-01：Maven 父工程与模块聚合

- 已创建父 POM：`private-helper-agent/pom.xml`。
- 已创建各 Maven 子模块 POM。
- `yanban-api` 已依赖 `yanban-core`。
- 验证通过：使用 Windows JDK/Maven 执行 `mvn -q validate` 成功。
- 验证通过：执行 `mvn -q dependency:tree -pl yanban-api` 成功。

### A-02：启动模块与健康检查（代码部分）

- 已创建 Spring Boot 启动类：`YanbanApiApplication`。
- 已创建 `application.yml`、`application-dev.yml`。
- 已引入 actuator，并暴露 `/actuator/health`。

- 已在真实 MySQL/Redis 环境下启动 `yanban-api`。
- 已验证 `/actuator/health` 返回 `UP`。
- 已验证 Flyway 在 MySQL 中创建 `sys_users`、`agent_sessions`、`agent_messages`、`agent_tool_runs`、`flyway_schema_history`。
- 已完成真实 API 冒烟：注册成功、带 JWT 查询 `/api/v1/users/me` 成功、无 Token 返回 401。

### A-03：数据库迁移框架与系统用户表

- 已引入 Flyway MySQL 支持。
- 已创建迁移脚本：`V1__create_sys_users.sql`。
- 已创建 `sys_users` JPA Entity 与 Repository。
- H2 测试环境中 Flyway 迁移通过。

### A-04：用户注册与密码存储

- 已实现 `POST /api/v1/auth/register`。
- 密码使用 BCrypt 存储。
- 重复用户名返回 `409 Conflict`。
- 已补充集成测试：注册成功、BCrypt 校验、重复用户名。

### A-05：JWT 登录、刷新与 Security 过滤链

- 已实现：
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/refresh`
  - `GET /api/v1/users/me`
- JWT 签名密钥来自配置 `yanban.jwt.secret`，默认映射环境变量 `JWT_SECRET`。
- Security 放行 `/api/v1/auth/**` 与 `/actuator/health`，保护 `/api/v1/**`。
- 已补充集成测试：未带 Token、伪造 Token、登录后访问当前用户。

### A-06：`yanban-core` 会话与消息领域模型

- 已在 `yanban-core` 添加 Agent 持久化实体与 Repository：
  - `AgentSession`
  - `AgentMessage`
  - `AgentToolRun`
- 已在 `yanban-api` 添加迁移脚本：`V2__create_agent_tables.sql`。
- 已创建表设计：`agent_sessions`、`agent_messages`、`agent_tool_runs`。
- 已补充 `@DataJpaTest`：插入 1 个 session、2 条 message、1 条 tool run，并按 session 查询验证。

### A-07：DeepSeek ModelProvider（非流式）

- 已在 `yanban-core` 定义模型抽象：
  - `ChatModelProvider`
  - `ChatRequest`
  - `ChatResponse`
  - `ChatMessage`
  - `ToolCall`
  - `ToolSpec`
  - `ModelProviderException`
- 已实现 `DeepSeekModelProvider#chat`，使用 OpenAI 兼容 Chat Completions 格式。
- 已实现 `DeepSeekProperties`，配置前缀为 `yanban.model.deepseek`。
- 已在 `application-dev.yml` 增加 DeepSeek 配置块，密钥来自 `${DEEPSEEK_API_KEY}`。
- 已补充 Mock HTTP 单元测试：验证请求 payload、Bearer Header、响应解析、HTTP 错误映射、缺失 API Key 错误。

### A-08：DeepSeek 流式输出

- 已扩展 `ChatModelProvider#streamChat`。
- 已新增 `ChatChunk`。
- 已实现 `DeepSeekModelProvider#streamChat`，支持解析 DeepSeek/OpenAI 兼容 SSE：
  - `data: {...}` token chunk
  - `finish_reason`
  - `data: [DONE]`
- 已补充 Mock SSE 单元测试：验证多 chunk 合并与 done 事件。

### A-09：Tool 抽象与注册表

- 已在 `yanban-core` 添加 Tool 抽象：
  - `ToolDefinition`
  - `ToolCall`
  - `ToolResult`
  - `ToolExecutor`
  - `ToolRegistry`
- 已实现内置测试工具 `EchoToolExecutor`。
- 已实现 `ToolRegistryConfig`，启动时收集 `ToolExecutor` Bean 并注册。
- 已补充单元测试：echo 执行、OpenAI tools 格式导出、重复注册拒绝、未知工具拒绝。

### A-10：Harness 最小循环（无 RAG、无 MCP）

- 已新增 `yanban-core` Harness 包：`com.yanban.core.harness`。
- 已实现：
  - `HarnessRequest`
  - `HarnessResult`
  - `HarnessEngine`
  - `HarnessConfig`
  - `HarnessException`
- Harness 行为：
  - 拼接 history + 当前用户消息。
  - 每轮调用 `ChatModelProvider#chat`。
  - 请求中携带 `ToolRegistry#listToolsForModel()`。
  - 若模型返回 `tool_calls`，解析 function arguments JSON。
  - 调用 `ToolRegistry#execute` 执行工具。
  - 将工具结果写回 role=`tool` 消息并进入下一轮。
  - 若模型返回普通 assistant 文本，则结束。
  - 达到 `maxSteps` 后返回失败结果。
- 已补充单元测试：工具调用后最终回答、直接文本回答、连续工具调用触发 max_steps 终止。

### A-11：Harness 与会话持久化集成

- 已在 `yanban-api` 增加 Agent REST API：
  - `POST /api/v1/agent/sessions`
  - `GET /api/v1/agent/sessions`
  - `GET /api/v1/agent/sessions/{sessionId}/messages`
  - `POST /api/v1/agent/sessions/{sessionId}/messages`
- 已实现 `AgentService`：
  - 创建会话并保存 provider/model/maxSteps/ragDisabled 快照。
  - 查询当前用户会话列表。
  - 查询当前用户指定会话消息历史。
  - 发送消息时加载历史、调用 `HarnessEngine`、持久化本轮 user/assistant/tool 消息。
  - 校验 session 归属，其他用户访问返回 404。
- 已调整 `YanbanApiApplication`，通过 `@EntityScan` 与 `@EnableJpaRepositories` 扫描 `com.yanban`，让 API 模块识别 `yanban-core` 的实体与 Repository。
- 已补充集成测试：创建会话、发送消息并持久化、越权访问拒绝、会话列表仅返回当前用户数据。
- 已更新 `docs/API-smoke.md` 增加 Agent 会话 API 示例。

### A-12：知识库简化实现（阶段 A）

- 已为 `yanban-knowledge` 模块补齐依赖：Spring Web、JPA、Validation、Security、Tika、测试依赖。
- 已在 `yanban-api` 添加迁移脚本：`V3__create_kb_tables.sql`。
- 已创建知识库表：
  - `kb_documents`
  - `kb_chunks`
- 已在 `yanban-knowledge` 实现：
  - `KbDocument` / `KbChunk` 实体与 Repository
  - `KnowledgeIngestionService`（同步解析 + 固定长度分块）
  - `KnowledgeSearchService` / `SimpleKnowledgeSearchService`
  - `KnowledgeController`
- 已开放阶段 A 临时接口：
  - `POST /api/v1/kb/documents/simple-upload`
  - `POST /api/v1/search`
- 简化上传行为：
  - 同步用 Tika 提取文本
  - 按固定长度分块写入 `kb_chunks`
  - `kb_documents.status` 从 `PROCESSING` 置为 `READY`
- 简化检索行为：
  - 用 SQL `LIKE` 做当前用户可见文档检索
  - 权限规则：当前用户私有 + 所有公开文档
  - 返回 `documentId`、`filename`、`chunkIndex`、`chunkText`、`score`
- 已实现 `search_knowledge` 工具：`SearchKnowledgeToolExecutor`。
- 已补充单元/集成测试：
  - tool 执行返回检索结果
  - owner 可搜索自己的私有文档
  - 其他用户不能搜索他人私有文档
  - 公开文档可被其他用户搜索到
- 已更新 `docs/API-smoke.md` 增加 KB 简化上传与搜索接口说明。

### A-13：默认 RAG 与“禁用知识库”开关

- 已在 `yanban-core` 新增 RAG 抽象：
  - `KnowledgeContextProvider`
  - `KnowledgeSnippet`
- 已在 `yanban-knowledge` 实现 `KnowledgeSearchContextProvider`，将 `KnowledgeSearchService` 结果适配为 Harness 可用的知识片段。
- 已扩展 `HarnessRequest`，增加：
  - `userId`
  - `ragDisabled`
- 已扩展 `HarnessEngine`：
  - 当 `ragDisabled=false` 且存在 `KnowledgeContextProvider` 时，先按当前用户与当前问题检索知识库。
  - 若命中结果，则自动插入一条 role=`system` 的知识库上下文消息。
  - 若 `ragDisabled=true`，则跳过知识库检索。
- 已在 `AgentService` 中打通 `ragDisabled`：
  - 单次请求优先使用 `SendMessageRequest.ragDisabled`
  - 否则回退到会话 `ragDisabled` 配置
- 已补充 Harness 单元测试：
  - 开启 RAG 时会调用 `KnowledgeContextProvider`
  - 关闭 RAG 时不会调用 `KnowledgeContextProvider`
  - 开启 RAG 时模型收到的消息中包含知识库 system context

### A-14：WebSocket 流式对话

- 已在 `yanban-api` 引入 `spring-boot-starter-websocket`。
- 已新增 WebSocket 包：`com.yanban.api.ws`。
- 已实现：
  - `WebSocketConfig`
  - `WebSocketAuthHandshakeInterceptor`
  - `ChatWebSocketHandler`
  - `WsChatRequest`
  - `WsChatEvent`
- 当前 WebSocket 端点：
  - `ws://localhost:8080/api/v1/ws/chat?token=<accessToken>`
- 当前协议：
  - 客户端发送 `sessionId/content/ragDisabled/skillId`
  - 服务端推送 `chunk` / `done` / `error`
- 已实现握手阶段 JWT 校验（query 参数 `token`）。
- 已实现阶段 A 流式行为：
  - 校验会话归属
  - 持久化本轮 user 消息
  - 调用 `ChatModelProvider#streamChat`
  - 按 token 推送 `chunk`
  - 完成后持久化 assistant 消息并推送 `done`
- 已补充单元测试：验证 chunk/done 事件发送及 user/assistant 持久化调用。
- 已新增 `docs/WEBSOCKET.md` 与 `docs/API-smoke.md` 中的 WebSocket 示例说明。

### A-15：用户设置 API（DeepSeek + max_steps）

- 已新增 Flyway 迁移：`V4__create_sys_user_settings.sql`。
- 已创建表：`sys_user_settings`。
- 已新增设置模块：
  - `SysUserSettings`
  - `SysUserSettingsRepository`
  - `UserSettingsService`
  - `UserSettingsController`
  - `UserSettingsRequest`
  - `UserSettingsResponse`
  - `SettingsCryptoService`
- 已实现接口：
  - `GET /api/v1/settings`
  - `PUT /api/v1/settings`
- 当前设置项包含：
  - `defaultProvider`
  - `deepseekApiKey`（仅写入，不明文返回）
  - `deepseekModel`
  - `deepseekTemperature`
  - `maxSteps`
  - `ragDefaultEnabled`
- `deepseekApiKey` 已采用 AES/GCM 加密落库，GET 仅返回 `deepseekApiKeyConfigured`。
- 已让 `AgentService.createSession(...)` 读取用户设置默认值：
  - `defaultProvider`
  - `deepseekModel`
  - `maxSteps`
  - `ragDefaultEnabled`（转换为 session 的 `ragDisabled`）
- 已补充测试：
  - 设置接口不回显明文 API Key
  - 落库密文可解密回原始值
  - 新建会话继承设置中的 provider/model/maxSteps/rag 默认值
- 已更新 `docs/API-smoke.md` 增加设置接口示例。

### A-16：Vue 工程初始化与认证页

- 已在 `frontend/` 初始化 Vite + Vue 3 + TypeScript 工程骨架。
- 已添加依赖配置：
  - `vue`
  - `vue-router`
  - `pinia`
  - `axios`
  - `naive-ui`
- 已创建前端基础文件：
  - `package.json`
  - `vite.config.ts`
  - `tsconfig*.json`
  - `index.html`
  - `src/main.ts`
  - `src/App.vue`
- 已实现前端基础模块：
  - `src/router/index.ts`
  - `src/api/http.ts`
  - `src/api/auth.ts`
  - `src/stores/auth.ts`
  - `src/components/AppLayout.vue`
- 已实现页面：
  - `/login`
  - `/register`
  - `/chat`（A-16 占位页）
  - `/settings`（A-16 占位页）
- 已实现认证能力：
  - Pinia `auth` store 持久化 access/refresh token
  - Axios 请求自动附加 Bearer Token
  - 401 自动清理本地 token 并跳转 `/login`
  - Router 守卫：未登录访问受保护路由时重定向到 `/login`
  - 已登录访问 `/login`、`/register` 时跳转到 `/chat`
- 已配置 Vite dev proxy：
  - `/api` -> `http://localhost:8080`
  - `/actuator` -> `http://localhost:8080`
- 受当前环境限制，尚未在 WSL 内执行 `pnpm install` / `pnpm dev`（本机缺少 `node`），待 Windows 侧或安装 Node 后进行前端运行验证。

### A-17：Vue 对话页（流式 + RAG 开关）

- 已新增前端 Agent API：`frontend/src/api/agent.ts`。
- 已将 `/chat` 从占位页改为真实对话页。
- 已实现：
  - 左侧会话列表
  - 新建会话按钮
  - 切换会话加载历史消息
  - 右侧消息区展示 user / assistant 消息
  - 输入框发送消息
  - 勾选框“本次不使用知识库”绑定 `ragDisabled`
- 已实现 WebSocket 对话流程：
  - 通过 `?token=<accessToken>` 建立连接
  - 发送 `sessionId/content/ragDisabled/skillId`
  - 接收 `chunk` 并实时追加 assistant 文本
  - 接收 `done` 后刷新历史消息
- 已实现首次发送自动创建会话。
- 已实现 Vite dev proxy 的 WS 支持：`/api` 增加 `ws: true`。
- 当前实现说明：
  - Enter 发送、Shift+Enter 换行
  - 发送完成后重新拉取 sessions/messages，确保页面与数据库保持一致
  - `/settings` 仍待 A-18 接入真实设置表单
- 受当前环境限制，尚未在 WSL 内执行前端运行时验证（缺少 `node` / `pnpm`）。

### A-18：Vue 设置页（阶段 A 范围）

- 已新增前端设置 API：`frontend/src/api/settings.ts`。
- 已将 `/settings` 从占位页改为真实设置页。
- 已接入后端接口：
  - `GET /api/v1/settings`
  - `PUT /api/v1/settings`
- 已实现设置表单字段：
  - `defaultProvider`（当前仅 deepseek）
  - `deepseekApiKey`
  - `deepseekModel`
  - `deepseekTemperature`
  - `maxSteps`
  - `ragDefaultEnabled`
- 已实现页面行为：
  - 页面加载时拉取当前用户设置
  - 保存时调用 `PUT /api/v1/settings`
  - 保存成功后清空 API Key 输入框
  - 页面仅显示 API Key 是否已配置，不回显示明文
  - 显示最近更新时间
- 页面文案已明确说明：API Key 仅写入后端保存，不会在页面回显。
- 受当前环境限制，尚未在 WSL 内执行前端运行时验证（缺少 `node` / `pnpm`）。

### A-19：阶段 A 中文 README 与 API 冒烟文档

- 已新增项目中文说明：`private-helper-agent/README.md`。
- README 已覆盖：
  - 项目简介
  - 环境要求
  - `.env` 与密钥说明
  - Docker 中间件启动
  - 后端启动
  - 前端启动
  - `/chat`、`/settings`、知识库简化接口说明
  - 已知限制与测试说明
- 已补充并校正 `docs/API-smoke.md`：
  - auth 返回字段统一为 `expiresIn`
  - chat / settings / websocket 冒烟说明与当前实现保持一致
- 已保留并复用 `docs/WEBSOCKET.md` 作为对话协议说明。

## 验证记录

- `mvn -q validate`：通过。
- `mvn -q dependency:tree -pl yanban-api`：通过。
- `mvn -q test`：通过（当前 29 个测试：core 14 个、knowledge 1 个、api 14 个）。
- `mvn -q clean test`：通过（父工程阶段 A 门禁 G-A1 通过）。
- 前端静态工程文件已生成；由于当前 WSL 环境缺少 `node`/`pnpm`，尚未执行前端构建验证。
- `node -v`：失败（WSL 当前无 Node.js），前端运行验证待 Windows 侧或补齐 Node 环境后执行。
- `docker compose config`：通过（Windows Docker CLI）。
- `docker compose up -d mysql redis`：通过（MySQL 使用宿主机 `3307` 端口）。
- Docker MySQL：`SELECT 1` 通过，`yanban_agent` 数据库存在。
- Docker Redis：`PONG` 通过。
- 真实后端健康检查：`/actuator/health` 返回 `UP`。
- 真实认证冒烟：注册、`/users/me`、无 Token 401 均通过。

## 环境注意事项

- WSL 中未安装 Linux JDK，`mvn` shell 脚本无法直接运行。
- WSL 中未启用 Docker CLI，但可通过 Windows `docker` CLI 操作 Docker Desktop。
- 当前可通过 Windows 命令运行 Maven：

```bash
cmd.exe /c "cd /d C:\\java_file\\private_helper_Agent\\private-helper-agent && set \"JAVA_HOME=C:\\software\\java\\17.0.5\" && C:\\software\\apache-maven-3.9.4-bin\\apache-maven-3.9.4\\bin\\mvn.cmd -q test"
```

## 阶段 A 门禁检查

- G-A1 构建：通过。`mvn -q clean test` 已通过。
- G-A2 认证：通过。已有真实冒烟记录（注册 / `/users/me` / 401）与集成测试。
- G-A3 对话：后端 WebSocket / Handler / 会话持久化链路测试通过；前端 `/chat` 代码已完成，但因当前 WSL 缺少 `node` / `pnpm`，浏览器侧手工流式验收待 Windows 侧执行。
- G-A4 RAG：通过。Harness RAG 单元测试、知识库权限集成测试已覆盖；前端“禁用知识库”开关代码已接入 WS 请求。
- G-A5 持久化：基本通过。会话 / 消息 Repository 与 API 集成测试通过，数据库持久化链路已验证；“重启后端后历史仍在”的完整人工复验待下一次 Windows 侧联调时补做。
- G-A6 安全：通过。`.env.example` 无真实密钥，当前 `private-helper-agent/` 下未发现 `.env` 文件。
- G-A7 遗留隔离：当前 WSL 工作目录不可见 `.git` 元数据，无法直接执行 git diff 门禁；从文件操作记录看未修改 `PaiSmart-main/`、`paper-agent/`，建议在 Windows Git 环境再补一次最终确认。

### B-01：启动 ES、Kafka、MinIO

- 已启动阶段 B 中间件：
  - Elasticsearch 8.10.4
  - Kafka 3.8.1
  - MinIO RELEASE.2025-04-22T22-12-26Z
- Kafka 镜像已从 `bitnami/kafka:latest` 调整为 `apache/kafka:3.8.1`：
  - 原因：拉取 bitnami latest 时遭遇镜像仓库 `429 Too Many Requests`
  - 当前版本已在本机成功启动并健康
- 已完成 Kafka 初始化：
  - 创建 topic：`file-processing`
- 已完成 MinIO 初始化：
  - 成功创建 bucket：`yanban-agent`
- 已完成 Elasticsearch 初始化：
  - 创建 index template：`yanban-kb-chunks-v1-template`
  - 匹配索引：`yanban-kb-chunks-v1*`
  - `dense_vector` 维度当前固定为 `1024`
- 已更新配置文件：
  - `yanban-api/src/main/resources/application-dev.yml`
    - 增加 `spring.kafka.bootstrap-servers`
    - 增加 `yanban.knowledge.elasticsearch.*`
    - 增加 `yanban.knowledge.minio.*`
    - 清理掉 `DEEPSEEK_API_KEY` 的明文默认值，改回环境变量读取
- 已更新文档：
  - `docs/SETUP.md`
  - `.env.example`
  - `.gitignore`

### B-02：Flyway 扩展 — KB 与论文表

- 已新增迁移：`V5__extend_kb_and_create_paper_tables.sql`。
- 已扩展 KB 表：
  - `kb_documents` 新增：
    - `object_key`
    - `mime_type`
    - `file_size`
    - `error_message`
  - `kb_chunks` 新增：
    - `es_doc_id`
- 已新增分片上传表：
  - `kb_chunk_uploads`
- 已新增论文表：
  - `paper_tasks`
  - `paper_task_rounds`
- 已在 `yanban-knowledge` 新增：
  - `KbChunkUpload`
  - `KbChunkUploadRepository`
- 已扩展：
  - `KbDocument`
  - `KbChunk`
- 已在 `yanban-paper` 新增：
  - `PaperTask`
  - `PaperTaskRound`
  - `PaperTaskRepository`
  - `PaperTaskRoundRepository`
- 已更新依赖：
  - `yanban-paper/pom.xml` 增加 JPA / test 依赖
  - `yanban-api/pom.xml` 增加 `yanban-paper` 依赖
- 已补充 Repository 测试：
  - `KnowledgeRepositoryTest`
  - `PaperRepositoryTest`
- 已修复 Flyway 在 H2 测试环境下对 `ALTER TABLE ... ADD COLUMN` 的兼容问题。

## 验证记录

- `docker compose -f docs/docker-compose.yml up -d elasticsearch kafka minio`：通过。
- `docker compose -f docs/docker-compose.yml ps`：ES / Kafka / MinIO 均为 healthy。
- `GET http://localhost:9200/_cluster/health`：返回 `green`。
- `kafka-topics.sh --list`：包含 `file-processing`。
- `mc mb --ignore-existing local/yanban-agent`：bucket 创建成功。
- `PUT /_index_template/yanban-kb-chunks-v1-template`：返回 `acknowledged=true`。
- `mvn -q test`：通过（当前 31 个测试：core 14 个、knowledge 2 个、paper 1 个、api 14 个）。

### B-03：MinIO 分片上传与合并

- 已实现接口：
  - `POST /api/v1/upload/chunk`
  - `POST /api/v1/upload/merge`
- 已实现能力：
  - 分片写入 MinIO 临时对象
  - 分片记录写入 `kb_chunk_uploads`
  - 分片 MD5 校验
  - merge 后写入正式对象
  - 创建 `kb_documents`，状态置为 `PROCESSING`
  - 发送 Kafka 消息到 `file-processing`
- 已新增：
  - `KnowledgeUploadService`
  - `FileProcessingMessage`
  - upload / merge 请求响应 DTO
- 已补充接口集成测试：
  - `ChunkUploadIntegrationTest`

### B-04：Kafka 消费者 — 解析与分块

- 已新增：
  - `FileProcessingConsumer`
  - `FileProcessingService`
- 已实现能力：
  - 消费 `file-processing`
  - 从 MinIO 读取正式对象
  - 使用 Tika 解析文本
  - 文本切块写入 `kb_chunks`
  - 成功后将 `kb_documents.status` 更新为 `READY`
  - 失败后写入 `error_message` 并置 `FAILED`
- 已将分块逻辑收敛到 `FileProcessingService#splitText(...)`，供阶段 A 简化上传与阶段 B 正式消费链路复用。
- 已补充测试：
  - `FileProcessingConsumerIntegrationTest`
    - READY 路径
    - FAILED 路径

### B-05：DashScope Embedding 与 ES 写入

- 已新增配置：
  - `yanban.knowledge.embedding.*`
  - `yanban.knowledge.elasticsearch.*`
- 已新增：
  - `KnowledgeEmbeddingProperties`
  - `KnowledgeElasticsearchProperties`
  - `EmbeddingClient`
  - `DashScopeEmbeddingClient`
  - `KnowledgeIndexService`
  - `ElasticsearchKnowledgeIndexService`
  - `IndexedChunkDocument`
  - `VectorizationService`
- 已完成链路接入：
  - Kafka 文件解析成功后，先写 `kb_chunks`
  - 再调用 DashScope embedding
  - 再写入 Elasticsearch
  - ES 返回 `_id` 后回写 `kb_chunks.es_doc_id`
- 已加入维度校验：embedding 维度必须与 `YANBAN_ES_VECTOR_DIMS` 一致，否则直接失败。
- 已新增测试：
  - `DashScopeEmbeddingClientTest`
  - `VectorizationServiceTest`
- 已补充并通过整体验证：
  - `mvn -q -pl yanban-knowledge,yanban-api -am test`
- 已顺手降低测试噪音：为主要 API SpringBootTest 增加 `spring.kafka.listener.auto-startup=false`，避免多数测试场景自动连本机 Kafka。

### B-06：混合检索与权限

- 已新增：
  - `KnowledgeSearchIndexClient`
  - `ElasticsearchKnowledgeSearchIndexClient`
  - `KnowledgeSearchIndexHit`
  - `HybridKnowledgeSearchService`
- 已完成正式检索切换：
  - `HybridKnowledgeSearchService` 作为主 `KnowledgeSearchService`
  - 检索时先对 query 生成 embedding
  - 再走 Elasticsearch 检索
  - 返回结果后追加词面命中加权，形成当前阶段的混合排序
- 权限规则已接入 ES 查询过滤：
  - 当前用户自己的私有文档
  - 所有公开文档
  - 默认不返回他人私有文档
- 已保留数据库 fallback：
  - 当 embedding / ES 检索失败时，自动回退到原 SQL LIKE 方案
  - `SimpleKnowledgeSearchService` 当前作为 fallback 实现保留
- 已补测试：
  - `HybridKnowledgeSearchServiceTest`
  - `KnowledgeControllerIntegrationTest` 已补 mock，继续覆盖私有/公开可见性
- 已执行通过：
  - `mvn -q -pl yanban-knowledge,yanban-api -am test`

### 文档修订记录（implementation.md）

- 已补充 `B-07` 的隐含前置说明：
  - 知识库管理页开始前，后端应先补齐 `GET /api/v1/kb/documents` 与 `DELETE /api/v1/kb/documents/{documentId}` 等支撑接口。
  - 明确这些接口虽然服务前端页，但实现时机应早于或并行于 B-07。
- 修订原因：原步骤中直接要求“列表 / 删除 / 状态展示”，但未显式写出其后端前置补齐时机，容易造成实施歧义。

### B-07 前置后端补齐：知识库列表 / 删除 API

- 已新增：
  - `KnowledgeDocumentService`
  - `KbDocumentListItemResponse`
- 已补接口：
  - `GET /api/v1/kb/documents`
  - `DELETE /api/v1/kb/documents/{documentId}`
- 当前列表接口返回：
  - `id`
  - `userId`
  - `filename`
  - `status`
  - `isPublic`
  - `mimeType`
  - `fileSize`
  - `errorMessage`
  - `createdAt`
  - `updatedAt`
- 当前删除策略：
  - 仅允许 owner 删除
  - 删除 `kb_chunks`
  - 调用 ES `_delete_by_query` 按 `documentId` 清理索引文档
  - 若存在 `objectKey`，删除 MinIO 原始对象
  - 最后删除 `kb_documents`
- 已补测试：
  - `KnowledgeControllerIntegrationTest`
    - 列表仅返回当前用户文档
    - 删除后列表不再可见
- 已执行通过：
  - `mvn -q -pl yanban-api -am -Dtest=KnowledgeControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -q -pl yanban-knowledge,yanban-api -am test`

### B-07：Vue 知识库管理页（进行中）

- 已新增前端 API：
  - `frontend/src/api/knowledge.ts`
- 已新增页面：
  - `frontend/src/views/KnowledgeBasePage.vue`
- 已完成前端接线：
  - 新增路由 `/knowledge-base`
  - 顶部导航已加入“知识库”入口
- 当前页面能力：
  - 选择文件
  - 按 `1 MB` 分片顺序上传
  - 分片失败自动重试 3 次
  - merge 后自动刷新列表
  - 列表展示 `status / isPublic / mimeType / fileSize / errorMessage / updatedAt`
  - 当存在 `PROCESSING / UPLOADING` 文档时每 3 秒轮询状态
  - 支持删除文档
- 已更新样式：
  - `frontend/src/styles.css` 已补知识库页样式
- 说明：
  - 由于当前环境缺少 WSL Node / pnpm，尚未在本轮完成浏览器侧构建运行验证；需后续在 Windows/IDE 环境手动确认页面交互。

### B-08：Vue 检索调试页

- 已新增页面：
  - `frontend/src/views/KnowledgeSearchDebugPage.vue`
- 已扩展前端 API：
  - `frontend/src/api/knowledge.ts` 新增 `searchKnowledge(...)`
- 已完成前端接线：
  - 新增路由 `/knowledge-base/search-debug`
  - 顶部导航已加入“检索调试”入口
  - 知识库列表页右上角已增加跳转按钮
- 当前页面能力：
  - 输入 `query`
  - 输入 `topK`
  - 调用 `POST /api/v1/search`
  - 展示 `filename / documentId / chunkIndex / score / isPublic / chunkText`
  - 提供“填入示例 / 清空结果”辅助操作
- 说明：
  - 由于当前环境缺少 WSL Node / pnpm，尚未在本轮完成浏览器侧构建运行验证；需后续在 Windows/IDE 环境手动确认页面交互。

### B-09：`yanban-paper` 模块 — 领域与存储

- 已新增配置：
  - `yanban-paper/src/main/java/com/yanban/paper/config/PaperStorageProperties.java`
  - `yanban-paper/src/main/java/com/yanban/paper/config/PaperStorageConfig.java`
- 已新增服务：
  - `PaperStorageService`
  - `PaperTaskService`
- 已新增 Web 层：
  - `PaperController`
  - `PaperProcessRequest`
  - `PaperTaskResponse`
- 已完成接口：
  - `POST /api/v1/paper/process`
- 当前行为：
  - 仅允许上传 `.docx`
  - 原始文件优先写 MinIO，若无 MinIO Bean 则回退到本地目录
  - 创建 `paper_tasks` 记录
  - 初始状态写为 `PENDING`
  - `current_stage=UPLOAD_RECEIVED`
- 已更新配置：
  - `yanban-api/src/main/resources/application-dev.yml` 新增 `yanban.paper.storage.*`
- 已补测试：
  - `yanban-api/src/test/java/com/yanban/api/paper/PaperControllerIntegrationTest.java`
  - 覆盖：
    - 上传 `sample.docx` 后创建 `PENDING` task
    - 非法扩展名返回 `400`
- 已执行通过：
  - `mvn -q -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -q -pl yanban-paper,yanban-api -am test`

### B-10：PaperOrchestrator 全流程（进行中）

- 已新增：
  - `PaperAsyncConfig`
  - `PaperSseEvent`
  - `PaperEventStreamService`
  - `PaperOrchestrator`
- 已补任务相关接口：
  - `GET /api/v1/paper/tasks/{taskId}`
  - `GET /api/v1/paper/events?taskId=`
  - `POST /api/v1/paper/tasks/{taskId}/pause`
  - `POST /api/v1/paper/tasks/{taskId}/resume`
  - `POST /api/v1/paper/tasks/{taskId}/stop`
  - `GET /api/v1/paper/tasks/{taskId}/download`
- 已新增迁移：
  - `V6__add_final_object_key_to_paper_tasks.sql`
- 当前最小编排骨架已具备：
  - 异步执行
  - 任务状态推进：`PENDING -> RUNNING -> COMPLETED/FAILED/STOPPED`
  - `paper_task_rounds` 写入
  - SSE 历史回放与订阅推送
  - pause / resume / stop 控制
  - 下载链路可用（当前下载的是“结果文件占位版”）
- 当前已接入的事件类型：
  - `log`
  - `summary_ready`
  - `sections`
  - `outer_round`
  - `section_loop_start`
  - `section_attempt`
  - `section_polished`
  - `section_review_done`
  - `paper_review_done`
  - `review`
  - `references_ready`
  - `complete`
  - `paused`
  - `error`
- 已补测试：
  - `PaperControllerIntegrationTest` 现已覆盖：
    - 上传 docx
    - task 创建
    - round 生成
    - task 查询
    - 下载接口 content-type
    - 非法扩展名拒绝
- 已执行通过：
  - `mvn -q -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -q -pl yanban-paper,yanban-api -am test`
- 当前未完成项：
  - 真实论文处理逻辑仍未接入
  - OpenAlex 文献推荐仍为占位
  - 结果 docx 当前为下载链路验证版本，不代表最终润色结果

### 文档修订记录（implementation.md）

- 已补充 B-10 当前状态说明：
  - 当前仅完成可演示骨架版异步编排与 SSE / 下载链路
  - 真实 Summary / 分章 / Abstract / OpenAlex 仍待后续优化推进
  - 允许先以前端论文页（B-11）继续联调

### B-11：Vue 论文三步页（进行中）

- 已新增前端 API：
  - `frontend/src/api/paper.ts`
- 已新增页面：
  - `frontend/src/views/PaperPage.vue`
- 已完成前端接线：
  - 新增路由 `/paper`
  - 顶部导航已加入“论文修改”入口
- 当前页面能力：
  - 步骤 1：上传 docx + 配置 `targetLanguage / scoreThreshold / maxRounds / innerMaxAttempts / literatureCount`
  - 步骤 2：通过 fetch + Authorization header 订阅 SSE，并展示事件流
  - 步骤 3：下载结果文件
  - 支持从路由 query `taskId` 打开已有任务
  - 支持 pause / resume / stop
- 本轮已补：
  - 下载改为 `axios blob` 方案，解决新窗口下载不携带 Bearer token 的问题
  - 页面增加 SSE 连接状态展示
  - 收到 `complete / error / paused` 后主动关闭当前 SSE 连接
  - 切换 `taskId` 时自动重载任务并重连
  - 提交后清空已选文件
- 当前注意事项：
  - 当前 WSL 缺少 Node / pnpm，尚未在本轮完成浏览器侧运行验证。

### B-12：对话跳转论文页

- 已新增后端意图服务：
  - `yanban-api/src/main/java/com/yanban/api/agent/PaperRevisionIntentService.java`
- 已完成后端接线：
  - `AgentService` 支持在 REST 对话接口中短路返回论文页跳转
  - `ChatWebSocketHandler` 支持在 WS 对话路径中短路返回论文页跳转
  - `SendMessageResponse` 新增 `navigationUrl`
  - `WsChatEvent` 新增 `navigationUrl`
- 当前意图关键词覆盖：
  - `润色论文`
  - `修改论文`
  - `帮我润色`
  - `帮我修改`
  - `论文润色`
  - `论文修改`
  - 以及部分英文短语
- 当前产品行为：
  - 当用户在 `/chat` 中表达论文润色意图时，后端不继续走常规模型回答
  - 直接返回带 `/paper` 深链的助手消息
  - 前端聊天页展示“打开论文修改页”按钮
- 已完成前端接线：
  - `frontend/src/views/ChatPage.vue` 已支持从消息内容 / WS done 事件提取 `navigationUrl`
  - 已补对应按钮样式
- 已补测试：
  - `AgentControllerIntegrationTest` 新增论文跳转意图覆盖
  - `ChatWebSocketHandlerTest` 已适配新的 handler 构造参数
- 已执行：
  - `mvn -q -pl yanban-api -am -Dtest=AgentControllerIntegrationTest,ChatWebSocketHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 当前说明：
  - 暂未创建占位 `paper_task_id`；真实论文任务仍在用户进入 `/paper` 后上传 docx 时创建。

### B-13：GLM ModelProvider

- 已新增核心类：
  - `yanban-core/src/main/java/com/yanban/core/model/GlmProperties.java`
  - `yanban-core/src/main/java/com/yanban/core/model/GlmModelProvider.java`
  - `yanban-core/src/main/java/com/yanban/core/model/RoutingChatModelProvider.java`
- 已扩展模型路由：
  - `ChatRequest` 新增 `provider`、`apiKey`
  - `HarnessRequest` 新增 `provider`、`apiKey`
  - `HarnessEngine` 已改为透传 provider/apiKey 到统一模型门面
- 已更新配置：
  - `application-dev.yml` 新增 `yanban.model.glm.*`
- 已新增迁移：
  - `V7__extend_sys_user_settings_for_glm.sql`
- 已扩展用户设置：
  - `SysUserSettings` 增加 `glmApiKeyEncrypted`、`glmModel`
  - `UserSettingsRequest` / `UserSettingsResponse` 增加 GLM 字段
  - `UserSettingsService` 已支持 `defaultProvider=glm`、GLM 密钥加解密、GLM 默认模型
- 已扩展运行时行为：
  - `AgentService.createSession(...)` 现在会按 provider 选择默认模型快照
  - `AgentService` / `ChatWebSocketHandler` 会按 provider 选择对应用户密钥
- 已补测试/验证：
  - 新增 `GlmModelProviderTest`
  - 已修正 `DeepSeekModelProviderTest`、`HarnessEngineTest`、`ChatWebSocketHandlerTest`、`AgentControllerIntegrationTest`、`UserSettingsControllerIntegrationTest` 以适配多 provider 结构
  - 已执行：
    - `mvn -q -pl yanban-core,yanban-api -am -Dtest=GlmModelProviderTest,DeepSeekModelProviderTest,UserSettingsControllerIntegrationTest,AgentControllerIntegrationTest,ChatWebSocketHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 当前未完成项：
  - 前端设置页尚未暴露 GLM 相关字段（按计划在 B-14 完成）

### B-14：Vue 设置页扩展（GLM + MCP + Skills 列表）

- 已新增后端迁移：
  - `V8__extend_sys_user_settings_for_mcp_and_skills.sql`
- 已扩展用户设置后端：
  - `SysUserSettings` 新增：
    - `githubPatEncrypted`
    - `filesystemRootsText`
    - `disabledSkillsJson`
  - `UserSettingsRequest` / `UserSettingsResponse` 新增：
    - `githubPat`
    - `githubPatConfigured`
    - `filesystemRoots`
    - `disabledSkills`
  - `UserSettingsService` 已支持：
    - GitHub PAT 加解密
    - filesystem roots JSON 序列化
    - disabled skills JSON 序列化
- 已新增 Skills 列表 API（阶段性 stub）：
  - `GET /api/v1/skills`
  - 新增：
    - `SkillsController`
    - `SkillsService`
    - `SkillListItemResponse`
  - 当前会扫描：
    - `skills/builtin/`
    - `skills/user/`
- 已完成前端设置页改造：
  - `frontend/src/api/settings.ts`
  - `frontend/src/api/skills.ts`
  - `frontend/src/views/SettingsPage.vue`
- 当前页面能力：
  - 切换默认 provider（DeepSeek / GLM）
  - 配置 DeepSeek / GLM API Key 与模型
  - 配置 GitHub PAT
  - 配置 filesystem 允许根目录
  - 查看 Skills 列表并启用 / 禁用
  - 保留 max_steps 与 RAG 默认开关
- 已执行验证：
  - `mvn -q -pl yanban-api -am -DskipTests compile`
  - `mvn -q -pl yanban-api -am -Dtest=UserSettingsControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 当前说明：
  - 前端设置页尚未在浏览器实际运行验证（当前 WSL 缺少 Node / pnpm）
  - Skills 列表 API 当前为轻量 stub，后续可在 B-20 再增强

### B-15：`yanban-mcp` — stdio Client 基础

- 已新增 `yanban-mcp` 核心类：
  - `McpClientException`
  - `McpToolDescriptor`
  - `McpServerProcessConfig`
  - `McpStdioClient`
  - `DefaultMcpStdioClient`
  - `McpTransport`
  - `ProcessMcpTransport`
  - `ContentLengthMcpFraming`
- 已完成基础能力：
  - 启动子进程
  - Content-Length framing 编解码
  - `initialize`
  - `tools/list`
  - `tools/call`
  - 关闭
- 已实现命令白名单校验：
  - 子进程首个命令不在 `allowedCommands` 中时直接拒绝
- 已补测试：
  - `ContentLengthMcpFramingTest`
  - `DefaultMcpStdioClientTest`

### B-16：连接 GitHub MCP Server

- 已新增 API 侧 MCP 配置：
  - `McpProperties`
  - `McpConfig`
  - `McpClientFactory`
  - `McpServerKind`
- 已接入 GitHub MCP discovery / 调用骨架：
  - 配置前缀：`yanban.mcp.github.*`
  - 工具前缀：`mcp_github__`
  - 启动时通过 `McpToolRegistryCustomizer` 尝试 discovery
  - 调用时从当前用户设置解密 GitHub PAT 并注入 `GITHUB_TOKEN`
- 已更新：
  - `application-dev.yml`
  - `.env.example`
  - `docs/SETUP.md`

### B-17：连接 filesystem MCP + 路径白名单

- 已接入 filesystem MCP discovery / 调用骨架：
  - 配置前缀：`yanban.mcp.filesystem.*`
  - 工具前缀：`mcp_fs__`
- 已新增：
  - `FilesystemPathGuard`
  - `McpProxyToolExecutor`
  - `McpToolRegistryCustomizer`
- 已实现路径白名单：
  - 校验字段：`path / paths / directory / root`
  - 路径先规范化再判断是否落在当前用户 `filesystemRoots` 中
  - 非法路径直接拒绝调用
- 已补测试：
  - `FilesystemPathGuardTest`（4 例）
- 已执行验证：
  - `mvn -q -pl yanban-mcp,yanban-api -am -Dtest=DefaultMcpStdioClientTest,ContentLengthMcpFramingTest,FilesystemPathGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -q -pl yanban-mcp,yanban-api -am -DskipTests compile`
- 当前说明：
  - 由于本地缺少可直接联调的 Node MCP Server 环境，GitHub / filesystem MCP 仍未记录真实手动调用结果。
  - 但代码、配置、白名单校验与单元测试骨架已补齐。

### B-18：`yanban-skills` — 加载与注入

- 已新增 `yanban-skills` 模块核心类：
  - `SkillDefinition`
  - `SkillLoader`
  - `SkillRegistry`
- 已完成：
  - 扫描 `skills/builtin/` 与 `skills/user/`
  - 读取 `SKILL.md`
  - 轻量解析 `skill.yaml` 中的 `name / description / allowed_tools`
  - `SkillRegistry.refresh()`
- 已补测试：
  - `SkillLoaderTest`
  - `HarnessSkillFilterTest`
- 已扩展 Harness：
  - `HarnessRequest` 新增 `skillPrompt` / `allowedToolNames`
  - `HarnessEngine` 支持 Skill prompt 注入与工具白名单过滤

### B-19：内置 Skill `code-review` 与对话集成

- 已新增内置 Skill 文件：
  - `skills/builtin/code-review/SKILL.md`
  - `skills/builtin/code-review/skill.yaml`
- 已完成后端接线：
  - `SkillsService.resolveEnabledSkill(...)`
  - `AgentService.sendMessage(...)` 支持 skillId
  - `ChatWebSocketHandler` 在携带 skillId 时改走 Harness 路径
- 已完成前端接线：
  - `ChatPage.vue` 新增 Skill 下拉
  - WS 请求现在会带 `skillId`
- 当前说明：
  - Skill 模式下当前响应为“单次 chunk + done”，不是完整 tool-event 级流式编排
  - 但已可用于 code-review 这类需要 prompt 注入与工具白名单的场景骨架

### B-20：Skills 列表 API 与启用/禁用

- 已新增 / 调整接口：
  - `GET /api/v1/skills`
  - `PUT /api/v1/skills/{id}/enabled`
  - `POST /api/v1/skills/refresh`
- 已新增：
  - `SkillEnabledRequest`
  - `ResolvedSkill`
- `GET /api/v1/skills` 当前返回：
  - `id`
  - `name`
  - `description`
  - `builtin`
  - `enabled`
  - `path`
- 已补测试：
  - `SkillsControllerIntegrationTest`
- 已执行验证：
  - `mvn -q -pl yanban-skills,yanban-core,yanban-api -am -Dtest=SkillLoaderTest,HarnessSkillFilterTest,SkillsControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 需要说明：
  - 实施计划里写的是“禁用后 WS 请求带该 skillId 返回 400”，但在当前 WebSocket 技术语义下，握手完成后无法返回 REST 风格 400；当前实现改为返回 `error` 事件。这一偏差已记录到架构文档。
  - `code-review` 依赖 filesystem MCP 的真实效果仍需后续具备 Node MCP Server 环境后补手动联调。

### B-21：OCR 可插拔接口（最小实现）

- 已新增：
  - `KnowledgeOcrProperties`
  - `OcrProvider`
  - `HttpOcrProvider`
- 已改造：
  - `KnowledgeStorageConfig`
  - `FileProcessingService`
- 当前行为：
  - `mimeType` 为 `image/*` 时走 OCR 分支
  - 若 OCR 未配置，文档标记 `FAILED` 且错误提示为 `OCR 未配置`
- 已补测试：
  - `FileProcessingConsumerIntegrationTest` 新增图片走 OCR 分支覆盖

### B-22：`yanban-cli` — login、chat、config

- 已新增 `yanban-cli` 基础实现：
  - `CliConfigStore`
  - `CliApiClient`
  - `YanbanCli`
- 已支持命令：
  - `yanban login`
  - `yanban chat`
  - `yanban config list`
  - `yanban config set`
- 当前行为：
  - `login` 交互式输入用户名密码，并写入 `~/.yanban-agent/config.properties`
  - `chat` 通过后端 WebSocket 打印流式 chunk
  - `config set` 当前最小支持：
    - `max-steps`
    - `default-provider`

### B-23：`yanban-cli` — kb 与 paper 子命令

- 已继续扩展 CLI 命令：
  - `yanban kb list`
  - `yanban kb upload <file>`
  - `yanban paper status <taskId>`
- 当前行为：
  - `kb list` 查询与 Web 共用同一知识库列表 API
  - `kb upload` 当前走 `simple-upload` 单文件接口
  - `paper status` 输出 `status / currentStage`，并将 `currentStage` 作为 recent log 的简化替代
- 已执行验证：
  - `mvn -q -pl yanban-knowledge,yanban-skills,yanban-cli,yanban-api -am -DskipTests compile`
  - `mvn -q -pl yanban-knowledge,yanban-skills,yanban-api -am -Dtest=FileProcessingConsumerIntegrationTest,SkillLoaderTest,HarnessSkillFilterTest,SkillsControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 当前说明：
  - CLI 尚未补独立自动化测试，只完成了编译级验证与依赖后端的相关测试。
  - `paper status` 的“最近日志一行”目前用 `currentStage` 近似表达，还没有单独的日志查询接口。
  - 当前 `YanbanCli` 已是可运行的 Java 主类，但**还没有补齐终端命令化交付**：
    - 还不能直接在 Windows 终端中用 `yanban login` 这种全局命令方式运行
    - 目前更接近“Java 主类 / IDEA 调试入口”，缺少 `yanban.bat` / 可执行脚本 / 安装到 PATH 的分发形态
  - 因此 B-23 的“CLI 命令已实现”目前更准确地说是：
    - **命令逻辑已实现**
    - **终端命令入口尚未补完整**

### B-24：集成测试与 CI 友好

- 已在父 POM `private-helper-agent/pom.xml` 增加 Surefire 标签策略：
  - 默认 `excludedGroups=manual`
  - `mvn test -Dgroups=manual` 时通过 profile 清空默认排除
- 当前默认测试策略明确为：
  - H2（`MODE=MySQL`）
  - Mock 外部依赖 / 本地替身
  - 不依赖真实 DeepSeek / DashScope / GitHub / MCP 外网服务
- 已新增共享测试配置：
  - `yanban-api/src/test/resources/application.properties`
  - 默认关闭 `spring.kafka.listener.auto-startup=false`
  - 减少默认测试对本地 Kafka 的噪声连接
- 当前已确认的核心覆盖：
  - Harness 多轮：`HarnessEngineTest`
  - RAG 开关：`HarnessEngineTest`
  - Skill 白名单：`HarnessSkillFilterTest`
  - JWT：`AuthControllerIntegrationTest`
  - KB 权限检索：`KnowledgeControllerIntegrationTest`
- 已在 `README.md` / `docs/SETUP.md` 明确说明：
  - 默认测试不使用 Testcontainers
  - 选择 H2 + Mock 作为 CI 友好方案
- 已执行验证：
  - `mvn -q clean test`
  - 结果：当前默认 profile 全绿（已通过 surefire 报告复核）
  - `mvn -q test -Dgroups=manual` 已验证可执行（当前尚无真实外网 manual 测试类）
- 当前说明：
  - 尽管默认测试已全绿，真实外网型 manual 测试（如真实 MCP / 真实模型密钥）仍需后续补充测试类与手测记录。

### B-25：文档定稿与开源准备

- 已更新 `README.md`：
  - 阶段 B 能力总览
  - 全 Docker 启动
  - GLM / MCP 配置
  - Skills 目录说明
  - CLI 命令
  - 测试与 manual tag 说明
  - 开源卫生说明
- 已更新 `docs/SETUP.md`：
  - ES / Kafka / MinIO
  - Node 版本
  - GitHub PAT / MCP
  - OCR 配置
  - manual 测试命令
- 已新增 `private-helper-agent/LICENSE`：Apache-2.0
- 已执行开源卫生扫描（排除 `node_modules/dist/target`）：
  - 未发现项目源码中的真实密钥命中
  - 命中项为文档示例值与测试伪数据
  - 工作区可见本地 `target/` 产物，但属于 `.gitignore` 范围
- 当前说明：
  - 由于当前 WSL 视图不可见 `.git` 元数据，本轮无法完成最终 git-index 级别的“已提交内容”卫生核验，只能完成工作区文件级检查。

## 阶段 B 门禁（本轮核验）

### G-B1 构建

- 状态：✅ 通过
- 依据：
  - 已执行 `mvn -q clean test`
  - surefire 报告复核全部 `Failures: 0, Errors: 0`

### G-B2 知识库（Web 分片上传 PDF → READY → 检索命中）

- 状态：🟡 部分通过
- 已确认：
  - 后端分片上传与异步处理链路已有自动化覆盖：`ChunkUploadIntegrationTest`、`FileProcessingConsumerIntegrationTest`
  - 检索与权限已有覆盖：`KnowledgeControllerIntegrationTest`
- 未完成：
  - 当前未补一轮真实前端页面手测记录（受当前 WSL 前端运行条件限制）
  - 当前记录中也未专门补一轮“PDF 文件 + Web 页面”闭环截图式验证

### G-B3 对话 + RAG

- 状态：❌ 当前待修
- 已确认：
  - `HarnessEngineTest` 已覆盖默认 RAG / 禁用 RAG 分支
  - `ChatWebSocketHandlerTest` 已覆盖 WS 基本消息链路
  - 知识库上传与检索调试页真实手测已成功，说明底层 KB 检索链路可用
- 已复现问题：
  - 前端 `/chat` 页在默认模式下提问知识库已有内容时，回答仍为“不知道”或声称无法访问知识库
- 根因判断：
  - 当前普通 WebSocket 聊天路径未真正接入 Harness/RAG 注入链路
  - 检索调试成功 ≠ `/chat` 普通 WS 已完成 RAG 接线
- 未完成：
  - 需要修复 `ChatWebSocketHandler` 普通对话路径，使其真正复用 RAG 能力

### G-B4 论文

- 状态：🟡 部分通过
- 已确认：
  - `PaperControllerIntegrationTest` 通过
  - 论文任务查询 / 下载 / 控制 API 已存在
- 未完成：
  - 尚未补一轮 Web 三步页 `complete + 下载 docx` 的新手测记录
  - 当前 B-10 仍是 skeleton-first 实现，最终 docx 为占位链路产物

### G-B5 GLM

- 状态：🟡 部分通过
- 已确认：
  - `GlmModelProviderTest` 通过
  - 设置页与后端 provider 路由已接好
- 未完成：
  - 缺少真实 GLM key 下的手动对话记录

### G-B6 MCP

- 状态：🟡 部分通过
- 已确认：
  - GitHub / filesystem MCP 代码接线完成
  - `FilesystemPathGuardTest`、`DefaultMcpStdioClientTest`、`ContentLengthMcpFramingTest` 通过
- 未完成：
  - 真实 Node MCP Server 联调记录仍缺

### G-B7 Skill

- 状态：🟡 部分通过
- 已确认：
  - `SkillLoaderTest`、`HarnessSkillFilterTest`、`SkillsControllerIntegrationTest` 通过
  - `code-review` 内置 Skill、启用/禁用、加载/刷新均已落地
  - 已通过真实手测确认 filesystem MCP 可被 `code-review` Skill 成功调用
- 已复现问题：
  - 当前 `code-review` 对话会把 Skill prompt / 处理性说明直接展示在聊天消息中
  - 大模型读取到的部分代码片段也会直接回显在最终对话内容里
  - 当前中间处理过程暴露过多，交互体验不理想
- 未完成：
  - 需要优化 Skill / MCP 结果在聊天页中的展示方式，减少中间提示词与原始代码片段泄露
  - GitHub MCP 与更完整的 Skill 手测记录仍待补充

### G-B8 CLI

- 状态：❌ 当前待补
- 已确认：
  - CLI 已完成编译验证
  - `yanban login/chat/config/kb/paper status` 命令逻辑已实现
  - `com.yanban.cli.YanbanCli` 可作为 Java 主类在 IDEA 中运行
- 已复现问题：
  - 当前还不能像真正 CLI 一样在终端直接执行 `yanban login`
  - 缺少脚本化命令入口（如 `yanban.bat` / PATH 分发）
- 未完成：
  - 需要补齐终端命令化交付方式
  - 需要再做一轮真实 CLI 对接运行中后端的手测记录

### G-B9 开源卫生

- 状态：✅ 基本通过
- 已确认：
  - `LICENSE` 已新增（Apache-2.0）
  - 无 `.env` 文件命中
  - 排除 `node_modules/dist/target` 后未发现常见真实密钥模式
- 说明：
  - 工作区中本地 `target/` 与 `frontend/node_modules/` 存在，但属于忽略目录

### G-B10 遗留隔离

- 状态：🟡 受环境限制
- 已确认：
  - 实施过程中代码变更均在 `private-helper-agent/`
- 未完成：
  - 由于当前 WSL 视图缺少 `.git` 元数据，无法在此环境完成最终 git-diff 级隔离核验

## 2026-06-14：C阶段执行结果

### C-01：修复 `/chat` 普通 WebSocket 路径未接 RAG

- 状态：✅ 已完成代码修复
- 本次处理：
  - 将 `ChatWebSocketHandler` 的普通聊天路径统一改为走 `AgentService.sendMessage(...)`
  - 不再区分“普通流式 provider 路径”与“Skill/Harness 路径”
  - 第一版接受牺牲 token 级流式，优先保证普通聊天也能真正复用 Harness/RAG 链路
- 影响：
  - `/chat` 普通对话现在与 Skill 模式一样，统一通过 Harness 生成回答
  - 纸面上已消除“检索调试页可命中，但聊天页不吃 RAG”的结构性分叉
- 已更新测试：
  - `ChatWebSocketHandlerTest`
- 已执行验证：
  - `mvn -q -pl yanban-api -am -Dtest=ChatWebSocketHandlerTest,AgentControllerIntegrationTest,HarnessEngineTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 当前说明：
  - 自动化验证已通过
  - 仍需你在前端 `/chat` 页做一轮真实手测，确认知识库命中问题已消失
  - 在后续手测中又发现：即使勾选“本次不使用知识库”，模型仍可能通过 `search_knowledge` 工具主动扫描知识库
  - 已追加修复：当 `ragDisabled=true` 时，从 Harness 可见工具集中移除 `search_knowledge`，避免自动 RAG 关闭后仍被模型主动调用知识库工具
  - 已补测试：`HarnessRagToolDisableTest`

### C-01A：将 `search_knowledge` 改为通过执行上下文隐式获取当前用户

- 状态：✅ 已完成代码修复
- 本次处理：
  - 从 `search_knowledge` 工具 schema 中移除了显式 `userId`
  - 工具执行时改为通过 `ToolExecutionContext` 获取当前用户 ID
  - 若缺少用户上下文，工具直接失败，不再使用不明确的默认值
- 修改文件：
  - `yanban-knowledge/src/main/java/com/yanban/knowledge/tool/SearchKnowledgeToolExecutor.java`
  - `yanban-knowledge/src/test/java/com/yanban/knowledge/tool/SearchKnowledgeToolExecutorTest.java`
- 已执行验证：
  - `mvn -q -pl yanban-knowledge,yanban-core,yanban-api -am -Dtest=SearchKnowledgeToolExecutorTest,HarnessEngineTest,ChatWebSocketHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 当前说明：
  - 代码与测试已完成
  - 仍需你在真实聊天页验证：模型不再向用户索要 `userId`

### C-02A：排查并修复 MCP / Skill 的结构化工具调用兼容性

- 状态：🟡 继续处理中
- 已完成的排查：
  - 在 `HarnessEngine` 中补充诊断日志
  - 诊断结果显示：当前问题并不是 Skill 未选中，而是 filesystem MCP 工具根本没有成功注册
- 已确认根因：
  - 后端启动日志存在：`Skip MCP tool registration for FILESYSTEM: 启动 MCP 子进程失败`
  - 因此模型即使知道正确工具名，也拿不到真实工具定义，只能输出伪 `<tool_call>` 文本
- 已追加修复：
  - 将 MCP 默认命令改为 Windows 兼容形式：`cmd,/c,npx,...`
  - 同步放宽白名单：`cmd,npx,node`
- 修改文件：
  - `yanban-core/src/main/java/com/yanban/core/harness/HarnessEngine.java`
  - `yanban-api/src/main/resources/application-dev.yml`
  - `.env.example`
  - `docs/SETUP.md`
- 已执行验证：
  - `mvn -q -pl yanban-core,yanban-api -am -Dtest=HarnessEngineTest,ChatWebSocketHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 当前说明：
  - 你需要重启后端，再观察启动日志中是否还出现 `Skip MCP tool registration for FILESYSTEM`
  - 若不再出现，再继续复测 `code-review` 场景

### C-02：优化 Skill / MCP 中间处理内容在聊天页的展示

- 状态：✅ 已完成第一轮前端展示优化
- 本次处理：
  - 前端聊天页不再把 `system` / `tool` 历史消息误显示为普通“你”的消息气泡
  - 新增“显示中间过程”开关，默认隐藏中间处理消息
  - `system` / `tool` 消息改为折叠卡片展示
  - 助手消息中的代码块改为独立代码块样式，减少原始大段文本直接糊在正文中
- 本次范围：
  - 只改前端展示结构，不改后端事件分层
- 修改文件：
  - `frontend/src/views/ChatPage.vue`
  - `frontend/src/styles.css`
- 当前说明：
  - 这是第一轮收敛，重点是减少中间过程的直接暴露感
  - 同时已修正 `code-review` Skill 提示词：明确要求模型只能调用真实 MCP 工具名，禁止输出伪 `<tool_call>` 文本与伪协议
  - 若后续仍觉得中间过程污染过重，再进入后端事件分层方案评估

### C-07：前端视觉细化第二批（论文 / 知识库 / 设置）

- 状态：✅ 已完成代码改造
- 本次处理：
  - `PaperPage.vue` 改为论文三步工作流视觉：顶部说明区、步骤条、上传参数卡、实时进度卡、结果下载卡。
  - `KnowledgeBasePage.vue` 改为文档工作台视觉：顶部统计区、上传 dropzone、文档卡片列表、状态标签与处理状态概览。
  - `KnowledgeSearchDebugPage.vue` 改为检索调试工作台视觉：顶部说明区、独立查询面板、结果卡片列表与排名标识。
  - `SettingsPage.vue` 改为设置中心视觉：模型设置、Agent 设置、MCP 设置、Skills 分区卡片与底部保存栏。
  - `styles.css` 补充统一的工作台 hero、卡片、上传区、文档卡、搜索结果、论文步骤、设置页等样式。
- 本次范围：
  - 仅做视觉结构与样式调整，不改动知识库 / 论文 / 设置业务接口与功能逻辑。
- 额外修复：
  - 修正 `ChatPage.vue` 中 Skill 下拉对 `builtin` 字段的过期引用，改为使用当前前端类型中的 `source === 'builtin'`，否则前端构建会失败。
- 已执行验证：
  - `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过。
  - 构建存在 Vite chunk size warning，但不影响本次视觉改造验证。
- 当前说明：
  - 已完成编译级验证；仍建议在浏览器中手动检查浅色 / 深色主题切换、页面层级与主要操作按钮位置。

### C-08：前端视觉细化第三批（认证页 / 收尾）

- 状态：✅ 已完成代码改造
- 本次处理：
  - `LoginPage.vue` 改为 AI 产品式登录页：品牌区、产品说明、轻量表单卡片、大尺寸输入框与主按钮。
  - `RegisterPage.vue` 改为同风格注册页：品牌区、产品定位说明、注册表单卡片。
  - `styles.css` 补充 `auth-shell`、`auth-panel`、`auth-brand-block`、`auth-logo` 等认证页样式，并兼容移动端单列布局。
- 本次范围：
  - 不引入新依赖，不改认证逻辑，不改路由守卫。
- 已执行验证：
  - 与 C-07 一并执行 `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过。
- 当前说明：
  - 登录 / 注册页已与主应用工作台视觉语言保持一致；仍建议浏览器手动确认登录、注册、跳转流程。

### E-01：LaTeX 解析与文档模型（L0 / PARSE）

- 状态：✅ 已完成第一版代码地基
- 本次处理：
  - 新增 Flyway `V9__create_latex_paper_tables.sql`：扩展 `paper_tasks`（input_format/mode/main_entry），新增 `literature_cards`、`paper_sections`、`paper_task_analysis`、`paper_task_artifacts`、`paper_task_clarifications`、`paper_task_literature`、`suggestions`、`suggestion_evidence`。
  - 扩展 `PaperTask` 实体，新增 `PaperSection` / `PaperSectionRepository`。
  - 新增 `LatexParserService` 与 L0 文档模型 record：`LatexDocument`、`LatexSection`、`LatexProtectedSpan`、`LatexFloat`、`LatexCitationUsage`、`LatexCrossReference`、`LatexBibEntry`、`LatexLintIssue`。
  - 支持单文件 `.tex` 解析：preamble 元数据（title/authors/keywords）、sections、cite/ref/label/includegraphics、figure/table、数学与受保护环境、外部 `.bib` 与内联 `thebibliography`。
  - 硬 lint 第一版：悬空 cite、断 ref、begin/end 不配平、花括号不配平、重复 bib key。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，5 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；Flyway V1~V9 在 H2 MySQL mode 下可迁移。
- 当前说明：
  - E-01 暂不接前端/API；按约定先完成后端服务与自动测试地基。
  - 第一版解析器是务实分词器，未实现完整 LaTeX 语法；多文件精确写回、真编译、equations[] 一等实体仍后置。

### E-02：角色识别 + 结构确认检查点（L0 / ROLE_RECOGNITION + STRUCTURE_CHECK）

- 状态：✅ 已完成第一版后端地基
- 本次处理：
  - 新增 `LatexRoleRecognitionService`：基于 E-01 文档模型做启发式角色识别，输出 `RecognizedSectionRole`、`RoleRecognitionResult`、`missingRoles`。
  - 新增结构歧义检测：当无显式 Related Work 章节但 Introduction 中存在引用密集/相关工作线索时，生成 `RELATED_WORK_PLACEMENT` 阻塞型确认问题，默认 `KEEP_IN_INTRO`。
  - 新增 `PaperTaskClarification` / `PaperTaskClarificationRepository` / `PaperClarificationService`：可创建 pending clarification，阻塞型问题将任务置为 `WAITING_INPUT` + `STRUCTURE_CHECK`，回答后若无 pending 则恢复 `RUNNING`。
  - 新增 SSE 事件：`clarification_needed`、`clarification_resolved`。
  - 新增 `PaperSectionService` 与用户改角色 API：支持列出任务章节、按枚举校验后把章节角色改为用户指定值，并标记 `role_source=user`、`role_confidence=1.0`。
  - `PaperController` 新增章节与 clarification 相关端点。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，7 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 自动装配、新 repository 扫描、Flyway V1~V9 均通过。
- 当前说明：
  - 真实 LLM 角色复核 prompt 仍在 E-03 接入；E-02 先完成启发式 + 用户确认/改角色 + WAITING_INPUT 持久化地基。
  - 当前 orchestration 还未把 PARSE→ROLE→STRUCTURE_CHECK 串入真实 pipeline，后续步骤继续接入。

### E-03：Prompt 资源体系化

- 状态：✅ 已完成第一版 prompt 资源与渲染服务
- 本次处理：
  - 新增 `yanban-paper/src/main/resources/prompts/` 资源目录。
  - 新增 9 个 prompt 文件：`role-confirm`、`research-profile`、`section-polish`、`section-review`、`literature-extract`、`gap-analysis`、`relatedwork-gen`、`contribution-gen`、`abstract`。
  - Prompt 内容已覆盖公共守则：限定角色枚举、不编实验/数据/引用、只从候选文献选 evidence、占位符保留、polish/review 分离、Suggestion JSON、L3c 文献抽取 JSON。
  - 新增 `PaperPromptTemplate` 与 `PaperPromptService`：从 classpath `prompts/*.md` 加载模板，提取 `{{变量}}` 必填集合，渲染时缺变量快速报错，支持简单 Iterable 渲染。
  - 新增 `PaperPromptServiceTest` 覆盖：预期模板加载、变量替换、缺必填变量报错、未知 prompt 报错。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，11 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 资源加载与自动装配未受影响。
- 当前说明：
  - E-03 只完成 prompt 资源化和模板渲染，不在本步接真实 LLM 调用。
  - 后续 E-04 可复用 `research-profile` prompt 生成结构化研究画像。

### E-04：结构化研究画像（L3 种子 / PROFILE）

- 状态：✅ 已完成第一版服务地基
- 本次处理：
  - 新增 `PaperTaskAnalysis` / `PaperTaskAnalysisRepository`，对应 `paper_task_analysis` 表，持久化 `research_profile_json`。
  - 新增 `ResearchProfileResult`，字段覆盖 problem/method/contributions/datasets/baselines/metrics/tasks/keywords，并带 `degraded` 与 `rawText`。
  - 新增 `PaperResearchProfileService`：使用 `research-profile` prompt，基于 `LatexDocument.sections` 生成 sectionSummaries，调用模型生成研究画像，解析 JSON 并写入 `paper_task_analysis`。
  - JSON 解析失败时不阻断任务，返回 degraded 结果并保留 rawText。
  - 为保持 `yanban-paper` 模块解耦，新增 `PaperModelClient` 轻量接口；在 `yanban-api` 中新增 `PaperModelClientConfig`，用现有 `ChatModelProvider` 适配。
  - 更新 repository 测试覆盖 `paper_task_analysis`，新增 `PaperResearchProfileServiceTest` 覆盖有效 JSON、非 JSON 降级、生成并保存画像。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，14 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 适配器装配正常。
- 当前说明：
  - E-04 已具备 PROFILE 阶段的服务地基，但尚未接入真实编排阶段链。
  - 后续 E-05 将基于研究画像进入文献检索与卡片地基。

### E-05：文献检索与卡片地基（L3 / RETRIEVE）

- 状态：✅ 已完成第一版服务地基
- 本次处理：
  - 新增 `LiteratureCard` / `LiteratureCardRepository` 与 `PaperTaskLiterature` / `PaperTaskLiteratureRepository`，对应全局文献卡片与任务-文献关联。
  - 新增 `LiteratureSource` 抽象与第一版 OpenAlex、arXiv provider，OpenAlex 解析 work JSON 与 inverted abstract，arXiv 解析 Atom XML。
  - 新增 `LiteratureService`：从 `ResearchProfileResult` 生成多 query，跨 provider 检索，按 DOI / arXiv id / OpenAlex id / S2 id / title hash 去重，写入 `literature_cards` 与 `paper_task_literature`。
  - 完成简版相关性排序：关键词、任务、problem/method 命中、引用量、年份轻权重；第一版 narrative_role 分为 advocacy / critique。
  - 完成简版 L3c 卡片分析：无 LLM 时基于摘要规则写 `analysis_json`，标记 `rule-based-l3c-v1`，后续可升级为 `literature-extract` prompt。
  - 完成概念阶梯第一版：将选中文献按 advocacy / critique 写入 `paper_task_analysis.concept_ladder_json`。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，18 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 上下文与 Flyway V1~V9 均通过。
- 当前说明：
  - E-05 暂不接 Elasticsearch / embedding，保持 `yanban-paper` 独立；语义相似度与 MMR 深化、真实 LLM L3c、RETRIEVE SSE checkpoint 将在编排接入时继续增强。
  - 目前服务确保“推荐均来自真实 provider 返回候选”，不允许模型凭空补引用。

### E-06：gap 分析与建议生成（L4 / GAP_ANALYSIS）

- 状态：✅ 已完成第一版服务地基
- 本次处理：
  - 新增 `Suggestion` / `SuggestionRepository` 与 `SuggestionEvidence` / `SuggestionEvidenceRepository` / `SuggestionEvidenceId`，对应 `suggestions` 与 `suggestion_evidence` 表。
  - 新增 `GapSuggestionResult` 与 `PaperGapAnalysisService`：读取任务、研究画像、已选文献卡片，渲染 `gap-analysis` prompt，调用模型生成建议 JSON。
  - 实现 Suggestion JSON 解析与落库：track/category/severity/statement/evidence/applicable/patch。
  - 实现 grounding 校验：evidence 只允许引用当前任务已选真实文献卡片；模型引用不存在卡片会被丢弃。
  - 实现诚实闸门第一版：无真实 evidence 的 ADVOCACY 自动转 CRITIQUE，applicable=false，patch 清空，避免无支撑内容进入 LaTeX 补丁。
  - 将生成结果摘要写入 `paper_task_analysis.gap_matrix_json`；补齐实体 JSON 字段长度，避免测试 DDL 255 长度与 Flyway LONGTEXT 意图不一致。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，20 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 上下文、Flyway V1~V9 与新增 repository 扫描均通过。
- 当前说明：
  - E-06 暂未接真实编排 SSE 与 LaTeX patch 应用；当前重点是确保建议和 evidence 持久化、grounding 校验、防幻觉闸门可测试。
  - 后续 E-07 可在此基础上做占位保护润色与 patch 组装。

### E-07：占位保护分章润色（L1/L2 / POLISH）

- 状态：✅ 已完成第一版服务地基
- 本次处理：
  - 新增 `LatexMaskingService` 与 `MaskedLatexText`：保护 cite/ref/label/includegraphics、数学片段与重点环境，生成 `[[YANBAN_*_0001]]` 占位符，支持 unmask 与占位符集合校验。
  - 新增静态 lint 第一版：捕获花括号不配平、环境 begin/end 不配平、unmask 后残留占位符。
  - 新增 `SectionPolishResult` 与 `PaperSectionPolishService`：逐章 mask → `section-polish` prompt → 占位符校验 → unmask → lint → `section-review` prompt。
  - 实现 retry 策略：掉占位或 lint blocker 会拒绝当次输出并带原因重试；review 低分在未达最大轮次时带审查意见重试；仍失败则保留原章节。
  - 对 References 角色默认跳过润色；成功或失败结果写回 `paper_sections.polish_status/review_json/diff_json`，并记录内存型 object key 占位。
  - 补齐 `PaperSection.reviewJson/diffJson` 长度，保持测试 DDL 与 Flyway LONGTEXT 设计一致。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，25 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 上下文与 Flyway V1~V9 均通过。
- 当前说明：
  - E-07 暂未接真实编排 SSE、MinIO 正式对象存储、超长章节切块和 Abstract 专用 prompt；当前先完成可测试的占位保护、review 回环和章节状态落库地基。
  - 后续 E-08 可基于 `paper_sections` 的 polish/review/diff 状态进入三件套组装。

### E-08：三件套产出（ASSEMBLE）

- 状态：✅ 已完成第一版服务地基
- 本次处理：
  - 新增 `PaperTaskArtifact` / `PaperTaskArtifactRepository`，对应 `paper_task_artifacts` 表，支持按 task/type/version 查询产物。
  - 扩展 `PaperStorageService#storeArtifact`，支持保存 `.tex`、`.bib`、Markdown report 等任意论文产物。
  - 新增 `PaperAssembleResult` 与 `PaperAssembleService`：从 `LatexDocument`、`paper_sections`、`suggestions`、`suggestion_evidence`、`literature_cards` 组装三件套。
  - 基础版：产出 `suggested_bib` 与 `review_report`，不改原文。
  - 进阶版：额外产出 `polished_tex`，第一版按章节顺序拼接可用润色文本/原文，并写入 `paper_tasks.final_object_key`。
  - `suggested.bib` 只从真实 evidence 文献卡片生成，自动避让原 bib key；审查报告按章节状态、Suggestion 与 evidence 输出，并附 AI 自查免责声明。
  - 进阶版增加静态校验第一版：复用 LaTeX lint，并标记原 bib 缺失的 cite key。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，27 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 上下文、Flyway V1~V9 与新增 repository 扫描均通过。
- 当前说明：
  - E-08 仍未接真实前端下载链路与用户逐条采纳 patch；进阶版 patch 精确改写、多文件写回、真编译仍后置。
  - 当前已形成可落库、可存储、可测试的三件套组装地基。

### E-09：论文质量样例集与评价记录

- 状态：✅ 已完成离线验收资产与自动化检查
- 本次处理：
  - 新增中文 LaTeX 样例 `zh-rag-polish`：包含 `main.tex` + `refs.bib`，覆盖中文章节、cite/ref/figure/math、RAG 论文常见表达。
  - 新增英文 LaTeX 样例 `en-literature-gap`：包含 `main.tex` + `refs.bib`，覆盖 Abstract/Introduction/Method/Discussion/Conclusion、equation/ref/math 与 grounded writing 主题。
  - 新增无 `.bib` 内联 bibliography 样例 `inline-bibliography`：覆盖 legacy `thebibliography` / `bibitem` 解析场景。
  - 新增 `memory-bank/paper-quality-evaluation.md`：记录中文与英文样例的原文、三件套结果记录方式、人工评价维度、推荐文献真实可溯源核对要求。
  - 新增 `private-helper-agent/docs/paper-quality-samples/README.md`：说明样例位置、验收顺序与手动端到端记录要求。
  - 更新 `memory-bank/test-checklist.md`：增加阶段 E 论文质量专项测试清单。
  - 新增 `PaperQualitySampleTest`：自动校验中文、英文、内联 bibliography 三类样例可解析且无 BLOCKER lint。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 上下文与 Flyway V1~V9 均通过。
- 当前说明：
  - E-09 已建立可重复对比的离线样例与评价记录模板；真实前端/模型端到端 artifact object key、模型 provider、检索源和人工评分仍需在后续阶段 E 门禁/阶段 F 手测时补充。

### 阶段 E 门禁核验

- 状态：✅ 后端/离线自动化门禁已通过，真实端到端手测项后续补充
- 本次处理：
  - 在 `memory-bank/test-checklist.md` 增加阶段 E 门禁核验表，对 G-E1~G-E7 逐项记录证据。
  - G-E1~G-E6 均已有自动化测试覆盖；G-E7 三件套服务地基通过自动化，VSCode/latexmk 真编译作为后续手测项保留。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

### F-01：阶段进度增强

- 状态：✅ 已完成第一版前后端展示增强
- 本次处理：
  - 扩展 `PaperSseEvent` 可选进度字段：`currentSection/totalSections/sectionTitle/attempt/maxAttempts/progressPercent`，旧事件仍兼容。
  - 当前骨架编排 `PaperOrchestrator` 发布关键事件时补充进度百分比、章节编号与尝试次数。
  - 论文页新增阶段链展示：解析、结构确认、画像、文献、Gap、润色、组装、完成。
  - 论文页新增整体进度条、章节进度、尝试次数展示。
  - 论文页将 SSE 事件类型和阶段映射为友好中文文案。
  - 论文页保留折叠的原始 SSE JSON 日志，方便调试。
  - 更新 `architecture.md` 记录 SSE 事件结构扩展与前端展示策略。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `pnpm build`（frontend）：通过；仅 Vite chunk size warning。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。
- 当前说明：
  - 当前进度字段先接入既有骨架编排；后续真实 LaTeX 流水线接入编排时，可直接复用同一 SSE schema 上报真实章节总数、当前章节和尝试次数。

### F-02：结构确认检查点交互 UI

- 状态：✅ 已完成前端第一版
- 本次处理：
  - 在 `frontend/src/api/paper.ts` 新增 clarification 与 section API：查询确认问题、提交答案、查询章节、更新章节角色。
  - 论文页接入 `clarification_needed` / `clarification_resolved` 事件后刷新确认问题与章节角色。
  - 新增“结构确认”面板：批量展示问题、选项、阻塞/提示标识，默认选中“保持原样”或后端 `defaultOption`。
  - 支持“全部保持原样”批量提交；支持单项提交；非阻塞问题支持跳过。
  - 新增“章节角色”面板：展示章节 title、role、confidence/source，并允许下拉修改角色。
  - 更新 `test-checklist.md`，补充 F-02 手测步骤。
- 已执行验证：
  - `pnpm build`（frontend）：通过；仅 Vite chunk size warning。
- 当前说明：
  - F-02 当前完成 UI/API 接入；真实歧义任务端到端触发、批量确认后续跑与角色修改体验仍需浏览器手测。

### F-03：在线预览 + 逐条采纳

- 状态：✅ 已完成第一版预览与采纳状态闭环
- 本次处理：
  - 新增 `PaperSuggestionResponse`、`PaperSuggestionStatusUpdateRequest`、`PaperArtifactResponse`。
  - 新增 `PaperPreviewService`：按任务读取 suggestions、计算 evidence 数量、生成 A/B 诚实分级、更新 suggestion 状态、读取 artifact 元数据。
  - `PaperController` 新增接口：`GET /suggestions`、`POST /suggestions/{id}/status`、`GET /artifacts`。
  - 扩展 `PaperSectionResponse` 返回 `reviewJson/diffJson`，用于章节源码级 JSON 预览。
  - 前端论文页新增“在线预览与逐条采纳”面板：基础版/进阶版切换、章节 diff/review 预览、A/B 分级展示、A 类建议勾选采纳、拒绝建议、artifact 版本摘要。
  - 更新 `architecture.md` 与 `test-checklist.md`，记录预览/采纳接口与手测项。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `pnpm build`（frontend）：通过；仅 Vite chunk size warning。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。
- 当前说明：
  - 当前先完成预览与采纳状态闭环；“按已采纳 patch 重新组装改后 tex”的强触发按钮仍待后续增强，E-08 assemble 服务地基已具备。

### F-04：审查报告 + suggested.bib 展示

- 状态：✅ 已完成第一版展示
- 本次处理：
  - 扩展 `PaperSuggestionResponse`，携带真实 evidence card 详情：title/authors/year/venue/DOI/arXiv/OpenAlex/S2/URL/PDF/citationCount。
  - `PaperPreviewService` 从 `suggestion_evidence` 与 `literature_cards` 聚合 evidence cards，确保展示来源仍来自真实文献卡片。
  - 论文页新增“审查报告与 suggested.bib”面板：展示 severity、category、track、statement、真实 evidence 链接。
  - 若建议无 evidence，明确提示“禁止直接写入论文”。
  - 展示推荐文献列表：标题、作者、年份、venue、DOI/URL/PDF/OpenAlex 等可回溯信息，并保留免责声明。
  - 更新 `architecture.md` 与 `test-checklist.md`。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `pnpm build`（frontend）：通过；仅 Vite chunk size warning。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。
- 当前说明：
  - 当前展示的是由 evidence cards 支撑的推荐文献列表；suggested.bib 原文内容预览/复制按钮可在后续增强。

### F-04.5：上传入口纠偏为 LaTeX-only

- 状态：✅ 已完成前后端纠偏
- 本次处理：
  - 确认论文模块第二期需求为 LaTeX-only，前端仍显示 `.docx` 上传属于早期遗留，不符合当前方向。
  - 后端 `PaperProcessRequest` 新增 `mainTex` 与可选 `bibFile`，短期保留旧 `file` 字段兼容。
  - 后端创建任务优先校验并上传 `.tex` 主文件，可选校验并上传 `.bib` 文件。
  - 任务元数据设置为 `inputFormat=LATEX`，并按是否上传 bib 设置 `mode=LATEX_ONLY/LATEX_BIB`。
  - 将源 `.tex` / `.bib` 登记为 `source_tex` / `source_bib` artifact，便于后续预览与产物追踪。
  - 前端论文页上传区改为 `.tex` 主文件必填、`.bib` 可选，提交字段改为 `mainTex` / `bibFile`。
  - 更新论文控制器集成测试，覆盖 LaTeX 上传与非法扩展名校验。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `pnpm build`（frontend，通过 `cmd.exe /c pnpm build`）：通过；仅 Vite chunk size warning。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。
- 当前说明：
  - 前端已不再要求上传 `.docx`；浏览器手测时应使用 `.tex` 主文件，可选上传 `.bib`。
  - 旧 `file` 字段仅作为短期兼容，不作为当前 UI 入口。

### F-05-缺陷 1：任务秒完成但未真实处理

- 状态：✅ 已修复第一版真实 LaTeX 编排入口
- 问题确认：
  - 用户上传 `.tex/.bib` 后约 5 秒完成，实际是 `PaperOrchestrator` 仍在运行早期最小骨架流程。
  - 旧流程中存在 `original-docx`、`summary draft`、`openalex placeholder` 等模拟内容，未真正读取并解析 LaTeX。
- 本次处理：
  - 将 `PaperOrchestrator` 的模拟 docx/摘要/章节流程替换为真实 LaTeX 入口。
  - 任务启动后真实读取 `.tex` 源文件与已登记的 `source_bib` artifact。
  - 调用 `LatexParserService` 解析文档，识别章节、引用、bibliography 与 lint 信息。
  - 调用 `LatexRoleRecognitionService` 识别章节角色。
  - 将章节落库到 `paper_sections`，并为每个原始章节保存 `section_original` artifact。
  - 如检测到阻塞类结构确认问题，任务进入 `WAITING_INPUT`，发布 `clarification_needed`，不再假完成。
  - 无阻塞确认项时，调用 `PaperAssembleService` 生成基础版产物：`suggested_bib` 与 `review_report`。
  - 修复存储服务：MinIO 写入后保留本地备份；MinIO 读取失败或 mock 返回空时回退本地文件，避免测试/本地开发读不到源文件。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `pnpm build`（frontend，通过 `cmd.exe /c pnpm build`）：通过；仅 Vite chunk size warning。
  - `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。
- 当前说明：
  - 这一步先修复“假流程秒完成”的关键缺陷，接入真实解析、章节识别、结构确认与基础组装。
  - 研究画像、真实文献检索、Gap 分析、分章模型润色等长流程尚未全量串入异步编排，后续继续作为 F-05 缺陷收敛项推进。

### F-05-缺陷 2：结构确认后状态 RUNNING 但不继续推进

- 状态：✅ 已修复后端自动续跑
- 问题确认：
  - 阻塞类结构确认触发后，原异步编排线程会正常结束并等待用户输入。
  - 用户回答完所有确认项后，原逻辑只把任务状态从 `WAITING_INPUT` 改回 `RUNNING`，但没有重新启动编排线程。
  - 因此前端显示 `RUNNING`，实际没有后续处理。
- 本次处理：
  - `PaperClarificationService` 在最后一个 pending clarification 被回答后，事务提交后自动调用 `PaperOrchestrator#startTask(taskId)` 续跑。
  - `PaperOrchestrator` 在续跑时读取已有 clarification：
    - 若仍有 `PENDING`，继续保持/进入 `WAITING_INPUT`。
    - 若已有确认项且均已回答，则跳过重复创建 clarification，继续进入后续组装流程。
  - 续跑过程中发布 `clarification_resolved` / “结构确认已完成，继续处理”进度事件。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。
- 当前说明：
  - 本次只修复方案 A：全部结构确认完成后自动续跑。
  - 页面布局暂未修改。

### F-05-缺陷 3：结构确认续跑时 paper_task_rounds 唯一键冲突

- 状态：✅ 已修复
- 问题确认：
  - 结构确认完成后自动续跑会重新进入 `PARSE` 阶段。
  - 编排再次插入 `taskId + roundNumber=1 + stage=PARSE` 的 round，触发唯一索引 `uk_paper_task_rounds_task_round` 冲突。
- 本次处理：
  - `PaperTaskRoundRepository` 增加 `findByTaskIdAndRoundNumberAndStage`。
  - `PaperTaskRound` 增加状态、输入、输出、备注 setter。
  - `PaperOrchestrator#persistRound` 从直接插入改为 upsert：若同一任务/轮次/阶段已存在，则更新内容；不存在才创建。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

### F-05-缺陷 4：第二阶段完成后结果文件不可下载

- 状态：✅ 已修复
- 问题确认：
  - 论文页“下载结果文件”仍依赖 `paper_tasks.final_object_key`。
  - 第二阶段当前基础版组装主要生成 `suggested_bib` / `review_report` artifacts，未必生成 `final_object_key`。
  - 因此前端显示“结果文件：尚未生成”，下载按钮禁用。
- 本次处理：
  - 后端 `downloadResult` 支持无 `final_object_key` 但已有结果 artifacts 时，将 `polished_tex` / `suggested_bib` / `review_report` 打包为 zip 下载。
  - 后端下载接口根据产物类型返回正确文件名与 content-type：zip 或 tex。
  - 前端下载按钮启用条件从只看 `finalObjectKey` 扩展为：有 `finalObjectKey` 或已有可下载 artifacts。
  - 前端结果文件文案在 artifacts 存在时显示“已生成 N 个产物，可下载 zip”。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `pnpm build`：通过，仅 Vite chunk size warning。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

### F-05-缺陷 5：生成产物为空壳，未串联真实检索/Gap/润色

- 状态：✅ 已修复第一版串联
- 问题确认：
  - 用户下载 zip 后只有 `review-report.md` 和空 `suggested.bib`。
  - 报告中所有章节为 `PENDING`，建议为 `No suggestions generated`。
  - 原因是编排只执行 LaTeX 解析、章节识别、结构确认和基础组装，尚未串入研究画像、文献检索、Gap 分析和分章润色。
- 本次处理：
  - `PaperOrchestrator` 主链路新增阶段：`PROFILE`、`RETRIEVE`、`GAP_ANALYSIS`、`POLISH`、高级 `ASSEMBLE`。
  - 研究画像抽取失败或模型返回空 JSON 时，使用标题与章节标题生成降级画像，避免无 query。
  - 文献检索单源失败时不打断任务，继续其他源。
  - Gap 分析模型无建议但已有真实检索卡片时，生成保守兜底建议，并绑定真实 evidence card，避免空报告。
  - 分章润色结果改为真实写入 artifact 存储，不再使用 `memory://` 占位，保证高级组装可读取润色文本。
  - 完整流程组装改为 advanced mode，产出 `polished_tex`、`suggested_bib`、`review_report`。
  - 下载策略改为 artifacts 优先打包 zip；即使存在 `finalObjectKey`，也会优先下载包含三件套的 zip。
  - 前端结果文件文案同步为 artifacts 优先，显示“已生成 N 个产物，可下载 zip”。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
  - `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
  - `pnpm build`：通过，仅 Vite chunk size warning。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

### F-05-缺陷 6：章节角色识别未接 LLM 复核，References 误判

- 状态：✅ 已修复第一版
- 问题确认：
  - 设计文档要求“启发式 → LLM 复核 → 用户可改”的三级角色识别。
  - 当前实现实际只使用 heuristic，`role-confirm.md` prompt 未被主链路调用。
  - `LatexParserService#guessRole` 使用 `contains("reference")`，导致 `Reference Beampattern` 被误判为 `REFERENCES`。
- 本次处理：
  - `LatexParserService` 将 References 判断改为严格标题匹配：`References` / `Reference` / `Bibliography` / `参考文献` 等，避免正文标题含 reference 被误判。
  - `LatexRoleRecognitionService` 接入 `role-confirm` prompt：可用模型时执行 LLM 章节角色复核，输出限定在枚举内；模型不可用/解析失败时降级 heuristic。
  - 增加 References 安全闸门：即使 LLM 返回 `REFERENCES`，也必须通过严格标题匹配，否则不采纳。
  - `LiteratureService` 写入检索诊断：queries、sourceAttempts、sourceFailures、rawCandidateCount、uniqueCandidateCount、selectedCount。
  - `PaperAssembleService` 在 `review-report.md` 增加 `Retrieval Diagnostics`，让 `suggested.bib` 为空时能定位是无 query、检索源失败、候选为 0 还是 selected 为 0。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，31 tests。
  - `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

## 规划说明

- 执行计划统一维护到 `memory-bank/implementation.md`。
- 第二期 LaTeX 方向权威设计纪要见：`memory-bank/discussion_about_fix_paper_20260615.md`。
- 前端视觉细化方案统一见：`memory-bank/design.md`。
- 手动验收与已测结果统一见：`memory-bank/test-checklist.md`。

## 下一步

- 进入 F-05：阶段 F 手测记录与缺陷收敛。
- 继续按 `implementation.md` 第二期 F 阶段推进。

### F-05-缺陷 7：真实产物质量收敛（重复 `\\end{document}` 与文献推荐偏少）

- 状态：✅ 已修复第一版
- 问题确认：
  - 用户真实产物中 `polished.tex` 末尾出现两个 `\\end{document}`，属于编译级错误。
  - `suggested.bib` 虽已非空，但仅 1 条且偏工具引用；检索 selected 候选未能在 Gap 建议较少时进入推荐产物。
  - 检索 query 仍主要由代码规则从画像拼接，覆盖面不足。
- 本次处理：
  - 新增 `literature-search-query.md` prompt 与 `LiteratureQueryPlanner`，优先让 LLM 基于研究画像生成 8–12 条英文学术检索 query，模型不可用时降级规则 query。
  - `LiteratureService` 改为使用 query planner，并把论文标题、目标语言、画像一并作为 query 规划上下文。
  - `PaperAssembleService` 在拼接章节前剥离章节文本尾部 `\\end{document}`，最终只追加一次文档结束标记。
  - `suggested.bib` 在 Gap evidence 不足时补入当前任务 selected 的高相关真实检索候选，最多补足到 5 条；报告中新增 `Supplemental Bibliography Candidates` 并标记为弱推荐、需人工核验。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，31 tests。
  - `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

### F-05-缺陷 8：真实产物复核发现 front matter 丢失、结构命令被模型改写、弱相关 bib 进入推荐

- 状态：✅ 已修复代码，需用户重跑任务验证新产物
- 真实产物复核结论：
  - `IEEE_TAES_regular_template_latex_v6-artifacts (4)/polished.tex` 已无重复 `\\end{document}`。
  - 但 `polished.tex` 丢失了 `\\title`、作者、`abstract`、`IEEEkeywords` 等 front matter。
  - 模型改写了 `\\label{}` / `\\ref{}` 名称，导致部分引用目标变化，并出现 `Fig.~\\ref{fig:ablation}` 指向注释掉的 label 的风险。
  - `suggested.bib` 中强 evidence 的 2 篇文献较合理，但 supplemental 弱推荐混入了 CP2K、6G、O-RAN 等低相关条目。
- 本次处理：
  - `LatexDocument` 新增 `frontMatter`，`LatexParserService` 提取 `\\begin{document}` 到第一个章节之间的标题、作者、摘要、关键词等内容。
  - `PaperAssembleService` 组装时保留 front matter，避免最终 `polished.tex` 丢标题/摘要/关键词。
  - `PaperSectionPolishService` 增加结构命令不变量校验：`cite/ref/label/includegraphics/bibliography` 的有序集合必须保持一致；否则拒绝模型输出并保留原文。
  - `PaperAssembleService` 增加 `REF_WITHOUT_LABEL` lint，检测最终 tex 中未定义 label 的 ref。
  - supplemental bib 兜底新增最低相关性阈值，避免低分弱相关候选进入 `suggested.bib` 和报告补充候选区。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，32 tests。
  - `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

### F-05-UI：论文页右侧结果区视觉重构

- 状态：✅ 已完成第一版
- 本次处理：
  - 将论文页右侧“结果下载”重构为“结果中心”。
  - 顶部增加结果下载状态条，集中展示可下载状态、结果文件、原始文件与下载按钮。
  - 将原本纵向堆叠的结构确认、章节角色、在线预览、审查报告拆分为 Tabs：总览 / 结构 / 章节 / 预览 / 报告。
  - 每个 Tab 内容区设置固定高度与内部滚动，避免章节很多时右侧变成很长一列。
  - 右侧列宽从 `l:6` 调整为 `l:7`，左侧上传区从 `l:8` 调整为 `l:7`，提升结果区可读性。
  - 修正文案：上传步骤由“选择 docx 与参数”改为“选择 tex / bib 与参数”。
- 已执行验证：
  - `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅 Vite chunk size warning。

### F-05-检索诊断产物：完整展示 raw/unique/selected 文献

- 状态：✅ 已完成第一版
- 本次处理：
  - `LiteratureService` 在每次文献检索后新增两个诊断产物：
    - `retrieved-literature.json`：完整 JSON，包含 queries、sourceAttempts、rawCandidates、uniqueCandidates、rankedCandidates、selectedCandidates。
    - `retrieved-literature.md`：适合人工阅读的 Markdown 摘要，列出 query、selected candidates、ranked unique candidates 与 source attempts。
  - rawCandidates 不去重，用于评估 OpenAlex/arXiv 原始返回质量；unique/ranked/selected 仍保留，用于观察去重和排序效果。
  - 下载 zip 增加 `retrieved-literature.json` 与 `retrieved-literature.md`，不影响正式 `suggested.bib` 推荐逻辑。
  - 前端结果中心文案同步显示 `retrieved-literature` 诊断文件数量。
- 已执行验证：
  - `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，32 tests。
  - `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
  - `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。
  - `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅 Vite chunk size warning。

### F-05-缺陷 9：首次上传后任务直接 FAILED，第二次才正常启动

- 状态：✅ 已完成首轮修复
- 用户现象：第一次上传 `.tex` 与 `.bib` 后点击开始处理，任务很快显示 `FAILED`；第二次重新上传同样文件后才正常执行。
- 分析判断：该问题更像异步编排首启时序/存储读取瞬时失败，而非文件格式问题；首次任务启动后如果源文件或 bib 在本地/MinIO 读取出现瞬时失败，会直接进入 `FAILED`，第二次因连接/存储已热身而成功。
- 本次修复：
  - `PaperOrchestrator#startTask` 增加任务级运行去重，避免同一 task 被结构确认恢复、SSE/页面操作等重复触发并发启动。
  - 主 `.tex` 读取改为 3 次重试，避免上传后本地备份/MinIO 回退存在瞬时不可读时直接失败。
  - `.bib` 读取改为 3 次重试；若仍失败，不再让整篇任务 `FAILED`，而是发布 `bib_read_skipped` 事件并按无 bib 模式继续解析。
  - 失败日志保留完整堆栈，错误信息会更明确指向 `source_tex` / `source_bib` 读取问题。
- 已执行验证：
  - `mvn -pl yanban-paper test`：通过，32 tests。
  - `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
  - `mvn test`：通过，28 tests。

### F-05-文献推荐质量链路：Introduction slots、数量范围、去重与泛化过滤

- 状态：🟡 已完成主要链路修复，剩余质量优化问题已确认
- 背景：围绕 `IEEE_TAES_regular_template_latex_v6-artifacts (12) ~ (20)` 连续测试，目标是让 LaTeX 论文“仅文献推荐”模式基于 Introduction citation slots 生成稳定、数量可控、可诊断、可去重的推荐列表。
- 已完成修复：
  - 文献数量不再固定 8：任务持久化 `literatureCount`，并新增前端/后端 `literatureMinCount` 与 `literatureCount` 范围控制。
  - Introduction Analysis 改为多次 API 调用：分别生成 `introductionPlan`、`citationSlots`、`citationAudit`，避免单个超长 JSON 被截断。
  - `review-report.md` 增加 `Introduction Analysis Diagnostics`，可直接查看 LLM call mode、slot count、fallback reason、raw preview 等。
  - `retrieved-literature.json/md` 展示 raw candidates、unique/ranked candidates、selected candidates，便于区分未去重/检索内去重/最终选择。
  - `suggested.bib` 与 `suggested-novel.bib` 分离：前者保留推荐全集，后者排除上传 `.bib` 中已有文献。
  - `.bib` 字段解析支持嵌套花括号，`Uploaded bibliography entries parsed` 已能正确显示 35，已有文献去重开始生效。
  - LLM slot 数量与 query 质量明显改善：`artifacts (20)` 中 Introduction Analysis 正常 `degraded=false`，`Raw LLM slot count=14`，`Fallback-added slot count=0`。
  - 最终选择加入 category balancing 与弱泛化过滤，避免 FDA-MIMO jamming 单一方向占满全部推荐。
- 当前测试结论（`artifacts (20)`）：
  - Introduction Analysis：✅ 达标，LLM 多调用成功，无 fallback。
  - Citation slots / queries：✅ 基本达标，能覆盖 FDA-MIMO、polarimetric、constant-modulus、optimization、learning 与 gap。
  - 数量范围：✅ 基本符合预期，`Selected candidates=27`，未强行用低相关文献补满 30。
  - 上传 `.bib` 去重：✅ 初步达标，解析 35 条已有 bib，识别 4 条 already-present，`suggested-novel.bib` 输出 22 条。
  - 推荐质量：🟡 可用但仍需精修，仍有少量 DFRC/通信、OTFS、泛 MIMO/泛波形类弱相关文献进入 `suggested-novel.bib`。
- 已确认的后续问题：
  - 泛化/弱相关文献仍未完全压到理想比例，例如 DFRC waveform、MIMO OTFS、Compact Decomposition、泛 MIMO-STAP/空间多样性背景项。
  - selected 内部仍可能出现多版本/近重复，例如 arXiv 与正式出版版本、标题近似但 DOI/OpenAlex 不同的候选。
  - 最终报告尚未逐篇标注“对应哪个 citation slot / supportStrength / useAs”，人工审查成本仍偏高。
- 后续建议：
  - 增加最终 LLM relevance filter，对 `selectedCandidates` 做二次筛选/降级，输出 `CORE/BACKGROUND/WEAK_REJECTED`。
  - 在 `retrieved-literature.md` 和 `review-report.md` 中按 citation slot 分组统计推荐数量，并列出每篇对应 slot。
  - 加强多版本近重复去重：title similarity + DOI/arXiv/published-version 合并 + 作者年份辅助。
