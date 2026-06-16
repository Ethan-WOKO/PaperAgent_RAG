# 研伴 Agent（Yanban Agent）— 产品设计文档

> 文档作用概述：记录**产品目标、需求边界、范围决策与长期功能设计基线**。这是产品层面的源文档；若实施细节有变化，不直接在本文件堆积执行过程，而应同步到 `implementation.md`、`architecture.md`、`progress.md` 等对应文档。
>
> **文档版本**：v0.2  
> **更新日期**：2026-05-24  
> **状态**：设计已定稿（待开发）  
> **文档目录**：`memory-bank/`（本文件所在目录）  
> **仓库根目录**：`private_helper_Agent/`  
> **产品名称**：**研伴 Agent**（英文代号：**Yanban Agent**；Maven/`private-helper-agent`）  
> **新实现路径**：`private_helper_Agent/private-helper-agent/`（与 `memory-bank/` 平级）  
> **遗留参考（只读，不修改）**：`PaiSmart-main/`、`paper-agent/`（仓库根下，与 `memory-bank/` 平级）

---

## 1. 文档目的与读者

本文档汇总产品讨论结论、需求决策与两个遗留项目 README 要点，作为 **研伴 Agent** 的统一设计基准。

**同目录文档**：[`tech-stack.md`](./tech-stack.md)、[`implementation.md`](./implementation.md)；实施过程维护 [`progress.md`](./progress.md)、[`architecture.md`](./architecture.md)。

**读者**：项目所有者、开发者、协作 Agent。

**开源**：计划将来开源（`private-helper-agent/README.md` 中文、注意密钥与示例配置分离）。

---

## 2. 产品愿景与目标

### 2.1 愿景

面向科研学习与自用的 **AI 研助平台**：内置 **知识库** 与 **论文修改**，统一 **Harness 对话** 承载 RAG、Skills、MCP；CLI 与 Web 共用能力，配置一致。

### 2.2 目标优先级

| 优先级 | 目标 |
|--------|------|
| P0 | 学习 MCP Client、Function Calling、Harness、Skills |
| P0 | 内置知识库（上传/解析/向量化/检索）与论文修改（完整流水线） |
| P0 | 全新 Vue 前端，覆盖两遗留项目基本能力 + 设置与 Skills |
| P1 | JWT 登录；本地运行，架构可扩展至服务器部署 |
| P1 | 多模型：DeepSeek（默认）+ GLM（智谱）；用户可配置密钥与参数 |
| P2 | 论文润色质量专项优化（第二期） |
| P2 | 开源发布与文档完善 |

### 2.3 非目标（第一期）

- 实现 MCP Server（仅 Client）  
- PaiSmart 全套组织标签 / 管理员后台 / 抖音扩展  
- Skill 在线编辑器（仅文件夹放置）  
- Skill 自动路由  
- 修改 `PaiSmart-main/`、`paper-agent/` 源码（仅作参考）  
- 论文润色效果达到发表级（第二期）

### 2.4 第一期工期期望与风险

| 项 | 内容 |
|----|------|
| 期望 | 约 **1 周** 跑通主链路，随后迭代测试 |
| 风险 | 范围含：全新 Vue 全功能页、Harness、双模型、JWT、KB 全链路、论文全流程、MCP GitHub+filesystem、单元测试、**新写而非套壳** — 一周为 **冲刺目标**，建议按 **「可演示最小闭环 → 功能补齐」** 分两阶段执行（见 §11） |
| 原则 | 第一期以 **能跑通 + 学 Harness** 为主；论文 prompt 质量第二期改 |

---

## 3. 已决事项总表（需求决策）

| # | 主题 | 决策 |
|---|------|------|
| 1 | 知识库基础设施 | **A**：沿用 PaiSmart 栈 — MySQL + Elasticsearch + MinIO + Redis + Kafka；Docker Compose 一键部署 |
| 2 | Embedding | **阿里云 DashScope**（text-embedding-v4，与 PaiSmart 一致） |
| 3 | 仓库布局 | 新代码仅在 `private-helper-agent/`；**不修改** 两个遗留项目 |
| 4 | 数据库 | **统一 MySQL**（Agent 会话、用户、KB、论文任务等同库或同实例多 schema）；**不使用 SQLite**（理由见 §6.4） |
| 5 | 认证 | **保留 JWT** 注册/登录 |
| 6 | 部署 | 第一期 **本地**；后期部署到 **服务器**（需预留配置与 HTTPS/鉴权扩展点） |
| 7 | 配置 | **CLI 与 Web 共用一套配置**（同一配置文件或服务端配置 API） |
| 8 | MCP（第一期） | 主连接 **GitHub MCP**（项目检索）；内置 Skill `code-review` 另需 **filesystem MCP** 读本地文件 |
| 9 | 前端 | **全新 Vue 3**；第一期包含 §7.5 全部页面；不接受极简版 |
| 10 | 页面范围 | 知识库管理 + 检索调试；论文三步；通用对话 + Skill；设置（模型/MCP/Skills/max_steps） |
| 11 | RAG | 对话 **默认开启 RAG**；用户可勾选 **「本次不使用知识库」** |
| 12 | 论文入口 | 固定「论文修改」页 + 对话可说「润色」→ **跳转论文页** 上传 docx，底层同一 Orchestrator |
| 13 | Skills 管理 | 用户仅通过 **`skills/user/` 放文件夹**；**不支持** Web 在线编辑 SKILL.md |
| 14 | 内置 Skill | **`code-review`**；绑定 **MCP filesystem** 读本地文件 |
| 15 | 论文流程 | **完整保留**：Summary → 分章润色/审查 → 跨章审查 → 摘要 → **文献推荐（OpenAlex）** |
| 16 | 论文质量 | 第一期跑通；**第二期** 改 prompt/评分 |
| 17 | 论文存储 | **MinIO + 本地 fallback** |
| 18 | 论文任务与会话 | **推荐：分开存储，会话内引用**（见 §7.2.4） |
| 19 | 上传 | **保留分片断点续传**（大文件/弱网/可恢复；小文件未必更快） |
| 20 | 文档类型 | PDF、Word、Markdown；**图片 OCR** 可调外部工具 |
| 21 | 文档权限 | 仅 **公开 / 私有**（不做组织标签树） |
| 22 | 对话模型 | **仅一种会话**：Harness 对话；KB 检索作为工具/默认 RAG，**不单独**开 KB 聊天通道 |
| 23 | 索引 | **异步**；UI 显示「处理中」 |
| 24 | 流式 | 对话使用 **SSE 或 WebSocket 流式** 输出 |
| 25 | max_steps | 默认 **20**；设置页 **可配置** |
| 26 | 模型 | **DeepSeek**（默认）+ **GLM（智谱）**；用户配置密钥；架构预留 OpenAI 兼容 Provider |
| 27 | CLI/Web | **同期开发**；常用 Web，CLI 用于试用与自动化 |
| 28 | 环境 | Windows + **Docker Desktop** 长期运行；JDK **17**（与遗留项目一致，可升级 21 再评估） |
| 29 | 迁移 | **新写 Agent**，参考遗留逻辑，**不做空壳转发** |
| 30 | 质量 | **需要单元测试**；README **中文** |
| 31 | 许可 | **计划开源** |
| 32 | 技术栈 | **Java 后端 + Vue 前端** |
| 33 | 论文语言 | 默认 **中文**；支持 **中/英一键切换**（`targetLanguage`：zh/en） |
| 34 | 典型 KB 文件 | **论文 PDF、docx** 为主 |
| 35 | 产品名 | **研伴 Agent（Yanban Agent）** — 取代 Working title「Private Helper Agent」 |

---

## 4. 产品能力地图

```mermaid
flowchart TB
  subgraph product [研伴 Agent / private-helper-agent]
    subgraph fixed [固定功能]
      KB[知识库<br/>分片上传 / 异步索引 / 公开私有]
      Paper[论文修改<br/>完整 Orchestrator + SSE]
    end

    subgraph agent [统一 Harness 对话]
      Chat[流式对话 SSE/WS]
      RAG[默认 RAG 可关闭]
      Harness[多轮 FC / max_steps 可配]
      Skills[Skills 选择]
    end

    subgraph external [外部]
      MCP_GH[GitHub MCP]
      MCP_FS[Filesystem MCP]
      DS[DeepSeek]
      GLM[GLM]
    end

    CLI[CLI] --> product
    Web[Vue Web + JWT] --> product
    Harness --> KB
    Harness --> Paper
    Harness --> MCP_GH
    Skills --> MCP_FS
    Harness --> DS
    Harness --> GLM
  end
```

### 4.1 能力分层（不变）

| 层级 | 内容 |
|------|------|
| **固定功能** | 知识库、论文修改 — 独立菜单与 API，**不是** Skill |
| **Harness + Tools** | `search_knowledge`、论文任务查询/跳转等 |
| **Skills** | 工作方式文档；内置 `code-review`；用户目录 `skills/user/` |
| **MCP** | GitHub（发现项目）+ filesystem（code-review 读本地） |

---

## 5. 用户、安全与部署

### 5.1 用户与认证

- 注册/登录/JWT 刷新（参考 PaiSmart 模式，按新模块重写）  
- 文档 **公开/私有** 与 `user_id` 绑定  
- 第一期部署：**本机 + Docker 中间件**  
- 后期 **服务器**：需支持配置 `server.public-url`、CORS、HTTPS 终止、密钥环境变量化（设计预留，第一期可不实现完整运维）

### 5.2 配置（CLI / Web 共用）

建议统一存储：**MySQL `user_settings` 或本地 `~/.yanban-agent/config.yaml` + DB 同步**（实现时二选一为主、另一种导入导出）。

| 配置项 | 说明 |
|--------|------|
| `deepseek.api_key` / `glm.api_key` |  per-user 或全局（多用户时用 DB 加密字段） |
| `default_model_provider` | deepseek \| glm |
| `model`, `temperature`, `max_tokens` | 模型参数 |
| `harness.max_steps` | 默认 20 |
| `mcp.servers[]` | GitHub、filesystem 启动命令与环境变量 |
| `rag.default_enabled` | 默认 true；前端可覆盖单次会话 |

**安全**：密钥不入 Git；`application.yml` 仅示例；日志脱敏。

### 5.3 MCP（第一期）

| Server | 用途 | 备注 |
|--------|------|------|
| **github** | 在对话中搜索/浏览 GitHub 项目与仓库 | 需 PAT；配置项 `GITHUB_TOKEN` |
| **filesystem** | 内置 Skill `code-review` 读取用户指定本地路径 | 限制 allowed_roots（如 workspace） |

Harness 启动前根据设置页加载 MCP 白名单；失败时 UI 提示，不阻塞 KB/Paper 固定功能。

---

## 6. 系统架构

### 6.1 仓库布局

```text
private_helper_Agent/
├── memory-bank/                  # 项目记忆库（设计 / 技术栈 / 实施计划等）
│   ├── design-doucment.md
│   ├── tech-stack.md
│   ├── implementation.md
│   ├── architecture.md         # 运行时架构补充（随开发更新）
│   └── progress.md             # 进度与门禁记录（随开发更新）
├── PaiSmart-main/                # 只读参考
├── paper-agent/                  # 只读参考
└── private-helper-agent/         # 新实现（本产品设计范围）
    ├── pom.xml                   # 父 POM
    ├── yanban-core/              # Harness, session, tools, model providers
    ├── yanban-knowledge/         # KB 上传/解析/向量/检索
    ├── yanban-paper/             # 论文 orchestrator, docx, tasks
    ├── yanban-mcp/               # MCP client
    ├── yanban-skills/            # Skill loader
    ├── yanban-api/               # Spring Boot Web, JWT, REST, SSE/WS
    ├── yanban-cli/               # Picocli / Spring Shell
    ├── frontend/                 # Vue 3 + Vite
    ├── docs/
    │   └── docker-compose.yml    # MySQL, Redis, MinIO, Kafka, ES
    └── README.md                 # 中文
```

### 6.2 模块职责

| 模块 | 职责 |
|------|------|
| `yanban-core` | Harness 循环、ToolRegistry、Session/Message 持久化、流式回调 |
| `yanban-knowledge` | 分片上传、Kafka 消费、Tika 解析、DashScope Embedding、ES 检索 |
| `yanban-paper` | PaperOrchestrator 全流程、MinIO、任务与 SSE 事件 |
| `yanban-mcp` | MCP Client：github + filesystem |
| `yanban-skills` | 扫描 builtin/user；注入 prompt；tools 白名单 |
| `yanban-api` | 聚合 REST、WebSocket/SSE、JWT、文件上传 |
| `yanban-cli` | 调用同一套 service（远程 API 或 embedded） |
| `frontend` | Vue 全站 |

### 6.3 模型 Provider

| Provider | 用途 | 协议 |
|----------|------|------|
| DeepSeek | 默认对话、论文 Agent | OpenAI 兼容 Chat Completions |
| GLM（智谱） | 用户可选 | OpenAI 兼容（按智谱文档适配） |

会话创建时 **快照** provider + model + 参数，避免中途切换导致行为不一致。

### 6.4 数据库：统一 MySQL（不使用 SQLite）

| 方案 | 优点 | 缺点 |
|------|------|------|
| **MySQL（已选）** | 与 KB/论文/用户同栈；JWT 多用户；服务器部署一致；事务与备份成熟 | 本地必须起 MySQL（已有 Docker） |
| SQLite（未选） | 零依赖、单文件便携 | 与 ES/MinIO 并存两套存储故事；多用户/并发写弱；后期上服务器要再迁 |

**结论**：Agent 会话表（`agent_sessions`, `agent_messages`, `agent_tool_runs`）与业务表同一 **MySQL 实例**，便于 JOIN 用户、关联论文 `task_id`。

### 6.5 Harness 规格

- 自主多轮：模型 → tool calls → 执行 → 再请求  
- 默认 `max_steps = 20`，设置页可改  
- 每步审计日志写 MySQL 或日志文件  
- 对话 **默认 RAG**：在组装请求前若未勾选「禁用 KB」，则先 `search_knowledge` 或将检索策略写入 system（实现二选一，产品行为：默认带 KB 上下文）  
- 流式：WebSocket 或 SSE 推送 token 与 tool 事件  

### 6.6 基础设施（Docker）

与 PaiSmart `docs/docker-compose.yaml` 对齐，包含：**MySQL、Redis、MinIO、Kafka、Elasticsearch**。

---

## 7. 功能需求详述

### 7.1 内置知识库（固定功能）

| ID | 功能 | 规格 |
|----|------|------|
| KB-01 | 分片上传 | 断点续传；合并后发 Kafka |
| KB-02 | 异步索引 | 状态：上传中 / 处理中 / 就绪 / 失败；前端轮询或 WS |
| KB-03 | 解析 | Tika：PDF、Word、Markdown；图片走 **OCR 外部工具**（接口抽象 `OcrProvider`） |
| KB-04 | 向量化 | DashScope Embedding → ES |
| KB-05 | 检索 | 混合检索；权限：公开/私有 |
| KB-06 | 管理页 | 上传、列表、删除 |
| KB-07 | 调试页 | 手动 query 看检索结果与得分 |
| KB-08 | Harness 工具 | `search_knowledge`（对话默认走 RAG 时调用） |

**不做**：组织标签树、管理员知识库大盘（可后期加）。

### 7.2 内置论文修改（固定功能）

| ID | 功能 | 规格 |
|----|------|------|
| PA-01 | 完整流水线 | Summary → 内外层润色审查 → PaperReview → Abstract → OpenAlex 文献推荐 |
| PA-02 | 语言 | `targetLanguage`: `zh` \| `en`；UI **一键切换** |
| PA-03 | 任务 API + SSE | 对齐 paper-agent 事件类型（见附录 B） |
| PA-04 | 存储 | MinIO + `./storage` fallback |
| PA-05 | 三步 UI | 上传 → 进度（SSE）→ 结果下载/预览 |
| PA-06 | 对话联动 | 意图「润色论文」→ 返回 **论文页深链** + `taskId`（若已创建）；上传在论文页完成 |
| PA-07 | 质量 | 第一期不专项优化 prompt |

#### 7.2.4 论文任务 vs Harness 会话（#18 推荐方案）

**推荐：分开存储，会话内引用（弱关联）**

| 方案 | 说明 | 选用 |
|------|------|------|
| A. 完全分开 | `paper_tasks` 独立；对话仅跳转论文页 | 太割裂 |
| B. 完全合并 | 论文 SSE 事件塞进 `agent_messages` | 消息表膨胀、协议混杂 |
| **C. 分开 + 引用（推荐）** | `paper_tasks` 独立生命周期；`agent_messages` 可含 `paper_task_id` 与跳转链接；对话说「润色」时创建任务并在会话插入一条 **系统/助手卡片消息** | ✅ |

**理由**：论文是 **长时异步任务**（分钟级、多 SSE 事件），Harness 是 **交互式消息流**；分离清晰，又能在同一会话看到「已为你创建论文任务 #123，点击查看进度」。

### 7.3 统一 Harness 对话

| ID | 功能 | 规格 |
|----|------|------|
| AG-01 | 唯一对话类型 | 无独立「知识库聊天」；KB 通过 RAG + tools |
| AG-02 | 默认 RAG | 可勾选「本次不使用知识库」 |
| AG-03 | Skill 选择 | 无 / builtin `code-review` / `skills/user/*` |
| AG-04 | 流式输出 | SSE 或 WebSocket |
| AG-05 | MCP | 对话中可调用 GitHub tools；选 code-review 时可用 filesystem |
| AG-06 | 会话列表 | 按用户分页；消息历史持久化 MySQL |

### 7.4 Skills

| ID | 功能 | 规格 |
|----|------|------|
| SK-01 | builtin/code-review | 审查清单；`allowed_tools`: filesystem 相关 MCP tools |
| SK-02 | user | 仅 `skills/user/<name>/SKILL.md` + 可选 `skill.yaml` |
| SK-03 | 设置页 | 列表、启用/禁用（不删文件）、**不提供**在线编辑器 |
| SK-04 | 说明文案 | 论文用「论文修改」菜单；KB 用知识库菜单 |

### 7.5 前端页面（第一期全部必选）

| 路由（示例） | 页面 |
|--------------|------|
| `/login`, `/register` | JWT 认证 |
| `/chat` | Harness 对话、Skill 选择、禁用 KB 勾选、流式 |
| `/knowledge-base` | 上传、列表、删除、处理状态 |
| `/knowledge-base/search-debug` | 检索调试 |
| `/paper` | 上传 → 处理 → 结果（三步） |
| `/settings` | DeepSeek/GLM 密钥、默认模型、max_steps、MCP 配置、Skills 列表 |

### 7.6 CLI（与 Web 同期）

| 命令（示例） | 说明 |
|--------------|------|
| `yanban login` | 获取 token |
| `yanban chat` | 交互对话（流式打印） |
| `yanban kb upload/list` | 知识库 |
| `yanban paper status` | 任务状态 |
| `yanban config` | 查看/设置与 Web 共用配置 |

---

## 8. 非功能需求

| 类别 | 要求 |
|------|------|
| 安全 | JWT、密钥加密存储、MCP/文件路径白名单、开源前密钥扫描 |
| 可观测 | Harness step 日志；论文 SSE；KB 任务状态 |
| 测试 | 单元测试：Harness 循环、Skill 加载、Tool 路由、RAG 开关逻辑 |
| 文档 | README 中文；`docs/` 部署与 Docker；`.env.example` |
| 性能 | 论文异步线程池；Kafka 消费并发可配置 |
| 扩展 | 服务器部署：环境变量、反向代理、HTTPS（第二期运维） |

---

## 9. 技术栈

| 层级 | 选型 |
|------|------|
| 语言 | Java 17 |
| 后端 | Spring Boot 3.x、Spring Security JWT、JPA |
| 消息/检索 | Kafka、Elasticsearch 8.x |
| 存储 | MySQL 8、Redis、MinIO |
| 文档 | Apache Tika；OCR 插件化 |
| Embedding | 阿里云 DashScope |
| LLM | DeepSeek + GLM（OpenAI 兼容适配层） |
| MCP | 自研 Client（stdio 为主） |
| 前端 | Vue 3 + TypeScript + Vite + Pinia |
| 构建 | Maven 多模块 + pnpm |
| 部署 | Docker Compose |

---

## 10. API 概要（实现时细化）

### 10.1 认证

- `POST /api/v1/auth/register`, `/login`, `/refresh`

### 10.2 知识库

- `POST /api/v1/upload/chunk`, `merge`, `GET status`  
- `GET/DELETE /api/v1/documents`  
- `POST /api/v1/search`（调试页 + 内部 service）  

### 10.3 论文

- `POST /api/v1/paper/process`  
- `GET /api/v1/paper/events?taskId=`（SSE）  
- `GET /api/v1/paper/tasks/{id}`, `GET download`  

### 10.4 Agent

- `GET/POST /api/v1/agent/sessions`  
- `POST /api/v1/agent/sessions/{id}/messages`（触发 Harness，流式）  
- `GET /api/v1/skills`  

### 10.5 设置

- `GET/PUT /api/v1/settings`（模型、max_steps、MCP、RAG 默认）  

---

## 11. 发布计划

### 11.1 一周冲刺：建议分两阶段

**阶段 A（约 3–4 天）：闭环**

- Docker 基础设施 + MySQL 表  
- JWT + 用户  
- Harness + DeepSeek + 流式 + MySQL 会话  
- KB：单格式上传可简化验证 → 再接通分片与 Kafka  
- 对话默认 RAG + 禁用勾选  
- Vue：`/chat`、`/login`、`/settings`（单模型可先）

**阶段 B（约 3–4 天）：齐套**

- 论文全流程 + 论文三步页 + 对话跳转  
- GLM Provider  
- MCP github + filesystem + code-review Skill  
- KB 管理页 + 检索调试；分片上传与异步状态  
- CLI 基础命令；核心单元测试；中文 README  

若一周时间不足，**优先保证**：Harness + KB 问答 + 论文上传跑通；MCP GitHub 与 GLM 可标为 A 末/B 初。

### 11.2 第二期

- 论文润色质量（prompt、评分、阈值）  
- 服务器部署指南、HTTPS、配置加固  
- Skill 启用/禁用 UI 增强；可选 Skill 自动推荐  
- OCR 提供商扩展；更多 MCP  

---

## 12. 遗留项目迁移指引（参考只读）

| 遗留 | 迁移到 | 方式 |
|------|--------|------|
| PaiSmart 上传/解析/ES | `yanban-knowledge` | **新写**，对照 `UploadService`、`FileProcessingConsumer`、`HybridSearchService` |
| PaiSmart ChatHandler | `yanban-core` Harness | **新写**；RAG 逻辑进 tools + 默认策略 |
| paper-agent Orchestrator | `yanban-paper` | **新写**，对照 `PaperOrchestrator` 与 prompts |
| 前端 | `frontend/` | **新 Vue**，交互对齐原有两项目功能 |

**禁止**：新 Agent 仅 HTTP 代理旧服务而不实现业务。

---

## 13. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 一周 scope 过大 | 阶段 A/B；GitHub MCP / GLM 可略延后 |
| GitHub MCP 配置复杂 | 文档写明 PAT 与 rate limit |
| 双 MCP + 双模型测试矩阵大 | 集成测试覆盖主路径即可 |
| 开源泄露密钥 | CI secret scan；示例配置无真实 key |
| 论文效果差 | 用户已知；第二期专项 |

---

## 14. 术语表

| 术语 | 含义 |
|------|------|
| 研伴 Agent | 本产品中文名 |
| Harness | 多轮 Agent 运行时 |
| 固定功能 | 知识库、论文修改 |
| Skill | `SKILL.md` 行为指引，非 KB/论文实现载体 |
| RAG | 检索增强；对话默认开启 |

---

## 15. 附录 A：决策变更记录

| 版本 | 日期 | 变更 |
|------|------|------|
| v0.1 | 2026-05-24 | 初稿 |
| v0.2 | 2026-05-24 | 产品更名研伴 Agent；全部待决事项闭合；MySQL 统一；JWT；Vue 全功能；MCP GitHub+filesystem；DeepSeek+GLM；一期一周分两阶段 |

---

## 16. 附录 B：paper-agent SSE 事件（继承）

`log`, `summary_ready`, `sections`, `outer_round`, `section_loop_start`, `section_attempt`, `section_polished`, `section_review_done`, `paper_review_done`, `review`, `references_ready`, `complete`, `paused`, `error`

---

## 17. 附录 C：相关路径

路径均相对于仓库根目录 `private_helper_Agent/`。

| 说明 | 路径 |
|------|------|
| 本设计文档 | `memory-bank/design-doucment.md` |
| 技术栈 | `memory-bank/tech-stack.md` |
| 实施计划 | `memory-bank/implementation.md` |
| 架构补充 | `memory-bank/architecture.md` |
| 进度记录 | `memory-bank/progress.md` |
| 新实现（规划） | `private-helper-agent/` |
| PaiSmart 参考 | `PaiSmart-main/README.md` |
| Paper 参考 | `paper-agent/README.md` |

---

*本文档 v0.2 已根据需求访谈更新。开发启动以本文为准；实现偏差需回写版本号。*
