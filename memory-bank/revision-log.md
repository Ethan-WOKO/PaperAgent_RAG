# 修订记录

> 文档作用概述：记录**每次实际代码修改、关键配置调整与修复动作**。它是改动流水，不是计划文档，也不是完整进度汇总。
>
> 用途：记录后续每次代码或关键配置修改。
> 规则：每次修改后，补充日期、修改摘要、涉及文件。

---

## 2026-06-12

### 修订 1：修复 Flyway V7 在 MySQL 上的迁移语法错误

**问题背景**
- 后端启动时，Flyway 在执行 `V7__extend_sys_user_settings_for_glm.sql` 时失败。
- 原因是脚本中使用了 H2 风格的：
  - `ALTER COLUMN ... SET NOT NULL`
- 该写法不兼容当前 MySQL 环境。

**修改内容**
- 将 `glm_model` 字段的非空修改语句改为 MySQL 兼容写法：
  - `MODIFY COLUMN glm_model VARCHAR(128) NOT NULL`

**修改文件**
- `private-helper-agent/yanban-api/src/main/resources/db/migration/V7__extend_sys_user_settings_for_glm.sql`

---

### 修订 2：修复 Skills 加载时只读事务导致的写入失败

**问题背景**
- 访问 Skills 列表时，后端报错：
  - `Connection is read-only. Queries leading to data modification are not allowed`
- 原因是 `SkillsService` 中部分方法使用了只读事务：
  - `@Transactional(readOnly = true)`
- 但这些方法内部会间接调用 `UserSettingsService#getOrCreate(...)`。
- 当用户首次访问、数据库中尚无 `sys_user_settings` 记录时，会触发插入默认设置，从而在只读事务中写库失败。

**修改内容**
- 将以下方法的事务从只读改为普通事务：
  - `listSkills(Long userId)`
  - `resolveEnabledSkill(Long userId, String skillId)`

**修改文件**
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/skills/SkillsService.java`

---

### 修订 3：修复 WebSocket 对话在模型配置缺失时直接断开连接

**问题背景**
- 对话页出现：`WebSocket 连接已关闭`
- 后端日志显示：
  - `com.yanban.core.model.ModelProviderException: DeepSeek apiKey is not configured`
- 原因是：
  - Skill 模式下走 `AgentService.sendMessage(...) -> HarnessEngine`
  - 普通流式模式下走 `chatModelProvider.streamChat(...)`
  - 当 Provider API Key 未配置时，异常会直接冒泡到 WebSocket Handler，导致连接被框架关闭，而不是返回前端可读的 `error` 事件。

**修改内容**
- 为 `ChatWebSocketHandler` 增加统一异常捕获。
- Skill 模式下新增对通用异常的捕获，改为发送 `WsChatEvent.error(...)`。
- 普通流式模式中将 `doOnError(...)` 改为 `onErrorResume(...)`，避免 `blockLast()` 继续向上抛错导致会话被强制关闭。
- 新增错误消息提取方法，将模型配置缺失等异常信息返回给前端。

**修改文件**
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/ws/ChatWebSocketHandler.java`

---

### 修订 4：补充知识库分片上传失败的后端错误日志

**问题背景**
- 前端上传知识库文档时，`POST /api/v1/upload/chunk` 返回 `500`。
- 浏览器侧只能看到通用 `Internal Server Error`，后端控制台没有足够的错误上下文，难以继续定位是 MinIO 写入失败还是数据库持久化失败。

**修改内容**
- 在 `KnowledgeUploadService` 的 `uploadChunk(...)` 和 `mergeChunks(...)` 异常分支中增加 `error` 日志。
- 日志中输出：
  - `userId`
  - `uploadId`
  - `filename`
  - `chunkNumber`
  - `totalChunks`
- 这样再次复现时，IDEA 控制台可直接看到底层异常堆栈。

**修改文件**
- `private-helper-agent/yanban-knowledge/src/main/java/com/yanban/knowledge/service/KnowledgeUploadService.java`

---

### 修订 5：放宽论文上传的 multipart 大小限制

**问题背景**
- 在论文页点击“开始处理”时，后端日志出现：
  - `MaxUploadSizeExceededException: Maximum upload size exceeded`
- 说明当前 Spring multipart 默认大小限制过小，导致 `.docx` 上传在进入业务逻辑前就被拦截。

**修改内容**
- 在 `application.yml` 中增加：
  - `spring.servlet.multipart.max-file-size: 50MB`
  - `spring.servlet.multipart.max-request-size: 50MB`
- 让论文上传与较大的文档分片请求不再因默认限制被直接拒绝。

**修改文件**
- `private-helper-agent/yanban-api/src/main/resources/application.yml`

---

### 修订 6：统一聊天页 WebSocket 普通对话与 Harness/RAG 链路

**问题背景**
- 检索调试页已经可以命中知识库内容，但 `/chat` 页普通对话仍回答“不知道”。
- 原因是普通 WebSocket 路径直接走 `streamChat(...)`，没有真正复用 Harness/RAG 注入链路；只有 Skill 模式才走 `AgentService.sendMessage(...)`。

**修改内容**
- 将 `ChatWebSocketHandler` 的普通聊天路径统一改为走 `AgentService.sendMessage(...)`。
- 第一版接受牺牲 token 级流式，优先保证普通对话与 Skill 对话共用 Harness/RAG 主链路。
- 同步更新 `ChatWebSocketHandlerTest`，改为验证统一后的 chunk + done 事件行为。

**修改文件**
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/ws/ChatWebSocketHandler.java`
- `private-helper-agent/yanban-api/src/test/java/com/yanban/api/ws/ChatWebSocketHandlerTest.java`

---

### 修订 7：关闭“本次不使用知识库”时同时禁用 `search_knowledge` 工具暴露

**问题背景**
- 在普通聊天已统一走 Harness 后，又通过真实手测发现：即使前端勾选“本次不使用知识库”，模型仍可能通过 `search_knowledge` 工具主动检索知识库。
- 原因是此前 `ragDisabled=true` 只关闭了“自动 RAG system context 注入”，但没有把 `search_knowledge` 从工具列表中移除。

**修改内容**
- 在 `HarnessEngine` 中补充 allowed tools 解析逻辑：
  - 当 `ragDisabled=true` 时，从可见工具集合里移除 `search_knowledge`
- 新增测试 `HarnessRagToolDisableTest`，验证关闭知识库时模型看不到 `search_knowledge`。

**修改文件**
- `private-helper-agent/yanban-core/src/main/java/com/yanban/core/harness/HarnessEngine.java`
- `private-helper-agent/yanban-core/src/main/java/com/yanban/core/tool/ToolRegistry.java`
- `private-helper-agent/yanban-core/src/test/java/com/yanban/core/harness/HarnessRagToolDisableTest.java`

---

### 修订 8：第一轮前端收敛 Skill / MCP 中间过程展示

**问题背景**
- 在 `code-review` Skill + filesystem MCP 手测中，聊天页会把 `system` / `tool` 消息当成普通用户消息展示。
- 同时，助手输出中的代码片段与处理中说明混在正文里，阅读体验较差。

**修改内容**
- 在前端聊天页中保留消息角色，不再把所有非 `assistant` 消息一律映射为 `user`。
- 新增“显示中间过程”开关，默认隐藏 `system` / `tool` 历史消息。
- 将 `system` / `tool` 消息改为折叠卡片展示。
- 将带围栏代码块的消息拆分为文本块 + 代码块样式展示。
- 本轮仅改前端展示，不改后端事件分层。

**修改文件**
- `private-helper-agent/frontend/src/views/ChatPage.vue`
- `private-helper-agent/frontend/src/styles.css`

---

### 修订 9：`search_knowledge` 改为通过执行上下文隐式获取当前用户

**问题背景**
- 在聊天页真实手测中，自动 RAG 已经能给出知识库片段，但模型仍可能继续调用 `search_knowledge`，并向用户索要 `userId`。
- 原因是工具 schema 把 `userId` 作为显式必填参数暴露给了模型。

**修改内容**
- 从 `search_knowledge` 工具 schema 中移除显式 `userId` 参数。
- 在工具执行时通过 `ToolExecutionContext` 获取当前用户 ID。
- 若缺少上下文，则直接返回失败，避免误用默认用户值。
- 更新测试，覆盖“隐式用户上下文成功”和“上下文缺失失败”两种情况。

**修改文件**
- `private-helper-agent/yanban-knowledge/src/main/java/com/yanban/knowledge/tool/SearchKnowledgeToolExecutor.java`
- `private-helper-agent/yanban-knowledge/src/test/java/com/yanban/knowledge/tool/SearchKnowledgeToolExecutorTest.java`

---

### 修订 10：收紧 `code-review` Skill 提示词，禁止伪工具调用文本

**问题背景**
- 在 `code-review` Skill 真实手测中，模型没有真正触发 MCP 工具，而是直接输出伪 `<tool_call>filesystem ...` 文本。
- 原因是 Skill prompt 对“应当如何调用工具”约束不够具体，导致模型脑补了错误的调用协议。

**修改内容**
- 在 `SKILL.md` 中明确列出允许使用的真实工具名：
  - `mcp_fs__read_file`
  - `mcp_fs__list_directory`
  - `mcp_fs__read_multiple_files`
- 明确禁止输出伪 XML、伪 `<tool_call>`、伪标签、伪命令。
- 明确要求不要向用户索要 `userId` 等内部参数。
- 要求中间过程不要当成最终回答输出。

**修改文件**
- `private-helper-agent/skills/builtin/code-review/SKILL.md`

---

### 修订 11：为 Skill / MCP 伪工具调用问题补充 Harness 诊断日志

**问题背景**
- 即使收紧了 `code-review` Skill 提示词，模型仍可能输出类似 `<tool_call>mcp_fs__read_file ...` 的伪调用文本。
- 需要先判断：模型是否真的返回了结构化 `tool_calls`，还是根本没有进入 function calling 模式。

**修改内容**
- 在 `HarnessEngine` 中增加诊断日志：
  - 当工具列表非空时，记录当前轮的 provider、可见工具名、finishReason
  - 当没有结构化 `tool_calls` 且正文内容疑似伪工具调用文本时，输出预警日志与内容预览
- 这一步用于辅助定位，不直接改变业务行为。

**修改文件**
- `private-helper-agent/yanban-core/src/main/java/com/yanban/core/harness/HarnessEngine.java`

---

### 修订 12：将 MCP 默认命令调整为 Windows 兼容的 `cmd /c npx`

**问题背景**
- 后端启动日志明确显示：
  - `Skip MCP tool registration for FILESYSTEM: 启动 MCP 子进程失败`
- 这说明 filesystem MCP 根本没有成功注册，模型因此拿不到真实工具定义，只能输出伪工具调用文本。
- 在 Windows + IDEA + Java `ProcessBuilder` 场景下，直接执行 `npx` 容易失败。

**修改内容**
- 将 GitHub / filesystem MCP 默认命令改为：
  - `cmd,/c,npx,-y,...`
- 将允许命令白名单同步改为：
  - `cmd,npx,node`
- 在 `SETUP.md` 中补充 Windows 下推荐使用 `cmd /c npx ...` 的说明。

**修改文件**
- `private-helper-agent/yanban-api/src/main/resources/application-dev.yml`
- `private-helper-agent/.env.example`
- `private-helper-agent/docs/SETUP.md`

---

### 修订 13：新增第二期计划与专项文档

**问题背景**
- 第一阶段 A/B/C 已进入收尾，后续需要按照第二期目标继续推进。
- 原 `implementation.md` 只有“第二期预告”，缺少可执行的阶段拆分、步骤依赖与验证方式。

**修改内容**
- 新增第二期设计补充文档，明确第二期目标、优先级、非目标与风险控制。
- 新增论文质量专项计划，明确论文处理流水线、Prompt 分层、评分维度、OpenAlex 策略与验收方式。
- 在 `implementation.md` 中追加第二期 D/E/F/G 阶段实施计划。
- 新增部署、Skills、OCR 文档草案。
- 更新 `abstract.md`，补充新增 memory-bank 文档说明与第二期推荐阅读顺序。

**修改文件**
- `memory-bank/second-phase-design.md`
- `memory-bank/paper-quality-plan.md`
- `memory-bank/implementation.md`
- `memory-bank/abstract.md`
- `private-helper-agent/docs/DEPLOYMENT.md`
- `private-helper-agent/docs/SKILLS.md`
- `private-helper-agent/docs/OCR.md`

---

### 修订 14：调整第二期主线为论文润色质量专项

**问题背景**
- 当前更希望尽快把论文润色部分做好。
- GitHub MCP 与 CLI 虽然已有代码基础，但不应继续阻塞第二期主线。

**修改内容**
- 将 `implementation.md` 中第二期计划调整为论文润色质量专项优先。
- 将 GitHub MCP 与 CLI 移入“后续拓展池”，不再作为第二期门禁。
- 更新第二期总门禁，使其围绕 docx 解析、Summary、分章润色、Abstract、OpenAlex、结果 docx、论文页体验。
- 更新 `second-phase-design.md` 的目标、优先级、非目标和风险控制。
- 更新 `paper-quality-plan.md`，补充 docx 解析边界、Prompt 变量规范与模型 JSON 输出格式。
- 将 `docs/DEPLOYMENT.md` 标记为后续拓展草案，避免与当前第二期主线冲突。

**修改文件**
- `memory-bank/implementation.md`
- `memory-bank/second-phase-design.md`
- `memory-bank/paper-quality-plan.md`
- `private-helper-agent/docs/DEPLOYMENT.md`

---

### 修订 15：新增论文润色工具与方案调研文档

**问题背景**
- 在正式实现第二期论文润色质量专项前，需要先参考市面常见论文润色 / 学术写作工具与 Reviewer Agent 思路，避免闭门造车。

**修改内容**
- 新增 `paper-polish-research.md`，整理 Grammarly、Paperpal、Writefull、Wordvice AI、QuillBot、DeepL Write、ChatGPT / Claude 等工具形态与可借鉴能力。
- 总结成熟论文润色工具的共性：修改原因、前后对比、学术风格、长文全局理解、阶段化反馈。
- 提出对研伴 Agent 第二期论文润色的落地建议：章节级结构化结果、审查 JSON、评分、OpenAlex、论文页展示增强。
- 更新 `abstract.md`，补充该调研文档的作用与第二期推荐阅读顺序。

**修改文件**
- `memory-bank/paper-polish-research.md`
- `memory-bank/abstract.md`

---

### 修订 16：联网核对并修正论文润色调研文档

**问题背景**
- `paper-polish-research.md` 初版基于已知产品形态整理，未联网核对，存在低估竞品能力、开源部分只有关键词等问题。

**修改内容**
- 使用 curl / Wikipedia / GitHub Search API 对 Grammarly、Paperpal、Writefull、Wordvice AI、QuillBot、DeepL Write 及多个开源论文润色/审查项目进行联网核对。
- 修正对商业产品的低估：补充 Grammarly / Paperpal 已有生成式写作、查重、引用生成；标注 QuillBot 已被 Course Hero 收购。
- 将开源“关键词”替换为真实仓库表（含 star 数）。
- 新增“取舍提醒”与“第 11 节联网核对来源与说明”。

**修改文件**
- `memory-bank/paper-polish-research.md`

---

### 修订 17：抓取开源项目 README/源码并提炼设计精华

**问题背景**
- 需要参考真实开源论文润色/审查项目的 pipeline 与 prompt 结构，为第二期 D 阶段数据结构/Prompt 设计提供依据，但要提炼精华、避免全盘引用。

**修改内容**
- 联网抓取并阅读：Theigrams/Academic-Writing-Assistant（README + app.py + rewriter.py + utils.py）、jiayou20021120-afk/paper-conductor（README + stage_cards.md）、bahayonghang/academic-writing-skills（README）。
- 在 `paper-polish-research.md` 新增第 12 节，按“值得采纳 / 刻意不采纳”提炼精华，并汇总落地决定表。
- 将可落地结论同步到 `paper-quality-plan.md`：公共 system 守则、润色标签式输出、审查 JSON 问题分级 blocker/minor、review 回环硬上限、章节 diff 后端自算、审查结果免责声明。

**修改文件**
- `memory-bank/paper-polish-research.md`
- `memory-bank/paper-quality-plan.md`

---

## 2026-06-16

### 修订 18：论文模块由 docx 整体转向 LaTeX 方向，正式文档全量转化

**问题背景**
- 经多轮设计讨论（记录于 `discussion_about_fix_paper_20260615.md`，共 15 节），第二期论文模块从「docx 论文润色」重大转向「面向 LaTeX 论文的、有据可查的 AI 审稿/改稿 + 文献补全助手」，原 docx 路线作废。需把全部设计结论转化进正式计划与架构文档。

**修改内容**
- 重写 `implementation.md` 第二期 D/E/F/G：D-01~D-07（数据模型/L0 解析/角色识别+检查点/Prompt/文献源/编排状态机/前端范围）、E-01~E-09（PARSE→ROLE→STRUCTURE_CHECK→PROFILE→RETRIEVE→GAP→POLISH→ASSEMBLE，GAP 先 POLISH 后）、F-01~F-05、G 阶段与总门禁全量改为 LaTeX 主线；总览索引更新为 82 步。
- 整体重写 `paper-quality-plan.md` 为 LaTeX 方向（能力分层 L0–L5、阶段链、占位保护、文献地基、gap 分析、L1/L2、Prompt 分层、三件套、验收）。
- 整体重写 `second-phase-design.md` 为 LaTeX 方向（目标/优先级/不做事项/关键产品改进/风险控制）。
- 更新 `paper-polish-research.md`：顶部加方向变更横幅；第 12.3「刻意不采纳」中「绑定 LaTeX 工具链」改为「采纳」，第 12.4 落地决定表步骤编号同步并新增该条。
- 在 `architecture.md` 追加第 18 节：第二期论文模块架构（能力分层/阶段链/L0 文档模型/角色识别/LiteratureService 复用架构/研究画像+gap 数据结构/数据模型/可恢复阶段步编排/交付物两模式）。

**修改文件**
- `memory-bank/implementation.md`
- `memory-bank/paper-quality-plan.md`
- `memory-bank/second-phase-design.md`
- `memory-bank/paper-polish-research.md`
- `memory-bank/architecture.md`
- `memory-bank/discussion_about_fix_paper_20260615.md`（设计讨论纪要，第 10–14 节本轮补全）

---

## 后续维护方式

以后每次修改，请继续按下面格式追加：

```md
## YYYY-MM-DD

### 修订 X：一句话概述

**问题背景**
- ...

**修改内容**
- ...

**修改文件**
- `路径/文件A`
- `路径/文件B`
```
