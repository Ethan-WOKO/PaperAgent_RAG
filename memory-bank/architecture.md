# 研伴 Agent — 架构补充记录

> 文档作用概述：记录**实现后的架构现状、关键设计取舍、已知技术偏差与模块职责**。当代码实现与最初设计相比出现结构性差异时，更新本文件；不要在这里维护逐步执行计划。
>
> 更新时间：2026-06-14

## 1. 当前工程结构

新实现目录固定为 `private-helper-agent/`，与 `memory-bank/` 平级。遗留项目 `PaiSmart-main/`、`paper-agent/` 保持只读。

```text
private-helper-agent/
├── pom.xml
├── docs/
│   ├── SETUP.md
│   ├── API-smoke.md
│   └── docker-compose.yml
├── yanban-core/
├── yanban-knowledge/
├── yanban-paper/
├── yanban-mcp/
├── yanban-skills/
├── yanban-api/
├── yanban-cli/
├── frontend/
└── skills/
    ├── builtin/code-review/
    └── user/
```

## 2. Maven 模块关系

当前已建立 Maven 聚合父工程：`com.yanban:yanban-parent:0.1.0-SNAPSHOT`。

- 父工程使用 Spring Boot `3.4.2` 作为 parent。
- Java 版本锁定为 17。
- 当前 `yanban-api` 依赖 `yanban-core`。
- 其余模块先保留最小 POM，后续按实施计划逐步引入依赖。

## 3. API 模块当前职责

`yanban-api` 目前承担：

- Spring Boot 启动入口。
- Actuator health endpoint。
- Flyway 数据库迁移。
- `sys_users` 用户表、JPA Entity、Repository。
- `agent_sessions`、`agent_messages`、`agent_tool_runs` 迁移脚本。
- 注册、登录、刷新 JWT、当前用户接口。
- Spring Security 无状态 JWT 过滤链。

`yanban-core` 目前承担：

- Agent 会话、消息、ToolRun 的 JPA Entity。
- Agent 会话、消息、ToolRun 的 Repository。
- 模型 Provider 抽象：`ChatModelProvider`、`ChatRequest`、`ChatResponse`、`ChatChunk`。
- DeepSeek Provider：非流式 `chat` 与流式 `streamChat`。
- Tool 抽象与注册表：`ToolDefinition`、`ToolCall`、`ToolResult`、`ToolExecutor`、`ToolRegistry`。
- Harness 最小循环：`HarnessEngine`、`HarnessRequest`、`HarnessResult`。
- 阶段 A 知识库：`KbDocument`、`KbChunk`、`KnowledgeIngestionService`、`KnowledgeSearchService`、`SearchKnowledgeToolExecutor`。

当前 API：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/register` | 注册用户，返回 JWT |
| POST | `/api/v1/auth/login` | 登录，返回 JWT |
| POST | `/api/v1/auth/refresh` | 使用 refresh token 换新 token |
| GET | `/api/v1/users/me` | 查询当前用户 |
| POST | `/api/v1/agent/sessions` | 创建 Agent 会话 |
| GET | `/api/v1/agent/sessions` | 当前用户会话列表 |
| GET | `/api/v1/agent/sessions/{sessionId}/messages` | 当前用户指定会话消息历史 |
| POST | `/api/v1/agent/sessions/{sessionId}/messages` | 发送消息并触发 Harness |
| GET | `/actuator/health` | 健康检查 |

## 4. Model Provider 设计

当前模型抽象位于 `yanban-core/src/main/java/com/yanban/core/model/`。

- `ChatModelProvider#chat`：非流式调用，用于 Harness 工具轮次与普通补全。
- `ChatModelProvider#streamChat`：流式调用，返回 `Flux<ChatChunk>`。
- `DeepSeekModelProvider` 使用 OpenAI 兼容 `/chat/completions` 协议。
- `DeepSeekProperties` 配置前缀：`yanban.model.deepseek`。
- `application-dev.yml` 中 DeepSeek API Key 从 `${DEEPSEEK_API_KEY}` 读取，不写入仓库。
- 单元测试使用本地 Mock HTTP Server，不访问真实 DeepSeek。

流式解析策略：

- 支持标准 SSE `data: {...}` 行。
- 支持 `data: [DONE]` 完成标记。
- 兼容 WebClient 直接解码出的 JSON data 字符串。
- `choices[0].delta.content` 映射为 token chunk。
- `choices[0].finish_reason` 映射为 done chunk。

## 5. Tool 设计

当前 Tool 抽象位于 `yanban-core/src/main/java/com/yanban/core/tool/`。

- `ToolDefinition`：工具名称、描述、JSON Schema 参数。
- `ToolExecutor`：工具执行接口。
- `ToolRegistry`：注册、按名查找、执行、导出 OpenAI tools 格式。
- `EchoToolExecutor`：内置测试工具，原样返回入参 `message`。
- `ToolRegistryConfig`：Spring 启动时收集所有 `ToolExecutor` Bean 注册到统一 Registry。

导出给模型的 tools 使用 `yanban-core` 的 `ToolSpec`，格式为 OpenAI-compatible function tools。

## 6. Harness 设计

当前 Harness 位于 `yanban-core/src/main/java/com/yanban/core/harness/`。

最小循环行为：

1. 输入 `history + userMessage`。
2. 组装 `ChatRequest`，携带当前 `ToolRegistry#listToolsForModel()`。
3. 调用 `ChatModelProvider#chat`。
4. 如果 assistant message 没有 `tool_calls`，返回成功结果。
5. 如果有 `tool_calls`：
   - 解析 `function.arguments` JSON。
   - 转换为 `com.yanban.core.tool.ToolCall`。
   - 执行 `ToolRegistry#execute`。
   - 将工具结果序列化为 role=`tool` 的 `ChatMessage`。
   - 继续下一轮。
6. 达到 `maxSteps` 后返回失败结果，错误信息包含 `max_steps`。

当前 A-10 暂未做：

- RAG 注入。
- MCP 工具。
- 会话持久化集成。
- 流式 Harness 多轮事件。

这些会在 A-11 之后按计划接入。

## 7. Agent API 与持久化集成

`yanban-api` 中新增 `com.yanban.api.agent` 包：

- `AgentController`：REST 入口。
- `AgentService`：会话归属校验、历史加载、Harness 调用、消息持久化。
- DTO：`CreateSessionRequest`、`SendMessageRequest`、`AgentSessionResponse`、`AgentMessageResponse`、`SendMessageResponse`。

当前持久化策略：

1. 创建会话时写 `agent_sessions`，保存模型 provider/model/maxSteps/ragDisabled 快照。
2. 发送消息时先读取当前会话历史 `agent_messages`。
3. 将历史转换为 `ChatMessage` 后调用 `HarnessEngine`。
4. 只持久化本轮新增消息，即 `history.size()` 之后的 Harness 结果消息。
5. user/assistant/tool 消息均写入 `agent_messages`。
6. assistant 的 `tool_calls` 序列化到 `tool_calls_json`。
7. 当前阶段未单独持久化每步 `agent_tool_runs`；A-10 中工具调用已写日志，后续 A-11+ 可补入 DB 审计。

权限策略：

- 所有 `/api/v1/agent/**` 接口使用 JWT 当前用户。
- 查询/发送消息前通过 `sessionId + userId` 查询会话。
- 非本人会话返回 `404 Not Found`，避免暴露资源存在性。

## 8. 阶段 A 知识库设计

当前阶段 A 的知识库实现位于 `yanban-knowledge` 模块，采用最小可演示方案。

数据模型：

- `kb_documents`：文档元数据（`user_id`、`filename`、`status`、`is_public`）。
- `kb_chunks`：文本分块（`document_id`、`chunk_index`、`chunk_text`）。

上传链路：

1. `POST /api/v1/kb/documents/simple-upload`
2. 同步接收 `MultipartFile`
3. 使用 Tika 提取文本
4. 固定长度分块（当前 `500` 字符）
5. 写入 `kb_chunks`
6. 文档状态置为 `READY`

检索链路：

1. `POST /api/v1/search`
2. `SimpleKnowledgeSearchService` 使用 SQL `LIKE` 检索 `kb_chunks`
3. 权限过滤：
   - 当前用户自己的私有文档
   - 所有公开文档
4. 返回 `KnowledgeSearchResult`

说明：

- 这是阶段 A 的临时实现，不含 Kafka、ES、Embedding。
- `search_knowledge` 已注册为 ToolExecutor Bean，可被后续 Harness / Function Calling 调用。
- 当前 `search_knowledge` 工具参数中显式带 `userId`，后续可在更完整的工具执行上下文中替换为隐式用户上下文。

## 9. 默认 RAG 设计

A-13 当前采用“检索后注入 system context”的方案，而不是先让模型自主调用 `search_knowledge`。

流程：

1. `AgentService` 根据单次请求或会话配置计算 `ragDisabled`。
2. `HarnessEngine` 在本轮 user message 入列前检查：
   - `ragDisabled == false`
   - `KnowledgeContextProvider` 可用
3. 若满足条件，则调用：
   - `KnowledgeContextProvider#searchContext(userMessage, userId, 3)`
4. 若检索结果非空，则生成一条 role=`system` 的知识库上下文消息。
5. 然后再追加本轮 role=`user` 消息，进入正常模型调用循环。

当前优点：

- 产品行为清晰，默认即带知识库上下文。
- 不依赖模型先学会调用工具。
- 与 A-12 的简化 SQL 检索实现容易对接。

当前限制：

- 每轮只自动检索一次。
- 还没有“模型二次追问时再次主动检索”的高级策略。
- system context 文本仍为简单拼接，后续可改为更结构化模板。

`search_knowledge` 工具仍保留，供后续模型主动二次检索与 B 阶段更复杂 RAG 路径复用。

## 10. WebSocket 流式对话设计

当前 WebSocket 方案采用 Spring 原生 `WebSocketHandler`，端点为：

```text
/api/v1/ws/chat?token=<accessToken>
```

认证：

- 通过 `WebSocketAuthHandshakeInterceptor` 在握手阶段解析 query 参数 `token`。
- 使用现有 `JwtService#parseAccessToken` 校验。
- 成功后将 `JwtUser` 放入 session attributes。
- 失败则握手返回 `401`。

消息协议：

- 客户端请求：`sessionId`、`content`、`ragDisabled`、`skillId`
- 服务端事件：
  - `chunk`
  - `done`
  - `error`

当前阶段 A 流式实现说明：

- WebSocket 走 `ChatModelProvider#streamChat`。
- 当前是“直接流式 provider 调用”，尚未实现完整的多轮流式 Harness + tool event 流。
- 在收到客户端消息后：
  1. 校验 session 归属。
  2. 读取历史消息。
  3. 先持久化本轮 user 消息。
  4. 调用 `streamChat`。
  5. 每个 token 推送 `chunk`。
  6. 完成后持久化 assistant 文本，推送 `done`。
- `ragDisabled` 字段已在协议中保留，但当前 WS 流式路径仍是阶段 A 简化版，后续可与 Harness/RAG 全路径进一步统一。

## 11. 用户设置设计

A-15 当前将用户默认配置存储在 `sys_user_settings` 表，采用“按 user_id 一条记录”的模型。

字段（阶段 A）：

- `default_provider`
- `deepseek_api_key_encrypted`
- `deepseek_model`
- `deepseek_temperature`
- `max_steps`
- `rag_default_enabled`

接口：

- `GET /api/v1/settings`
- `PUT /api/v1/settings`

安全策略：

- `deepseekApiKey` 仅允许通过 `PUT` 写入。
- 数据库存储为 AES/GCM 密文。
- `GET` 不返回明文 key，仅返回 `deepseekApiKeyConfigured` 布尔值。
- 当前加密密钥派生自 `yanban.jwt.secret` 的 SHA-256；阶段 A 够用，后续可替换为独立 KMS/专用配置密钥。

会话默认值继承规则：

- 创建会话时，如果请求未显式提供字段，则优先使用用户设置：
  - `defaultProvider`
  - `deepseekModel`
  - `maxSteps`
  - `ragDefaultEnabled`
- 其中 `ragDefaultEnabled=false` 会转换为 session 侧 `ragDisabled=true`。

当前限制：

- Provider 仍只有 `deepseek`。
- per-user `deepseekApiKey` 已可存储，但底层 `DeepSeekModelProvider` 仍主要使用当前全局配置；后续在 B 阶段接 GLM / 多 Provider 时再统一抽象运行时凭据选择。

## 12. 前端认证与路由设计

A-16 当前前端使用：

- Vue 3
- Vite
- TypeScript
- Pinia
- Vue Router
- Naive UI
- Axios

当前路由：

- `/login`
- `/register`
- `/chat`
- `/settings`

认证状态管理：

- `auth` store 负责保存：
  - `accessToken`
  - `refreshToken`
  - `currentUser`
- token 持久化到 `localStorage`。
- 应用启动时先 `restore()`，再调用 `/api/v1/users/me` 尝试恢复登录态。

路由守卫：

- `requiresAuth=true` 的页面在未登录时跳转 `/login`。
- `guestOnly=true` 的页面在已登录时跳转 `/chat`。

HTTP 侧策略：

- Axios 自动给 `/api/v1/**` 请求附加 Bearer Token。
- 收到 `401` 时自动清理本地 token，并跳回登录页。
- Vite dev server 通过 proxy 转发 `/api` 到后端 `:8080`，避免开发期 CORS 配置复杂化。

A-17 之后，`/chat` 已接入实际对话主链路：

- 页面初始化先请求 `GET /api/v1/agent/sessions`。
- 选择会话后请求 `GET /api/v1/agent/sessions/{id}/messages`。
- 发送消息时：
  1. 若当前无会话，则先 `POST /api/v1/agent/sessions` 创建会话。
  2. 前端将 user 消息先插入本地消息列表。
  3. 建立 WebSocket：`/api/v1/ws/chat?token=...`
  4. 发送 `{ sessionId, content, ragDisabled, skillId }`
  5. 流式接收 `chunk`，实时拼接 assistant 文本。
  6. 收到 `done` 后再重新拉取消息历史，确保与数据库一致。

当前前端 RAG 行为：

- 勾选框“本次不使用知识库”直接映射到 WS 请求中的 `ragDisabled`。
- 切换已有 session 时，UI 会用该 session 的 `ragDisabled` 作为当前默认显示值。

当前限制：

- 还未做消息滚动定位、重发、连接断线重试。
- 还未接入 tool event 可视化。

A-18 之后，`/settings` 已接入真实设置表单：

- 页面初始化调用 `GET /api/v1/settings`。
- 保存时调用 `PUT /api/v1/settings`。
- `deepseekApiKey` 字段遵循“仅写不回显”原则：
  - 前端只展示“已配置/未配置”状态
  - 每次保存后清空输入框
- 设置项包括：
  - `defaultProvider`
  - `deepseekModel`
  - `deepseekTemperature`
  - `maxSteps`
  - `ragDefaultEnabled`
- 这些设置会影响后续新建会话的默认快照行为（由后端 A-15 已实现）。

A-19 之后，阶段 A 文档入口如下：

- `README.md`：项目总览、启动步骤、前后端入口
- `docs/SETUP.md`：环境与中间件准备
- `docs/API-smoke.md`：REST 冒烟请求示例
- `docs/WEBSOCKET.md`：WebSocket 对话协议

当前文档组织已足以支持阶段 A 从零启动与联调。

## 13. 阶段 B 基础设施初始化

B-01 已完成本地阶段 B 中间件启动与初始化：

- Elasticsearch：`docker.elastic.co/elasticsearch/elasticsearch:8.10.4`
- Kafka：`apache/kafka:3.8.1`
- MinIO：`minio/minio:RELEASE.2025-04-22T22-12-26Z`

说明：

- Kafka 原先在 Compose 中使用 `bitnami/kafka:latest`，但实际拉取时遭遇镜像仓库 `429 Too Many Requests`。
- 为了保证本地可运行与版本固定，当前已切换到 `apache/kafka:3.8.1`。
- 这一调整已记录在 `progress.md` 中，后续若需要回切 bitnami，可在环境稳定时再评估。

当前初始化结果：

- Kafka topic：`file-processing`
- MinIO bucket：`yanban-agent`
- ES index template：`yanban-kb-chunks-v1-template`
- ES 向量维度：`1024`

当前配置入口：

- `spring.kafka.bootstrap-servers`
- `yanban.knowledge.elasticsearch.endpoint`
- `yanban.knowledge.elasticsearch.index-name`
- `yanban.knowledge.elasticsearch.vector-dimensions`
- `yanban.knowledge.minio.endpoint`
- `yanban.knowledge.minio.access-key`
- `yanban.knowledge.minio.secret-key`
- `yanban.knowledge.minio.bucket`
- `yanban.knowledge.minio.secure`

当前阶段仅完成“中间件可用 + 初始化资源就绪”，业务代码尚未真正接入 Kafka / ES / MinIO 读写链路；这些将在 B-02 之后继续实现。

B-02 之后，数据库与实体层扩展如下：

KB 扩展：

- `kb_documents` 新增：
  - `object_key`
  - `mime_type`
  - `file_size`
  - `error_message`
- `kb_chunks` 新增：
  - `es_doc_id`
- 新增 `kb_chunk_uploads`：用于记录阶段 B 分片上传元数据。

论文侧新增：

- `paper_tasks`
- `paper_task_rounds`

当前实体已分别落在：

- `yanban-knowledge`：`KbDocument`、`KbChunk`、`KbChunkUpload`
- `yanban-paper`：`PaperTask`、`PaperTaskRound`

说明：

- 这一阶段先打通 Flyway、JPA Entity、Repository 与测试。
- B-03 已接入 MinIO 分片上传与 merge：
  - `upload/chunk` 先写 MinIO 临时对象 + `kb_chunk_uploads`
  - `upload/merge` 合并后写正式对象，并创建 `kb_documents(status=PROCESSING)`
  - 同时发送 Kafka `file-processing` 消息
- B-04 已接入 Kafka 消费解析链路：
  - `FileProcessingConsumer` 消费 `file-processing`
  - `FileProcessingService` 从 MinIO 读取对象，用 Tika 解析，切块后写入 `kb_chunks`
  - 成功：`PROCESSING -> READY`
  - 失败：`PROCESSING -> FAILED` 并写 `error_message`
- B-05 已接入向量化与 ES 写入：
  - `FileProcessingService` 在切块持久化后调用 `VectorizationService`
  - `VectorizationService` 通过 `EmbeddingClient` 获取向量
  - 当前实现为 `DashScopeEmbeddingClient`
  - 再通过 `KnowledgeIndexService` 写入 ES
  - 当前实现为 `ElasticsearchKnowledgeIndexService`
  - ES 文档字段至少包括：`chunkId`、`documentId`、`userId`、`isPublic`、`chunkIndex`、`text`、`vector`
  - ES 返回 `_id` 后回写到 MySQL `kb_chunks.es_doc_id`
- 维度校验在应用侧先执行：若 embedding 维度与 `yanban.knowledge.elasticsearch.vector-dimensions` 不一致，则直接抛错并走 FAILED。
- B-06 已接入正式搜索编排：
  - `HybridKnowledgeSearchService` 作为主搜索入口
  - 先调用 `EmbeddingClient` 生成 query vector
  - 再通过 `KnowledgeSearchIndexClient` 查询 Elasticsearch
  - 当前 ES 检索实现为 `ElasticsearchKnowledgeSearchIndexClient`
  - ES 侧做权限过滤：`userId = 当前用户` 或 `isPublic = true`
  - 应用侧再叠加词面命中 bonus，形成当前阶段的“向量 + 词面”混合排序
- 当前仍保留 `SimpleKnowledgeSearchService` 作为数据库 fallback：
  - 当 DashScope embedding 不可用或 ES 查询异常时，自动回退到 SQL LIKE 方案
  - 这样可保证开发环境或外部依赖短暂失败时，知识检索链路仍可工作
- 现阶段 `POST /api/v1/search` 与 `search_knowledge` tool 已切换到新的主搜索编排。
- 为支撑 B-07 知识库页面，已补充文档管理后端：
  - `GET /api/v1/kb/documents`：返回当前用户文档列表，含 `status`、`mimeType`、`fileSize`、`errorMessage`
  - `DELETE /api/v1/kb/documents/{documentId}`：仅允许文档拥有者删除
- 删除策略当前固定为：
  1. 删除 MySQL `kb_chunks`
  2. 按 `documentId` 删除 Elasticsearch 索引文档
  3. 若存在 `objectKey`，删除 MinIO 原始对象
  4. 删除 MySQL `kb_documents`
- `simple-upload` 产生的旧测试文档因没有 MinIO/ES 对象，删除时会自动跳过对应清理步骤。
- B-07 前端知识库页当前采用以下交互策略：
  - 路由：`/knowledge-base`
  - 上传：浏览器端按 `1 MB` 分片，顺序调用 `POST /api/v1/upload/chunk`，成功后再调用 `POST /api/v1/upload/merge`
  - 重试：单个分片失败时最多自动重试 3 次
  - 状态展示：通过 `GET /api/v1/kb/documents` 轮询，每 3 秒刷新一次；当不存在 `PROCESSING / UPLOADING` 文档时停止轮询
  - 删除：用户在列表中触发 `DELETE /api/v1/kb/documents/{documentId}`
- B-08 前端检索调试页当前采用以下交互策略：
  - 路由：`/knowledge-base/search-debug`
  - 页面直接调用 `POST /api/v1/search`
  - 输入参数：`query`、`topK`
  - 输出展示：`filename`、`documentId`、`chunkIndex`、`score`、`isPublic`、`chunkText`
- B-09 / B-10 当前论文处理链路设计：
  - 创建任务接口：`POST /api/v1/paper/process`
  - 查询任务接口：`GET /api/v1/paper/tasks/{taskId}`
  - SSE 接口：`GET /api/v1/paper/events?taskId=`
  - 控制接口：
    - `POST /api/v1/paper/tasks/{taskId}/pause`
    - `POST /api/v1/paper/tasks/{taskId}/resume`
    - `POST /api/v1/paper/tasks/{taskId}/stop`
  - 下载接口：`GET /api/v1/paper/tasks/{taskId}/download`
  - 请求方式：`multipart/form-data`
  - 字段：`file`、`scoreThreshold`、`maxRounds`、`innerMaxAttempts`、`literatureCount`、`targetLanguage`
  - 当前仅接受 `.docx`
  - 服务入口：`PaperTaskService`
  - 异步编排入口：`PaperOrchestrator`
  - 原始文件存储：`PaperStorageService`
    - 优先写 MinIO（默认 bucket `yanban-agent`，prefix `paper/originals`）
    - 若无 MinIO Bean，则回退写本地 `data/paper-storage/`
  - 结果文件：
    - 已新增 `paper_tasks.final_object_key`
    - 编排完成后将原始 docx 复制为当前阶段的“结果文件占位版”，供下载链路验证
  - 当前最小事件流已接入：
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
  - 目前仍是“骨架编排”：
    - 事件类型和状态推进已对齐设计方向
    - 内容产出仍为占位逻辑
    - B-10 后续仍需继续替换为真实 Summary / 分章 / Review / OpenAlex 处理实现
- B-11 当前前端论文页交互策略：
  - 路由：`/paper`，支持 `?taskId=` 打开已有任务
  - 上传：表单提交 `multipart/form-data` 到 `POST /api/v1/paper/process`
  - 进度：前端使用 `fetch` + `Authorization: Bearer ...` 直连 SSE，避免原生 `EventSource` 无法附带 JWT header 的限制
  - 事件结束策略：收到 `complete / error / paused` 后主动关闭本次 SSE 连接
  - 下载：前端使用带 Bearer token 的 `axios blob` 下载，不再使用新窗口直开 URL，避免下载请求丢失鉴权 header
- B-12 当前对话跳转论文页实现策略：
  - 采用“轻量意图处理”而非先注册模型 tool
  - 后端 `PaperRevisionIntentService` 检测“润色论文 / 修改论文 / 帮我润色”等中英文关键词
  - 命中后短路正常模型调用，直接返回助手消息与导航 URL `/paper`
  - REST `POST /api/v1/agent/sessions/{sessionId}/messages` 返回 `navigationUrl`
  - WebSocket `/api/v1/ws/chat` 在 `done` 事件中附带 `navigationUrl`
  - 当前不创建占位 `paper_task_id`，由用户进入论文页后再上传 docx 创建真实任务
  - 前端聊天页会从消息内容或 WS done 事件中提取 `/paper`，显示“打开论文修改页”按钮
- B-13 当前多模型实现策略：
  - 在 `yanban-core` 新增 `GlmModelProvider` 与 `GlmProperties`
  - 新增 `RoutingChatModelProvider` 作为统一 `ChatModelProvider` 门面，根据 `ChatRequest.provider` 路由到 `deepseek` 或 `glm`
  - `ChatRequest` / `HarnessRequest` 已扩展 `provider` 与 `apiKey` 字段，支持会话快照 provider 与用户密钥透传
  - `AgentService` 创建 session 时：
    - `modelProviderSnapshot` 取用户设置或请求显式值
    - `modelSnapshot` 根据 provider 选择 `deepseekModel` 或 `glmModel`
  - `AgentService` 与 `ChatWebSocketHandler` 在真实调用前，会按 provider 读取并解密用户密钥：
    - `deepseek` -> `deepseek_api_key_encrypted`
    - `glm` -> `glm_api_key_encrypted`
  - `sys_user_settings` 已扩展：
    - `glm_api_key_encrypted`
    - `glm_model`
  - 当前设置 API 已支持：
    - `defaultProvider = deepseek | glm`
    - `glmApiKey`
    - `glmModel`
- B-14 当前设置页扩展策略：
  - 设置页已接入：
    - DeepSeek / GLM provider 切换
    - DeepSeek / GLM API Key
    - GitHub PAT
    - filesystem 允许根目录（多行文本）
    - Skills 启用 / 禁用
  - `sys_user_settings` 已进一步扩展：
    - `github_pat_encrypted`
    - `filesystem_roots_text`
    - `disabled_skills_json`
  - 新增后端 `GET /api/v1/skills`：
    - 当前为扫描 `skills/builtin/` 与 `skills/user/` 的轻量实现
    - 返回 `id / name / source / path / enabled / description`
    - `enabled` 基于用户设置里的 `disabled_skills_json`
  - 当前 Skills API 属于阶段性 stub：
    - 已满足设置页展示与启停切换
    - 后续可在 B-20 再扩为更完整的 Skill 元数据与运行时过滤机制
- B-15 / B-16 / B-17 当前 MCP 设计：
  - `yanban-mcp` 已新增 stdio JSON-RPC client：
    - `DefaultMcpStdioClient`
    - `ProcessMcpTransport`
    - `ContentLengthMcpFraming`
  - 当前协议实现：
    - `initialize`
    - `notifications/initialized`
    - `tools/list`
    - `tools/call`
    - `close`
  - 命令白名单：
    - 通过 `McpServerProcessConfig.allowedCommands` 校验进程首个可执行文件
    - 不在白名单时直接拒绝启动
  - API 侧 MCP 配置入口：
    - `yanban.mcp.github.*`
    - `yanban.mcp.filesystem.*`
  - 工具注册策略：
    - `McpToolRegistryCustomizer` 启动时尝试 discovery
    - GitHub 工具统一加前缀 `mcp_github__`
    - filesystem 工具统一加前缀 `mcp_fs__`
    - discovery 失败仅记录 warning，不阻塞应用启动
  - 运行时调用策略：
    - `McpProxyToolExecutor` 执行具体远端 MCP tool
    - 通过 `ToolExecutionContext` 读取当前用户 ID
    - 再从 `UserSettingsService` 取出当前用户的 `GITHUB_TOKEN` 与 `filesystemRoots`
  - filesystem 路径白名单：
    - `FilesystemPathGuard` 对 `path / paths / directory / root` 参数做规范化校验
    - 仅允许落在当前用户 `filesystemRoots` 内的绝对规范路径
    - 越权路径直接拒绝，不进入 MCP 子进程调用
  - 当前仍未补齐真实 Node MCP Server 手动联调记录；后续具备 Node 环境时需补 GitHub/filesystem 的真实 `list_tools / call_tool` 验证
- B-18 / B-19 / B-20 当前 Skills 设计：
  - `yanban-skills` 已新增：
    - `SkillLoader`
    - `SkillRegistry`
    - `SkillDefinition`
  - 扫描目录：
    - `skills/builtin/**`
    - `skills/user/**`
  - 当前解析策略：
    - `SKILL.md` 作为主 prompt 内容
    - `skill.yaml` 使用轻量行解析，提取 `name / description / allowed_tools`
  - `code-review` 已补：
    - `skills/builtin/code-review/SKILL.md`
    - `skills/builtin/code-review/skill.yaml`
    - allowed tools 当前限定为 filesystem 相关前缀工具
  - `HarnessRequest` 已扩展：
    - `skillPrompt`
    - `allowedToolNames`
  - `HarnessEngine` 已支持：
    - 注入 skill prompt 为 system message
    - 仅向模型暴露白名单 tools
    - 若模型调用白名单外 tool，则返回失败 tool result
  - `GET /api/v1/skills` 现已从真实 `SkillRegistry` 读取，而非纯 stub
  - `PUT /api/v1/skills/{id}/enabled` 已支持用户级启用 / 禁用
  - `POST /api/v1/skills/refresh` 已支持刷新扫描结果
  - 聊天页当前已接入 Skill 下拉；发送 WS 时会带 `skillId`
  - WS 路径当前对 skillId 的处理：
    - 若带 skillId，则改走 `AgentService.sendMessage(...)` 的 Harness 路径，而不是原先直接 `streamChat`
    - 因而 skill 模式下当前是“单次 chunk + done”响应，而非 token 级流式 tool 事件
  - 语义说明：
    - 对禁用 / 不存在 skillId，WS 当前返回 `error` 事件而不是 HTTP 400（因为 WebSocket 握手已完成后无法再返回常规 REST 400）
- B-21 当前 OCR 设计：
  - 新增 `OcrProvider` 抽象与 `HttpOcrProvider`
  - 配置前缀：`yanban.knowledge.ocr.*`
  - `FileProcessingService` 在 `mimeType` 以 `image/` 开头时走 OCR 分支
  - 若 OCR Provider 未配置，则文档直接标记为 `FAILED`，错误为 `OCR 未配置`
- B-22 / B-23 当前 CLI 设计：
  - 新增 `yanban-cli`：
    - `YanbanCli`
    - `CliConfigStore`
    - `CliApiClient`
  - 配置文件位置：`~/.yanban-agent/config.properties`
  - 已支持子命令：
    - `yanban login`
    - `yanban chat`
    - `yanban config list`
    - `yanban config set`
    - `yanban kb list`
    - `yanban kb upload <file>`
    - `yanban paper status <taskId>`
  - `chat` 当前通过后端 WebSocket 打印流式 chunk
  - `kb upload` 当前走 `simple-upload` 单文件接口，而非 Web 侧分片上传
  - `paper status` 当前输出 `status / currentStage`，并将 `currentStage` 作为 recent log 的简化替代；若后续需要严格“最近一条日志”，可再补专门查询接口或 CLI 的 SSE 截断读取逻辑
- B-24 当前测试与 CI 设计：
  - 父 POM 已加入 Surefire 标签控制：
    - 默认排除 `@Tag("manual")`
    - `mvn test -Dgroups=manual` 时通过 profile 取消默认排除
  - 默认自动化测试继续采用：
    - H2 内存数据库（`MODE=MySQL`）
    - Mock 外部依赖（DeepSeek / DashScope / MCP / ES 侧替身）
  - `yanban-api/src/test/resources/application.properties` 统一关闭了 Kafka listener auto startup，避免默认测试尝试连接本地 Kafka
  - 当前未引入 Testcontainers 作为默认 CI 路径
  - 当前未在 `private-helper-agent/` 内添加真正可被 GitHub 识别的 workflow 文件；原因是开发边界被限制在该子目录内，而 GitHub Actions 工作流要求仓库根级 `.github/workflows/`
- B-25 当前开源准备设计：
  - `README.md` 已改为阶段 B 能力说明
  - `docs/SETUP.md` 已补充全量中间件 / MCP / OCR / manual test 说明
  - License 选择：Apache-2.0
  - 开源卫生扫描当前排除了：
    - `node_modules/`
    - `dist/`
    - `target/`
  - 当前可确认：
    - 工作区未发现真实密钥文本
    - 本地 `target/`、`frontend/node_modules/` 存在但属于忽略目录
    - 由于当前 WSL 看不到 `.git` 元数据，无法在此环境完成最终 git-index 级卫生验证
- 阶段 B 门禁当前结论：
  - 自动化门禁已基本通过：
    - `G-B1` 构建全绿
    - `G-B9` 文件级开源卫生基本通过
  - 仍待后续手测 / 外部环境补齐的门禁：
    - `G-B2` Web 知识库页真实闭环
    - `G-B3` `/chat` 前端验收
    - `G-B4` `/paper` 前端三步验收
    - `G-B5` 真实 GLM key 手测
    - `G-B6` 真实 Node MCP 联调
    - `G-B7` code-review Skill 真实联调
    - `G-B8` CLI 对真实后端手测
    - `G-B10` git-diff 级遗留隔离核验

## 14. JWT 设计

- `accessToken` 与 `refreshToken` 都使用 HS 签名。
- claim `typ=access` 表示访问令牌。
- claim `typ=refresh` 表示刷新令牌。
- `sub` 保存 `userId`。
- `username` claim 保存用户名快照。
- 签名密钥配置：`yanban.jwt.secret`，默认从 `${JWT_SECRET}` 读取。
- 当前 refresh token 未接 Redis 黑名单；后续如实现登出/吊销，再接 Redis。

## 15. 数据库迁移

当前 Flyway 迁移：

- `V1__create_sys_users.sql`
- `V2__create_agent_tables.sql`

表：`sys_users`

字段：

- `id`
- `username`
- `password_hash`
- `created_at`
- `updated_at`

Agent 表：

- `agent_sessions`：会话级 provider/model/max_steps/rag_disabled 快照。
- `agent_messages`：用户、助手、tool 等消息；预留 `paper_task_id` 弱关联。
- `agent_tool_runs`：每次工具调用审计。

## 16. 测试策略

当前默认测试不依赖真实 MySQL：

- 使用 H2 内存数据库，`MODE=MySQL`。
- Flyway 迁移在测试启动时执行。
- 外部 Docker 未启动时仍可执行 `mvn test`。

后续涉及 MySQL/Redis/ES/Kafka/MinIO 的集成测试，应继续区分默认 Mock/H2 测试与 manual/Testcontainers 测试。

## 17. 当前环境限制

当前开发环境中：

- WSL 没有 Linux `java` / `docker` 命令。
- Windows JDK 位于 `C:\software\java\17.0.5`。
- Windows Maven 位于 `C:\software\apache-maven-3.9.4-bin\apache-maven-3.9.4`。
- Docker Desktop 已启动后可通过 Windows Docker CLI 操作。
- WSL 内仍无 `docker` 命令，验证命令使用 `cmd.exe` / `powershell.exe` 调用 Windows Docker CLI。
- 本机 `3306` 端口已被占用，因此本项目 MySQL Docker 服务映射为宿主机 `3307` → 容器 `3306`。
- 后端 `application-dev.yml` 使用 `jdbc:mysql://localhost:3307/yanban_agent`。

## 18. 第二期论文模块架构（LaTeX 方向）

> 方向：从 docx 转向「面向 LaTeX 论文的 AI 审稿/改稿 + 文献补全」。输入 `.tex` + `.bib`，产出可在 VSCode 编译的三件套。完整设计见 `discussion_about_fix_paper_20260615.md`。

### 18.1 能力分层与阶段链

能力分层：L0 解析+结构分析、L1 语言润色、L2 结构审查、L3 文献地基、L4 研究级建议、L5 多模态图 critique（后置）。

阶段链（任务状态机）：

```text
PARSE -> ROLE_RECOGNITION -> STRUCTURE_CHECK -> PROFILE -> RETRIEVE
      -> GAP_ANALYSIS -> POLISH -> ASSEMBLE -> COMPLETE
```

状态：RUNNING / PAUSED / STOPPED / COMPLETED / FAILED / WAITING_INPUT（新增，交互检查点释放线程）。

### 18.2 L0 LaTeX 解析与文档模型

务实分词器（非完整解析），自写 tex 分词；bib 用库或自写。文档模型（Document 树）：

```text
preamble(只读但抽 title/authors/keywords)
sections[level/title/role/rawRange/blocks]
protectedSpans[]   占位保护数据源
floats[kind/label/caption/graphics/referencedBy/rawContent(表格原文)]
citeUsage[]        认全部引用命令变体
crossRef           认全部 ref 家族（label 与 ref 映射）
bib                外部 .bib + 内联 thebibliography
sourceMap          节级 + 节内字符区间
```

占位保护（mask）：受保护元素换占位符送模型，返回校验占位符集合 ⊆ 输入再换回。第一版不真编译，只静态分析。

### 18.3 角色识别与结构确认检查点

三级：启发式（关键词中英 + 位置 + 内容）→ LLM 复核（限定枚举）→ 用户可改。结构确认检查点按内容位置判断真缺失，歧义批量问 + 默认保持原样；阻塞类走 WAITING_INPUT。

### 18.4 文献检索可复用架构（L3）

```text
LiteratureSource providers (OpenAlex / arXiv / S2 可插拔)
   -> LiteratureService (通用：查+去重+缓存+卡片+排序，分 LIGHT/DEEP)
        -> 对话内置工具(默认 LIGHT, function calling 非 MCP)
        -> 论文 L3 流水线 (DEEP + 概念阶梯/剧本编排)
   共用 MySQL 缓存 + 文献卡片 DTO + bib 生成
```

identity 主键：DOI > arXiv id > OpenAlex/S2 id > 标题哈希；三层缓存 raw/card/analysis（分析永久复用）。MySQL 当账本，ES 后置（留排序/召回抽象入口，第一版内存向量 cosine）。文献缓存只存公开文献，ES 用独立索引与私有 KB 分开。

### 18.5 研究画像与 gap 分析数据结构（L4）

研究画像（PROFILE）：problem/method/contributions/datasets/baselines/metrics/tasks/keywords。

Suggestion 统一对象：

```text
Suggestion{ track(advocacy/critique), category, severity(blocker/minor),
            statement, evidence[卡片 id], applicable,
            patch{ contentType(A真内容/B骨架), anchor, latexSnippet, addedBibKeys[] } }
```

A/B 判据按「能否 grounding」；辩护轨进 tex 补丁、批评轨进审查报告；诚实闸门：吹不动转批评、绝不编造。

### 18.6 数据模型（详见讨论纪要第 12 节）

全局（公开，无 userId）：literature_cards（卡片 + L3c 分析 + 版本，显式 id 列 + title_hash + upsert-by-any-id）。

私有（按任务）：

```text
paper_tasks(扩展 input_format/mode/main_entry)   paper_task_rounds(复用,事件日志)
paper_sections(行：role/role_source/char范围/polish_status)
paper_task_analysis(research_profile/concept_ladder/gap_matrix JSON)
suggestions   suggestion_evidence(->全局卡片)   paper_task_literature(->全局卡片)
paper_task_clarifications(交互检查点)
paper_task_artifacts(polished_tex/suggested_bib/review_report/parsed_model)
```

文件/产物走 MinIO（沿用 object_key 模式），库内存 object_key + 元数据；完整解析模型存 MinIO JSON 产物。sections 成行（要 FK + 按节状态 + 续传 + 用户改角色），解析细节嵌 JSON。

### 18.7 编排（可恢复阶段步）

每阶段从持久化状态读输入、写持久化输出；dispatcher 按 current_stage 续跑。PAUSE 用协作式 flag（沿用一期 checkpoint 忙等，阶段内短点用）；WAITING_INPUT 结束线程，用户答后从下一阶段重启。崩溃恢复 v1 做方案 A（手动重启），自动续跑后置。长阶段频繁 checkpoint（RETRIEVE 每批、POLISH 每章每轮）。

### 18.8 交付物与两模式

三件套：润色后 .tex + 审查报告（每条挂真实引用 + 免责声明）+ suggested.bib。基础版（不改原文/原 bib 只推荐）/进阶版（改原文 + 自动补 cite，采纳后过静态 lint：cite key 存在、括号/环境配平、受保护元素数量一致、无残留占位符）。在线预览为源码级高亮 diff + 逐条勾选采纳。
