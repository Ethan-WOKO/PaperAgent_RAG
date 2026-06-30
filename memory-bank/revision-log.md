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

## 2026-06-17

### 修订 19：完成 E-01 LaTeX 解析与文档模型第一版

**问题背景**
- 第二期已进入 E 阶段，需要先实现 L0/PARSE 地基：数据库落点、LaTeX 文档模型、务实解析器与硬性 lint，供后续角色识别、文献地基、gap 分析和润色链路复用。

**修改内容**
- 新增 Flyway `V9__create_latex_paper_tables.sql`，扩展 `paper_tasks` 并新增第二期论文核心表：`literature_cards`、`paper_sections`、`paper_task_analysis`、`paper_task_artifacts`、`paper_task_clarifications`、`paper_task_literature`、`suggestions`、`suggestion_evidence`。
- 扩展 `PaperTask` 实体，新增 `PaperSection` 与 `PaperSectionRepository`，并更新 repository 测试覆盖 `paper_sections`。
- 新增 `com.yanban.paper.latex` 包：`LatexParserService` 与 L0 文档模型 records，支持 preamble 元数据、sections、protectedSpans、floats、cite/ref、外部 `.bib` 与内联 `thebibliography`。
- 实现第一版硬 lint：悬空 cite、断 ref、begin/end 不配平、花括号不配平、重复 bib key。
- 新增 `LatexParserServiceTest`，覆盖章节/元数据/bib/保护 span、内联 bibliography、悬空 cite/ref、环境不配平与重复 bib key。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，5 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；Flyway V1~V9 在 H2 MySQL mode 下可迁移。

**修改文件**
- `private-helper-agent/yanban-api/src/main/resources/db/migration/V9__create_latex_paper_tables.sql`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTask.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperSection.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperSectionRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/*`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/domain/PaperRepositoryTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/latex/LatexParserServiceTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 20：完成 E-02 角色识别与结构确认检查点后端地基

**问题背景**
- E-01 已完成 LaTeX 文档模型与解析地基，E-02 需要在此基础上实现章节角色识别、结构歧义确认、用户可改角色与 WAITING_INPUT 持久化能力，避免 Related Work 等结构误判导致后续 gap/补丁链路“一步错步步错”。

**修改内容**
- 新增 `LatexRoleRecognitionService` 与相关 record：`RoleRecognitionResult`、`RecognizedSectionRole`、`StructureClarificationCandidate`。
- 实现启发式角色识别与结构歧义检测：无显式 Related Work 但引言中存在引用密集/相关工作线索时，生成 `RELATED_WORK_PLACEMENT` 阻塞确认，默认保持在引言中。
- 新增 `PaperTaskClarification` / `PaperTaskClarificationRepository` / `PaperClarificationService`：pending clarification 持久化、阻塞型问题置 `WAITING_INPUT`、回答后恢复 `RUNNING`，并发送 `clarification_needed` / `clarification_resolved` SSE。
- 新增 `PaperSectionService` 与章节/角色 API：列出任务章节、用户覆盖角色（枚举校验，写 `role_source=user`、`role_confidence=1.0`）。
- 扩展 `PaperController`，新增 sections 与 clarifications 端点。
- 更新 repository 测试并新增 `LatexRoleRecognitionServiceTest`。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，7 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 自动装配与 Flyway V1~V9 均通过。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskClarification.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskClarificationRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperSectionRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/RoleRecognitionResult.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/RecognizedSectionRole.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/StructureClarificationCandidate.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/LatexRoleRecognitionService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperClarificationService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperSectionService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperClarificationAnswerRequest.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperClarificationResponse.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperSectionResponse.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperSectionRoleUpdateRequest.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperController.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/domain/PaperRepositoryTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/latex/LatexRoleRecognitionServiceTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 21：完成 E-03 Prompt 资源体系化

**问题背景**
- 第二期论文流水线后续 PROFILE、RETRIEVE、GAP、POLISH 等阶段都依赖稳定 prompt 模板与变量契约。需要将 prompt 从代码中抽离为可维护资源，并在渲染时严格校验必填变量。

**修改内容**
- 新增 `yanban-paper/src/main/resources/prompts/`，包含 9 个 prompt：`role-confirm`、`research-profile`、`section-polish`、`section-review`、`literature-extract`、`gap-analysis`、`relatedwork-gen`、`contribution-gen`、`abstract`。
- Prompt 内容落实前期设计：角色枚举限制、标签式润色输出、review JSON、研究画像 JSON、L3c 文献抽取 JSON、Suggestion JSON、防幻觉铁律、polish/review 分离、占位符保留。
- 新增 `PaperPromptTemplate` 与 `PaperPromptService`：classpath 加载 `prompts/*.md`，提取 `{{变量}}`，渲染时缺必填变量快速报错。
- 新增 `PaperPromptServiceTest`，覆盖模板加载、变量替换、缺变量报错、未知 prompt 报错。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，11 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 资源加载与自动装配未受影响。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/resources/prompts/*.md`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperPromptTemplate.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperPromptService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/service/PaperPromptServiceTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 22：完成 E-04 结构化研究画像服务地基

**问题背景**
- L3 文献地基需要稳定的检索种子与 gap 分析基准。E-04 需基于 E-01 的 LaTeX 文档模型和 E-03 的 prompt 体系，抽取结构化研究画像并持久化到 `paper_task_analysis`。

**修改内容**
- 新增 `PaperTaskAnalysis` / `PaperTaskAnalysisRepository`，覆盖 `paper_task_analysis` 表中的研究画像、概念阶梯、gap 矩阵字段。
- 新增 `ResearchProfileResult` 与 `PaperResearchProfileService`：渲染 `research-profile` prompt，汇总 `LatexDocument` 章节内容，调用模型生成画像，解析 JSON 并写入 `research_profile_json`。
- 增加 degraded 降级策略：模型返回非 JSON/解析失败时不阻断任务，保存 rawText 与 degraded 标记。
- 为保持 `yanban-paper` 与 `yanban-core` 解耦，新增 `PaperModelClient` 轻量接口；在 `yanban-api` 新增 `PaperModelClientConfig`，用现有 `ChatModelProvider` 适配。
- 更新 repository 测试并新增 `PaperResearchProfileServiceTest`，覆盖有效 JSON、非 JSON 降级、生成并保存画像。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，14 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 适配器装配正常。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskAnalysis.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskAnalysisRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/ResearchProfileResult.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperResearchProfileService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperModelClient.java`
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/paper/PaperModelClientConfig.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/domain/PaperRepositoryTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/service/PaperResearchProfileServiceTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 23：完成 E-05 文献检索与卡片地基

**问题背景**
- E-04 已产出结构化研究画像，E-05 需要基于画像进入 L3/RETRIEVE：从真实文献源召回候选，完成去重、缓存、排序、任务关联和概念阶梯落库，为后续 gap 分析提供有据可查的文献地基。

**修改内容**
- 新增 `LiteratureCard` / `LiteratureCardRepository` 与 `PaperTaskLiterature` / `PaperTaskLiteratureRepository`，覆盖全局文献卡片与任务-文献关联。
- 新增 `com.yanban.paper.literature` 包：`LiteratureSource`、`LiteratureCandidate`、`LiteratureSearchResult`、`LiteratureService`、`OpenAlexLiteratureSource`、`ArxivLiteratureSource`、`LiteratureSourceConfig`。
- 实现 OpenAlex 与 arXiv 第一版 provider：OpenAlex 解析 work JSON、authors、concepts、referenced_works、abstract_inverted_index；arXiv 解析 Atom entry、作者、分类、PDF 链接。
- 实现 `LiteratureService`：从 `ResearchProfileResult` 生成多 query，跨 source 查询，按 DOI / arXiv id / OpenAlex id / S2 id / title hash 去重并 upsert，写入 `literature_cards` 与 `paper_task_literature`。
- 实现简版 L3c 与排序：摘要规则抽取 `analysis_json`，标记 `rule-based-l3c-v1`；相关性按关键词、任务、problem/method、引用量、年份轻权排序；选中文献写入 `paper_task_analysis.concept_ladder_json`。
- 更新 repository 测试覆盖 literature 表，新增 OpenAlex/arXiv 解析测试与 `LiteratureServiceTest`，验证真实候选来源、去重缓存、任务关联与概念阶梯写入。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，18 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 上下文、Flyway V1~V9 与自动装配均通过。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/LiteratureCard.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/LiteratureCardRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskLiterature.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskLiteratureRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/literature/*`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/domain/PaperRepositoryTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/literature/*Test.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 24：完成 E-06 gap 分析与建议生成服务地基

**问题背景**
- E-05 已产出真实检索文献卡片与概念阶梯，E-06 需要把 L3 文献地基转化为 L4 可采纳建议，并确保所有建议 evidence 均来自真实已选卡片，避免模型凭空生成引用、实验或贡献表述。

**修改内容**
- 新增 `Suggestion` / `SuggestionRepository` 与 `SuggestionEvidence` / `SuggestionEvidenceRepository` / `SuggestionEvidenceId`，覆盖 `suggestions` 与 `suggestion_evidence`。
- 新增 `GapSuggestionResult` 与 `PaperGapAnalysisService`：读取任务、`paper_task_analysis`、已选 `paper_task_literature`、真实 `literature_cards`，渲染 `gap-analysis` prompt 并调用 `PaperModelClient`。
- 实现 Suggestion JSON 解析与落库，覆盖 track/category/severity/statement/evidence/applicable/patch。
- 实现 grounding 校验：模型 evidence 只能引用当前任务已选真实卡片；不存在或未选中的 card id 会被丢弃。
- 实现诚实闸门第一版：无真实 evidence 的 ADVOCACY 自动转为 CRITIQUE，`applicable=false`，清空 patch，防止无支撑内容进入 LaTeX patch。
- 将生成摘要写入 `paper_task_analysis.gap_matrix_json`；补齐 `PaperTaskAnalysis` 与 `Suggestion` 中 JSON/长文本字段长度，保持测试 DDL 与 Flyway LONGTEXT 设计一致。
- 更新 repository 测试覆盖 suggestions/evidence，新增 `PaperGapAnalysisServiceTest` 覆盖 grounded suggestion、无效 evidence 过滤、无支撑 advocacy 转 critique。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，20 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 上下文、Flyway V1~V9 与新增 repository 扫描均通过。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/Suggestion.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/SuggestionRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/SuggestionEvidence.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/SuggestionEvidenceId.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/SuggestionEvidenceRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskAnalysis.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/GapSuggestionResult.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperGapAnalysisService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/domain/PaperRepositoryTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/service/PaperGapAnalysisServiceTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 25：完成 E-07 占位保护分章润色服务地基

**问题背景**
- E-06 已完成有据可查的建议生成，E-07 需要进入 L1/L2 POLISH：在不破坏 LaTeX 结构、引用、数学与浮动体的前提下分章润色，并通过独立 review 回环控制质量。

**修改内容**
- 新增 `LatexMaskingService` 与 `MaskedLatexText`，支持保护 cite/ref/label/includegraphics、数学片段与重点环境，生成 `[[YANBAN_*_0001]]` 占位符并支持 unmask。
- 新增占位符校验：输出占位符集合必须与输入集合一致，掉占位或新增占位符都会被拒绝。
- 新增静态 lint 第一版：捕获花括号不配平、环境 begin/end 不配平、unmask 后残留占位符。
- 新增 `SectionPolishResult` 与 `PaperSectionPolishService`：执行 mask → `section-polish` → 占位符校验 → unmask → lint → `section-review` 的分章润色链路。
- 实现 retry 策略：掉占位或 lint blocker 会拒绝当次输出并重试；review 低分在未达最大轮次时带审查意见重试；仍失败则保留原章节。
- References 角色默认跳过润色；润色结果写回 `paper_sections.polish_status/review_json/diff_json`，并记录内存型 object key 占位。
- 补齐 `PaperSection.reviewJson/diffJson` 字段长度，保持测试 DDL 与 Flyway LONGTEXT 设计一致。
- 新增 `LatexMaskingServiceTest` 与 `PaperSectionPolishServiceTest`，覆盖 mask/unmask、占位符缺失/新增校验、lint、低分重试、掉占位拒绝与保留原文。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，25 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 上下文与 Flyway V1~V9 均通过。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperSection.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/MaskedLatexText.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/LatexMaskingService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/SectionPolishResult.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperSectionPolishService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/latex/LatexMaskingServiceTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/service/PaperSectionPolishServiceTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 26：完成 E-08 三件套产出服务地基

**问题背景**
- E-07 已完成分章润色与 review 回环，E-08 需要把已有章节状态、真实文献 evidence 与建议结果组装为用户可下载/可审阅的三件套：润色后 `.tex`、`suggested.bib` 与审查报告。

**修改内容**
- 新增 `PaperTaskArtifact` / `PaperTaskArtifactRepository`，覆盖 `paper_task_artifacts` 表，支持按 task/type/version 查询产物。
- 扩展 `PaperStorageService#storeArtifact`，支持保存 `.tex`、`.bib`、Markdown report 等任意论文产物。
- 新增 `PaperAssembleResult` 与 `PaperAssembleService`，聚合 `LatexDocument`、`paper_sections`、`suggestions`、`suggestion_evidence`、`literature_cards` 生成产物。
- 实现基础版输出：不改原文，只生成 `suggested_bib` 与 `review_report`。
- 实现进阶版第一版输出：额外生成 `polished_tex`，按章节顺序拼接可用润色文本/原文，并更新 `paper_tasks.final_object_key`。
- `suggested.bib` 只从真实 evidence 文献卡片生成，自动避让原 bib key；审查报告按章节状态、Suggestion 与 evidence 输出，并附 AI 自查免责声明。
- 进阶版增加静态校验第一版：复用 LaTeX lint，并标记原 bib 缺失的 cite key。
- 更新 repository 测试覆盖 `paper_task_artifacts`，新增 `PaperAssembleServiceTest` 覆盖基础版/进阶版产物、artifact 落库、suggested.bib 真实 evidence 来源、任务完成状态。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，27 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 上下文、Flyway V1~V9 与新增 repository 扫描均通过。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskArtifact.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskArtifactRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperStorageService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperAssembleResult.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperAssembleService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/domain/PaperRepositoryTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/service/PaperAssembleServiceTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 27：完成 E-09 论文质量样例集与评价记录

**问题背景**
- E-08 已完成三件套组装地基，E-09 需要形成可重复对比的论文质量验收方式，覆盖中文、英文和无 `.bib`/内联 bibliography 场景，并把人工评价与文献真实性核对纳入手测清单。

**修改内容**
- 新增中文 LaTeX 样例 `zh-rag-polish`：`main.tex` + `refs.bib`，覆盖中文章节、cite/ref/figure/math 与 RAG 论文主题。
- 新增英文 LaTeX 样例 `en-literature-gap`：`main.tex` + `refs.bib`，覆盖 Abstract/Introduction/Method/Discussion/Conclusion、equation/ref/math 与 grounded writing 主题。
- 新增无 `.bib` 内联 bibliography 样例 `inline-bibliography`，覆盖 `thebibliography` / `bibitem` 解析。
- 新增 `PaperQualitySampleTest`，自动校验三类样例可解析、引用/参考文献存在、受保护元素被识别且无 BLOCKER lint。
- 新增 `memory-bank/paper-quality-evaluation.md`，记录中文与英文样例的原文、三件套结果记录方式、人工评价维度与推荐文献真实可溯源核对要求。
- 新增 `private-helper-agent/docs/paper-quality-samples/README.md`，说明样例位置、验收顺序与手动端到端记录要求。
- 更新 `memory-bank/test-checklist.md`，增加阶段 E 论文质量专项测试清单。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests；API 上下文与 Flyway V1~V9 均通过。

**修改文件**
- `private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/zh-rag-polish/main.tex`
- `private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/zh-rag-polish/refs.bib`
- `private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/en-literature-gap/main.tex`
- `private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/en-literature-gap/refs.bib`
- `private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/inline-bibliography/main.tex`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/quality/PaperQualitySampleTest.java`
- `private-helper-agent/docs/paper-quality-samples/README.md`
- `memory-bank/paper-quality-evaluation.md`
- `memory-bank/test-checklist.md`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 28：完成阶段 E 门禁核验与 F-01 阶段进度增强

**问题背景**
- E-01~E-09 已完成服务地基与离线样例集，需要先记录阶段 E 门禁核验结果，再进入阶段 F 的论文页体验增强。F-01 要求用户能看清处理阶段、当前章节和尝试次数，并保留调试日志。

**修改内容**
- 在 `memory-bank/test-checklist.md` 增加阶段 E 门禁核验记录，逐项标记 G-E1~G-E7 的验证证据与后续真编译/真实模型手测说明。
- 扩展 `PaperSseEvent`，新增可选进度字段：`currentSection`、`totalSections`、`sectionTitle`、`attempt`、`maxAttempts`、`progressPercent`；旧事件可继续只携带基础字段。
- 更新当前骨架 `PaperOrchestrator` 的关键事件发布，为摘要、章节、审查、文献、完成等阶段补充进度百分比、章节编号和尝试次数。
- 论文页新增阶段链、整体进度条、章节进度、尝试次数展示。
- 论文页将 SSE type/stage 映射为友好中文文案，同时保留折叠的原始 SSE JSON 日志用于调试。
- 更新 `architecture.md`，记录 SSE schema 扩展与前端展示策略。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
- `pnpm build`（frontend）：通过；仅 Vite chunk size warning。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperSseEvent.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperOrchestrator.java`
- `private-helper-agent/frontend/src/api/paper.ts`
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/architecture.md`
- `memory-bank/test-checklist.md`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 29：完成 F-02 结构确认检查点交互 UI

**问题背景**
- E-02 已有结构确认后端地基，F-02 需要把角色识别歧义变成用户可操作的前端交互：批量问题、默认保持原样、阻塞/提示区分、提交后续跑，并允许用户手动修改章节角色。

**修改内容**
- 在 `frontend/src/api/paper.ts` 新增 clarification 与 section API 类型/函数：查询确认问题、提交答案、查询章节、更新章节角色。
- 论文页接入 `clarification_needed` / `clarification_resolved` 事件后刷新确认问题与章节角色。
- 新增“结构确认”面板：展示问题 message/type/相关章节序号、选项、阻塞/提示标识。
- 默认选中“保持原样”或后端 `defaultOption`，降低误重构风险。
- 支持“全部保持原样”批量提交、单项提交，以及非阻塞问题跳过。
- 新增“章节角色”面板：展示章节 title、role、confidence/source，并允许下拉修改章节角色。
- 更新 `memory-bank/test-checklist.md`，补充 F-02 手测清单。

**验证**
- `pnpm build`（frontend）：通过；仅 Vite chunk size warning。

**修改文件**
- `private-helper-agent/frontend/src/api/paper.ts`
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/test-checklist.md`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 30：完成 F-03 在线预览与逐条采纳第一版

**问题背景**
- F-03 需要在论文页展示章节 diff、提供基础版/进阶版入口，并支持逐条采纳建议；同时需要把 A 类真实可采纳内容与 B 类骨架/批评内容区分展示，避免把无证据内容直接写入论文。

**修改内容**
- 新增 `PaperSuggestionResponse`、`PaperSuggestionStatusUpdateRequest`、`PaperArtifactResponse`。
- 新增 `PaperPreviewService`，支持按任务查询 suggestions、计算 evidence 数量、生成 A/B 诚实分级、更新 suggestion 状态、查询 artifact 元数据。
- `PaperController` 新增：
  - `GET /api/v1/paper/tasks/{taskId}/suggestions`
  - `POST /api/v1/paper/tasks/{taskId}/suggestions/{suggestionId}/status`
  - `GET /api/v1/paper/tasks/{taskId}/artifacts`
- 扩展 `PaperSectionResponse` 返回 `reviewJson/diffJson`，用于章节源码级 JSON 预览。
- 前端论文页新增“在线预览与逐条采纳”面板：
  - 基础版：只推荐，不改原文。
  - 进阶版：改原文 + 补 cite 的预览入口。
  - 章节 diff/review 源码 JSON 预览。
  - A/B 诚实分级展示。
  - A 类建议可勾选采纳；B 类只展示骨架/批评，不能直接采纳。
  - 支持拒绝建议。
  - 展示 suggested.bib / polished.tex artifact 版本数量。
- 更新 `architecture.md` 与 `test-checklist.md`，记录预览/采纳接口和手测清单。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
- `pnpm build`（frontend）：通过；仅 Vite chunk size warning。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperPreviewService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperSuggestionResponse.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperSuggestionStatusUpdateRequest.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperArtifactResponse.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperController.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperSectionResponse.java`
- `private-helper-agent/frontend/src/api/paper.ts`
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/architecture.md`
- `memory-bank/test-checklist.md`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 31：完成 F-04 审查报告与 suggested.bib 展示

**问题背景**
- F-04 需要在论文页展示有据可查的审查建议与推荐文献，让用户能看到每条 suggestion 的 severity、statement、真实引用来源，并对 suggested.bib 对应文献进行人工核验。

**修改内容**
- 扩展 `PaperSuggestionResponse`，新增 evidence card 详情列表，包含标题、作者、年份、venue、DOI、arXiv id、OpenAlex id、S2 id、URL、PDF、引用数。
- `PaperPreviewService` 从 `suggestion_evidence` 与 `literature_cards` 聚合真实 evidence cards，保证展示来源仍来自已落库文献卡片。
- 论文页新增“审查报告与 suggested.bib”面板：
  - 展示 severity、category、track、statement。
  - 展示真实 evidence card 链接。
  - 无 evidence 时明确提示“禁止直接写入论文”。
  - 展示推荐文献列表，含标题、作者、年份、venue、DOI/URL/PDF/OpenAlex 等可回溯信息。
  - 展示 AI 辅助免责声明。
- 更新 `architecture.md` 与 `test-checklist.md`，记录 F-04 展示策略与手测项。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
- `pnpm build`（frontend）：通过；仅 Vite chunk size warning。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperSuggestionResponse.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperPreviewService.java`
- `private-helper-agent/frontend/src/api/paper.ts`
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/architecture.md`
- `memory-bank/test-checklist.md`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 32：纠正论文上传入口为 LaTeX-only

**问题背景**
- 当前第二期论文模块已明确改为 LaTeX-only，但前端上传区仍沿用早期 `.docx` 文案与单文件上传逻辑，后端创建任务接口也存在 docx 校验残留。

**修改内容**
- `PaperProcessRequest` 新增 `mainTex` 与可选 `bibFile`，保留旧 `file` 字段做短期兼容。
- `PaperTaskService` 优先读取 `mainTex`，校验 `.tex` 必填、`.bib` 可选；上传主 tex 与 bib 文件。
- 创建任务时设置 `inputFormat=LATEX`、`mainEntry=<tex 文件名>`、`mode=LATEX_ONLY/LATEX_BIB`。
- 引入 `PaperTaskArtifactRepository`，将源文件登记为 `source_tex` / `source_bib` artifact。
- 前端论文页上传 UI 改为 `.tex` 主文件必填、`.bib` 可选，FormData 字段改为 `mainTex` / `bibFile`。
- 更新 `PaperControllerIntegrationTest`，覆盖 LaTeX 上传成功、bib 可选上传、非法扩展名拒绝。
- 更新 `progress.md` 记录本次纠偏。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
- `pnpm build`（frontend，通过 `cmd.exe /c pnpm build`）：通过；仅 Vite chunk size warning。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperProcessRequest.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperTaskService.java`
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `private-helper-agent/yanban-api/src/test/java/com/yanban/api/paper/PaperControllerIntegrationTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 33：修复论文任务秒完成但未真实处理的问题

**问题背景**
- 用户浏览器手测发现上传 `.tex/.bib` 后约 5 秒即完成，速度明显不符合真实论文处理。
- 排查确认 `PaperOrchestrator` 仍运行早期最小骨架流程，只发布模拟 SSE、写入占位 round，并复制原文件为 final，没有真正解析 LaTeX。

**修改内容**
- 重写 `PaperOrchestrator#runTask` 的第一版真实 LaTeX 编排入口：
  - 读取任务主 `.tex` object key。
  - 读取已登记的 `source_bib` artifact。
  - 调用 `LatexParserService` 解析 LaTeX 文档。
  - 调用 `LatexRoleRecognitionService` 识别章节角色与结构确认候选。
  - 将真实章节保存到 `paper_sections`。
  - 为每个原始章节保存 `section_original` artifact。
  - 存在阻塞确认项时进入 `WAITING_INPUT` 并发布 `clarification_needed`，不再假完成。
  - 无阻塞确认项时调用 `PaperAssembleService` 生成基础版 `suggested_bib` 与 `review_report`。
- 扩展 `PaperSectionRepository` 增加按任务删除章节的方法，供重新解析/重跑时清理旧章节使用。
- 增强 `PaperStorageService`：
  - MinIO 写入后继续保留本地备份。
  - MinIO 读取失败或返回空时回退本地文件，避免 mocked MinIO 和本地开发场景读不到源文件。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
- `pnpm build`（frontend，通过 `cmd.exe /c pnpm build`）：通过；仅 Vite chunk size warning。
- `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperOrchestrator.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperSectionRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperStorageService.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 34：修复结构确认后 RUNNING 但不继续推进

**问题背景**
- 用户反馈结构确认提交后页面显示 `RUNNING`，但任务实际不再推进。
- 排查确认：阻塞确认触发时异步编排线程已结束；确认全部回答后只更新状态，没有重新启动编排线程。

**修改内容**
- `PaperClarificationService`：最后一个 `PENDING` clarification 被回答后，将任务切回 `RUNNING/STRUCTURE_CHECK`，并在事务提交后调用 `PaperOrchestrator#startTask(taskId)` 自动续跑。
- `PaperOrchestrator`：续跑时检查任务已有 clarification：
  - 没有历史 clarification 时，按识别结果创建 pending clarification。
  - 仍有 pending clarification 时，保持等待输入并返回。
  - 所有 clarification 已回答时，不再重复创建问题，继续进入后续基础组装。
- 新增 `clarification_resolved` 进度事件，便于前端看到结构确认完成后任务继续处理。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
- `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperClarificationService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperOrchestrator.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 35：修复结构确认续跑时 round 唯一键冲突

**问题背景**
- 结构确认后自动续跑重新进入 `PARSE` 阶段，再次插入 `paper_task_rounds(task_id, round_number, stage)=(4,1,PARSE)`。
- 数据库唯一索引 `paper_task_rounds.uk_paper_task_rounds_task_round` 拒绝重复插入，导致任务失败并把 SQL 异常直接展示到前端。

**修改内容**
- `PaperTaskRoundRepository` 新增 `findByTaskIdAndRoundNumberAndStage`。
- `PaperTaskRound` 新增 `setStatus/setInputText/setOutputText/setNotes`。
- `PaperOrchestrator#persistRound` 改为 upsert：
  - 已存在相同任务/轮次/阶段 round 时更新内容。
  - 不存在时才创建新 round。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
- `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskRound.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTaskRoundRepository.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperOrchestrator.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-17

### 修订 36：修复第二阶段完成后结果文件不可下载

**问题背景**
- 第二阶段基础版完成后会生成 `suggested_bib` / `review_report` artifacts，但不一定生成 `paper_tasks.final_object_key`。
- 前端“下载结果文件”按钮只依赖 `finalObjectKey`，导致页面显示“尚未生成”且按钮禁用。

**修改内容**
- `PaperTaskService#downloadResult`：
  - 有 `finalObjectKey` 时继续下载最终 tex。
  - 无 `finalObjectKey` 但存在 `polished_tex` / `suggested_bib` / `review_report` artifacts 时，动态打包 zip 返回。
- `PaperTaskService` 新增下载文件名与 content-type 判断：
  - artifacts 三件套返回 `<source>-artifacts.zip` / `application/zip`。
  - final tex 返回 tex 文件名 / `application/x-tex`。
- `PaperController#download` 使用服务层返回的文件名与 content-type，不再硬编码 docx 类型。
- 前端论文页：
  - 下载按钮启用条件扩展为 `finalObjectKey` 或可下载 artifacts 存在。
  - 结果文件文案在 artifacts 存在时显示“已生成 N 个产物，可下载 zip”。

**验证**
- `mvn -pl yanban-paper test`（Windows JDK/Maven）：通过，30 tests。
- `pnpm build`：通过，仅 Vite chunk size warning。
- `mvn test` 全项目默认测试（Windows JDK/Maven）：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperTaskService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperController.java`
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

---

## 2026-06-22

### 修订 37：串联真实论文处理主流程，修复空推荐/空建议产物

**问题背景**
- 用户下载第二阶段产物后，`suggested.bib` 为空，`review-report.md` 显示所有章节 `PENDING`，并提示 `No suggestions generated`。
- 原因是当前编排只跑到 LaTeX 解析、章节识别、结构确认和基础组装，没有执行真实文献检索、Gap 分析和分章润色。

**修改内容**
- `PaperOrchestrator`：
  - 在结构确认后继续执行 `PROFILE`、`RETRIEVE`、`GAP_ANALYSIS`、`POLISH`、高级 `ASSEMBLE`。
  - 发布对应 SSE 进度事件，并持久化阶段 round。
  - 最终调用 `assemble(..., true)` 生成完整三件套。
- `PaperResearchProfileService`：
  - 模型调用失败或返回空画像时，从论文标题和章节标题生成降级研究画像，保证检索 query 不为空。
- `LiteratureService`：
  - 单个检索源异常时跳过该源，避免 OpenAlex/arXiv 某一路网络失败导致整个任务失败。
- `PaperGapAnalysisService`：
  - 模型无建议但已有真实 selected literature cards 时，生成保守兜底建议，并绑定真实 evidence card。
  - 保证报告不再是空建议，同时 `suggested.bib` 可从 grounded ADVOCACY 建议生成。
- `PaperSectionPolishService`：
  - 分章润色结果改为写入真实 artifact 存储，不再使用不可读取的 `memory://` 占位。
- `PaperTaskService` / `PaperPage.vue`：
  - 下载结果改为 artifacts 优先打包 zip，避免高级流程存在 `finalObjectKey` 时只能下载单个 tex。
  - 前端结果文件文案同步显示 zip 产物数量。

**验证**
- `mvn -pl yanban-paper test`：通过，30 tests。
- `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
- `pnpm build`：通过，仅 Vite chunk size warning。
- `mvn test`：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperOrchestrator.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperResearchProfileService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/literature/LiteratureService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperGapAnalysisService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperSectionPolishService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperTaskService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/service/PaperSectionPolishServiceTest.java`
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`
- `memory-bank/test-checklist.md`

---

## 2026-06-22

### 修订 38：接入 LLM 章节角色复核并修复 References 误判

**问题背景**
- 用户指出设计中已明确要求“启发式 → LLM 复核 → 用户可改”，但实际产物中 `Subproblem 3: SDP-Based Reference Beampattern Shaping` 被误判为 `REFERENCES` 并跳过润色。
- 排查发现当前 `LatexRoleRecognitionService` 只沿用 parser heuristic，`role-confirm.md` prompt 尚未接入主链路。

**修改内容**
- `LatexParserService`：
  - References 判断从 `contains("reference")` 改为严格标题匹配。
  - 避免 `reference beampattern` / `reference signal` / `reference model` 等正文标题被误判为参考文献章节。
- `LatexRoleRecognitionService`：
  - 注入 `PaperPromptService`、`PaperModelClient`、`ObjectMapper`。
  - 可用模型时调用 `role-confirm` prompt 做章节角色复核。
  - 模型不可用、调用失败、JSON 无效时自动降级 heuristic。
  - 输出角色限定在 `LatexSectionRole` 枚举内。
  - 增加 References 安全闸门：LLM 返回 `REFERENCES` 时，仍要求标题严格匹配 References/Bibliography/参考文献才采纳。
- `LiteratureService`：
  - 在 `concept_ladder_json` 写入检索诊断：queries、sourceAttempts、sourceFailures、rawCandidateCount、uniqueCandidateCount、selectedCount。
- `PaperAssembleService`：
  - `review-report.md` 增加 `Retrieval Diagnostics`，避免 `suggested.bib` 为空时无从定位。
- 测试：
  - 新增 `referenceBeampatternIsNotReferencesSection` 回归测试。

**验证**
- `mvn -pl yanban-paper test`：通过，31 tests。
- `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
- `mvn test`：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/LatexParserService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/LatexRoleRecognitionService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/literature/LiteratureService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperAssembleService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/latex/LatexRoleRecognitionServiceTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/service/PaperAssembleServiceTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

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

## 2026-06-24

### 修订 39：修复 LaTeX 组装重复结束标记并增强文献 query / bib 推荐兜底

**问题背景**
- 浏览器手测下载的真实 `polished.tex` 出现重复 `\\end{document}`，阻断编译。
- `suggested.bib` 只包含少量 Gap evidence，selected 候选较多时仍可能推荐过少。
- 文献检索 query 由代码拼接，缺少面向搜索策略的 LLM 规划。

**修改内容**
- 新增 `literature-search-query.md` prompt 和 `LiteratureQueryPlanner`，实现 LLM query 规划 + 规则 fallback。
- `LiteratureService` 接入 query planner，使用任务标题、目标语言与研究画像生成检索 query。
- `PaperAssembleService` 拼接章节前剥离尾部 `\\end{document}`，保证最终 tex 只保留一个结束标记。
- `suggested.bib` 在 Gap evidence 不足时补入 selected 高相关真实文献卡片；`review-report.md` 增加补充候选区，明确弱推荐与人工核验要求。
- `PaperAssembleServiceTest` 增加重复 `\\end{document}` 回归断言。

**验证**
- `mvn -pl yanban-paper test`：通过，31 tests。
- `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
- `mvn test`：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/resources/prompts/literature-search-query.md`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/literature/LiteratureQueryPlanner.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/literature/LiteratureService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperAssembleService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/literature/LiteratureServiceTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/service/PaperAssembleServiceTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

## 2026-06-24

### 修订 40：保留 LaTeX front matter、禁止模型改写结构命令、过滤低相关补充 bib

**问题背景**
- 复核用户真实产物 `IEEE_TAES_regular_template_latex_v6-artifacts (4)` 发现：
  - `polished.tex` 虽只剩 1 个 `\\end{document}`，但丢失了标题、作者、摘要和关键词。
  - 模型改写了 `\\label{}` / `\\ref{}` 名称，产生潜在 undefined reference。
  - `suggested.bib` 中 supplemental 弱推荐混入 CP2K、6G、O-RAN 等明显低相关文献。

**修改内容**
- `LatexDocument` 增加 `frontMatter` 字段。
- `LatexParserService` 提取 `\\begin{document}` 到第一个章节之间的 front matter，并用于标题/作者/关键词元数据抽取。
- `PaperAssembleService` 组装最终 tex 时保留 front matter。
- `PaperSectionPolishService` 增加结构命令不变量校验，禁止模型新增、删除或改写 `cite/ref/label/includegraphics/bibliography` 等结构命令。
- `PaperAssembleService` 增加 `REF_WITHOUT_LABEL` lint，检测最终 tex 的未定义引用目标。
- supplemental bib 增加相关性最低阈值，避免低分弱相关 selected 候选进入推荐 bib。
- 增加/更新回归测试：front matter 保留、结构命令变化拒绝、重复 `\\end{document}` 保持 1 次。

**验证**
- `mvn -pl yanban-paper test`：通过，32 tests。
- `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
- `mvn test`：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/LatexDocument.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/LatexParserService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperAssembleService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperSectionPolishService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/service/PaperAssembleServiceTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/service/PaperSectionPolishServiceTest.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

## 2026-06-24

### 修订 41：论文页结果中心 Tabs 化与右侧滚动布局优化

**问题背景**
- 论文页右侧结果下载区域承载结构确认、章节角色、预览采纳、审查报告等大量内容。
- 当章节较多时，右侧区域会被拉成很长一列，视觉拥挤且不利于定位结果下载按钮。

**修改内容**
- 将右侧“结果下载”重构为“结果中心”。
- 顶部新增下载状态条，集中显示产物状态、原始文件与下载按钮。
- 使用 Tabs 拆分：总览、结构、章节、预览、报告。
- Tab 内容区固定高度并内部滚动，避免长章节列表撑高整页。
- 调整三栏比例：左侧 `l:7`、中间 `l:10`、右侧 `l:7`。
- 新增结果中心相关样式与移动端响应式规则。

**验证**
- `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅 Vite chunk size warning。

**修改文件**
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

## 2026-06-24

### 修订 42：新增 retrieved-literature 检索诊断产物

**问题背景**
- `suggested.bib` 只展示 Gap 模型最终挑选的少量 evidence，不能反映检索系统 raw/unique/selected 三层质量。
- 当前需要先评估文献检索系统本身，为后续对话中直接检索文献打基础。

**修改内容**
- `LiteratureService` 新增检索诊断 artifact 输出：
  - `retrieved-literature.json`：完整保留 queries、sourceAttempts、rawCandidates、uniqueCandidates、rankedCandidates、selectedCandidates。
  - `retrieved-literature.md`：人工可读摘要，便于快速查看 query 与排序候选。
- rawCandidates 保留 API 原始返回，不去重；unique/ranked/selected 仍展示去重和排序链路。
- `PaperTaskService` 下载 zip 支持打包 `retrieved_literature_json` 与 `retrieved_literature_md`。
- 前端论文页结果中心同步统计 retrieved-literature 诊断文件。
- 更新 `LiteratureServiceTest` 覆盖诊断 artifacts 写入。

**验证**
- `mvn -pl yanban-paper test`：通过，32 tests。
- `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
- `mvn test`：通过，28 tests。
- `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅 Vite chunk size warning。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/literature/LiteratureService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperTaskService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/literature/LiteratureServiceTest.java`
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

### 修订 43：修复论文任务首次上传后首轮异步启动易 FAILED

**问题背景**
- 用户反馈第一次上传 `.tex` 与 `.bib` 后开始处理会显示 `FAILED`，第二次重新上传同样文件才开始真正执行。
- 该问题符合异步任务首启时序/存储读取瞬时失败特征：第一次启动如果源文件或 bib 读取失败会直接进入 FAILED，第二次因资源已热身而成功。

**修改内容**
- `PaperOrchestrator#startTask` 增加 taskId 级运行去重，防止同一任务被重复异步启动。
- 新增 `readStorageWithRetry`：源文件读取支持 3 次重试，并输出带 taskId/objectKey/attempt 的 warn 日志。
- 主 `.tex` 使用重试读取；重试失败才进入 FAILED，并给出明确 source_tex 错误。
- `.bib` 使用重试读取；重试失败时不再终止整篇任务，而是跳过 bib、发布 `bib_read_skipped` 事件并继续执行。

**验证**
- `mvn -pl yanban-paper test`：通过，32 tests。
- `mvn -pl yanban-api -am -Dtest=PaperControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，2 tests。
- `mvn test`：通过，28 tests。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperOrchestrator.java`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

## 2026-06-29

### 修订 44：记录 LaTeX 文献推荐链路阶段性修复与剩余质量问题

**问题背景**
- 连续测试 `IEEE_TAES_regular_template_latex_v6-artifacts (12) ~ (20)` 暴露出文献推荐链路多个问题：推荐数量固定、Introduction Analysis fallback、LLM JSON 截断、query 过宽、`suggested.bib` 与上传 `.bib` 去重不生效、FDA-MIMO jamming 方向过度集中、泛化文献混入。

**已完成修复摘要**
- 将文献推荐数量从单值扩展为范围：`literatureMinCount` / `literatureCount`。
- Introduction Analysis 从单次大 JSON 调用改为多次 API 调用：plan / slots / audit 分别生成后合并。
- 报告新增 Introduction Analysis Diagnostics，能定位 fallback 原因、slot 数量、raw preview。
- `retrieved-literature.json/md` 保留 raw / unique / ranked / selected 多层诊断。
- `suggested.bib` 与 `suggested-novel.bib` 分离，后者用于排除上传 bib 已有文献。
- 修复 `.bib` parser 对嵌套花括号字段解析不完整的问题，已有文献去重开始生效。
- 增加 balanced selection 与弱泛化过滤，降低单一主题占满与泛化候选入选概率。

**当前验证结论**
- `artifacts (20)` 中 Introduction Analysis 已正常成功：`generatedBy=introduction-analysis-v1`、`degraded=false`、`Raw LLM slot count=14`、`Fallback-added slot count=0`。
- 上传 `.bib` 解析数为 35，已识别 4 条 already-present，`suggested-novel.bib` 输出 22 条新增推荐。
- 推荐结果比早期版本明显更均衡，但仍有少量 DFRC/通信/OTFS/泛 MIMO/泛波形类弱相关文献，需要后续精排。

**后续待处理**
- 增加最终 LLM relevance filter / slot-aware rerank，降低泛化文献比例。
- 在报告中输出每篇推荐对应的 citation slot、supportStrength、useAs。
- 增强多版本/近重复去重。

**修改文件（本阶段涉及）**
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperIntroductionAnalysisService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/literature/LiteratureService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/literature/LiteratureRerankService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperAssembleService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/latex/LatexParserService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/domain/PaperTask.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperProcessRequest.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/web/PaperTaskResponse.java`
- `private-helper-agent/yanban-api/src/main/resources/db/migration/V11__add_literature_min_count_to_paper_tasks.sql`
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `private-helper-agent/frontend/src/api/paper.ts`
- `private-helper-agent/yanban-paper/src/main/resources/prompts/introduction-analysis.md`
- `private-helper-agent/yanban-paper/src/main/resources/prompts/literature-rerank.md`
- `memory-bank/progress.md`
- `memory-bank/revision-log.md`

## 2026-06-29

### 修订 论文润色 Critic/Repairer 与日常文献检索第一版

**问题背景**
- 对 `reference/IEEE_TAES_regular_template_latex_v6.tex` 与润色产物对比后，发现当前润色整体改善语言，但仍可能出现：重复扩写、概念漂移、将 manifold synthesis 误写为 projection、以及新增结构/贡献表达过度等问题。
- 现有 `section-review` 主要独立审查润色后文本，缺少与原文的逐项对照；日常聊天中也只有论文润色跳转意图，尚不能直接触发文献检索能力。

**修改内容**
- 新增 `section-repair.md`，用于在 Critic 发现问题后做保守最小修复。
- 将 `section-review.md` 改为 original-aware critic：输入原文、润色文和 diff summary，重点检查原意保持、unsupported content、LaTeX 结构改变、重复扩写和过度改写。
- 收紧 `section-polish.md` 与 `introduction-polish.md`：默认保守润色，禁止无依据新增 contribution bullets、变量、模型、公式和 bullet list。
- 改造 `PaperSectionPolishService`：润色后先做占位/结构/lint 校验，再做原文对照 review；若 review 未通过，调用 repair prompt 做最小修复并二次 review。
- 新增 `AdHocLiteratureSearchService`，复用公开 `LiteratureSource` 做日常轻量文献检索、去重、年份过滤、相关性排序和 BibTeX 生成。
- 新增 `search_literature` 工具，供 Harness/function calling 在普通对话中主动检索公开文献。
- 新增 `ConversationIntentRouterService`，统一处理论文润色跳转与文献检索意图；支持 `/literature`、中文关键词和轻量语义触发。

**修改文件**
- `private-helper-agent/yanban-paper/src/main/resources/prompts/section-polish.md`
- `private-helper-agent/yanban-paper/src/main/resources/prompts/introduction-polish.md`
- `private-helper-agent/yanban-paper/src/main/resources/prompts/section-review.md`
- `private-helper-agent/yanban-paper/src/main/resources/prompts/section-repair.md`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperSectionPolishService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/literature/AdHocLiteratureSearchService.java`
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/agent/SearchLiteratureToolExecutor.java`
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/agent/ConversationIntentRouterService.java`
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/agent/AgentService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/literature/AdHocLiteratureSearchServiceTest.java`
- `private-helper-agent/yanban-api/src/test/java/com/yanban/api/agent/ConversationIntentRouterServiceTest.java`

### 修订 45：补齐 Critic/Repairer 验证、低置信文献检索确认与 H2 迁移兼容

**问题背景**
- 在接入原文对照 Critic/Repairer 与日常文献检索后，需要验证真实 REST 会话路径是否会触发 `/literature` 检索并持久化消息。
- 首次全量 API 测试暴露 `V10__extend_literature_cards_and_task_count.sql` 中多列 `MODIFY COLUMN` 写法不兼容 H2 MySQL mode。
- 日常聊天中的弱语义文献检索意图如果直接执行，可能对普通问题产生误触发。

**修改内容**
- 将 V10 迁移中的多列 `MODIFY COLUMN` 拆为多条单列 `ALTER TABLE ... MODIFY COLUMN ...`，兼容 H2 测试与 MySQL。
- `ConversationIntentRouterService` 增加低置信确认策略：显式 `/literature` 与强文献关键词直接检索；弱语义命中只返回确认提示。
- 移除裸 `references` 英文关键词触发，降低普通编程/语言问题误判。
- 为 Agent REST 路径补充集成测试：`/literature FDA-MIMO jamming 1篇 bibtex` 直接返回检索结果和 BibTeX，不调用普通模型聊天。
- 补充路由单元测试：弱语义触发只提示确认；泛化 `Java references` 不触发文献检索。
- `PaperTaskService` 下载 artifacts 时补充 `source_bib` 支持，并尽量使用 metadata 中原始文件名。

**验证**
- `cmd.exe /c "cd private-helper-agent && mvn -pl yanban-api -am -Dtest=ConversationIntentRouterServiceTest,AgentControllerIntegrationTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test"`：通过，9 tests。
- `cmd.exe /c "cd private-helper-agent && mvn -pl yanban-api -am test"`：通过，33 tests；Flyway V1--V11 在 H2 中全部迁移成功。
- `cmd.exe /c "cd private-helper-agent && mvn -pl yanban-paper test"`：通过，33 tests。

**修改文件**
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/agent/ConversationIntentRouterService.java`
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/agent/SearchLiteratureToolExecutor.java`
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/agent/AgentService.java`
- `private-helper-agent/yanban-api/src/main/resources/db/migration/V10__extend_literature_cards_and_task_count.sql`
- `private-helper-agent/yanban-api/src/test/java/com/yanban/api/agent/ConversationIntentRouterServiceTest.java`
- `private-helper-agent/yanban-api/src/test/java/com/yanban/api/agent/AgentControllerIntegrationTest.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/literature/AdHocLiteratureSearchService.java`
- `private-helper-agent/yanban-paper/src/main/java/com/yanban/paper/service/PaperTaskService.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/literature/AdHocLiteratureSearchServiceTest.java`
- `private-helper-agent/yanban-paper/src/test/java/com/yanban/paper/literature/LiteratureServiceTest.java`
- `memory-bank/revision-log.md`
- `memory-bank/progress.md`
- `memory-bank/test-checklist.md`
- `memory-bank/architecture.md`

### 修订 46：恢复已执行 V10 迁移并隔离 H2 测试迁移

**问题背景**
- 真实 MySQL 启动时报 Flyway checksum mismatch：`V10` 已在本地库执行，后续直接修改版本化迁移文件导致 `flyway_schema_history` 中的旧 checksum 与本地文件不一致。
- 自动化测试未暴露该问题，是因为测试使用全新的 H2 内存库，每次从零执行迁移，没有历史 checksum 可对比。

**修改内容**
- 恢复 `src/main/resources/db/migration/V10__extend_literature_cards_and_task_count.sql` 到原始 MySQL 多列 `MODIFY COLUMN` 写法，保证已执行过 V10 的真实库 checksum 不变。
- 新增 `src/test/resources/db/migration-h2/`，复制一套测试专用迁移，其中 V10 使用 H2 兼容的单列 `MODIFY COLUMN` 写法。
- 在 `yanban-api/src/test/resources/application.properties` 中将测试 Flyway location 指向 `classpath:db/migration-h2`。
- 形成规则：生产/主资源中的已发布版本化迁移不可再修改；测试数据库兼容差异放在测试资源或新增后续迁移中处理。

**验证**
- `cmd.exe /c "cd private-helper-agent && mvn -pl yanban-api -am -Dtest=AgentControllerIntegrationTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test"`：通过，5 tests。
- `cmd.exe /c "cd private-helper-agent && mvn -pl yanban-api -am test"`：通过，33 tests。

**修改文件**
- `private-helper-agent/yanban-api/src/main/resources/db/migration/V10__extend_literature_cards_and_task_count.sql`
- `private-helper-agent/yanban-api/src/test/resources/application.properties`
- `private-helper-agent/yanban-api/src/test/resources/db/migration-h2/*.sql`
- `memory-bank/revision-log.md`

### 修订 47：前端深色高级 Research Workspace 第一版

**问题背景**
- 现有前端功能已基本打通，但视觉观感偏普通，缺少高级 AI research workspace 的产品感，影响后续继续开发体验。
- 用户提供多张深色/浅色 UI 参考图，决定先统一做深色高级版，后续再补浅色一键切换细节。

**修改内容**
- 将默认主题改为深色。
- 重构 `AppLayout`：从顶部导航改为左侧固定工作台导航 + 顶部任务操作栏。
- 顶部栏加入 New Task、Upload Paper、Search Literature、Agent Mode 和主题切换入口。
- 聊天页改为三栏研究工作台：左侧会话列表，中间 Chat workspace，右侧 Research Agent 活动面板。
- 聊天页新增视觉化 Agent Plan、Tools & Execution、Execution Trace 壳，为后续真实 Agent 任务拆解功能预留承载区域。
- 全局 CSS 追加 ScholarAI premium workspace refresh 设计系统：深色 navy 背景、玻璃卡片、紫蓝/cyan accent、圆角卡片、状态 badge、paper workflow 卡片与滚动条风格。
- 保留现有浅色主题变量和一键切换入口，后续可继续细化浅色版。

**验证**
- `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅保留 Vite chunk size warning。

**修改文件**
- `private-helper-agent/frontend/src/components/AppLayout.vue`
- `private-helper-agent/frontend/src/views/ChatPage.vue`
- `private-helper-agent/frontend/src/App.vue`
- `private-helper-agent/frontend/src/composables/useTheme.ts`
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/revision-log.md`

### 修订 48：Chat 工作台布局细节打磨

**问题背景**
- 深色/高级工作台第一版整体方向可用，但 Chat 页仍存在细节不协调：输入框没有稳定贴底、会话列表距离左侧导航偏远、右侧 Agent 面板视觉重量偏强、顶部工具区略松散。

**修改内容**
- 将 Chat 主卡片内容区改为 `grid-template-rows: auto minmax(0, 1fr) auto`，让消息区独立滚动，composer 稳定贴在底部。
- Chat 页取消 `max-width + auto margin` 居中，改为在 workspace 内自然铺满，使 Recent Conversations 更靠近左侧导航。
- 主聊天区消息和输入框宽度上限从 980px 提升到 1120px，消息气泡最大宽度提升到 940px，提升长回答阅读舒适度。
- 会话列表宽度从 260px 收到 252px，item 高度、hint、metadata 间距变轻，active 会话保留左侧强调线。
- 顶部工具区变紧凑，checkbox 做成 pill 风格，Skill 下拉缩窄。
- 右侧 Agent 面板宽度从 330px 收到 318px，卡片阴影、间距和 item padding 降低，减少抢视觉中心的问题。
- 输入框 textarea 从 minRows 3 调为 2，整体更贴近底部操作区。

**验证**
- `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅保留 Vite chunk size warning。

**修改文件**
- `private-helper-agent/frontend/src/views/ChatPage.vue`
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/revision-log.md`

**修订 48 补充**
- 三栏 Chat 布局的响应式折叠阈值从 1280px 上调到 1560px，避免 1366/1440 等常见屏幕在左侧全局导航存在时出现横向拥挤或溢出。

### 修订 49：修复 Chat 用户消息未贴右的问题

**问题背景**
- Chat UI 中用户消息虽然使用蓝色气泡和右侧头像，但整组消息没有真正贴到聊天区右侧，视觉上停留在中间偏左，不符合常见聊天界面习惯。

**原因分析**
- 早期样式中 `.message-row--user` 保留了 `justify-content: flex-end`。
- 后续新版样式又加入 `flex-direction: row-reverse`，两者叠加后在 row-reverse 主轴下反而会把用户消息组推向左侧/中间。

**修改内容**
- 在最终覆盖样式中增加消息对齐保护规则：
  - `.chat-messages` 明确 `align-items: stretch`。
  - `.message-row` 明确 `width: 100%` 和 `align-self: stretch`。
  - 助手/系统/工具消息保持 `row + flex-start`。
  - 用户消息使用 `row-reverse + flex-start`，确保头像在最右、气泡在头像左侧且整组靠右。
  - 用户气泡最大宽度收窄到 `min(68%, 760px)`，避免短消息横跨过宽。

**验证**
- `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅保留 Vite chunk size warning。

**修改文件**
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/revision-log.md`

### 修订 50：Chat 模型选择、会话重命名/删除与自动取名

**问题背景**
- Chat 页面输入框附近缺少会话级模型选择，用户只能去 Settings 修改默认模型，不适合即时切换。
- 会话列表显示模型名不符合用户预期，应改为显示最近更新时间。
- 会话不支持重命名和删除，长期使用会导致列表难以管理。
- 新会话默认标题没有语义，需要在用户发送第一条消息后自动生成简洁标题。

**修改内容**
- 前端 Chat：
  - 在 composer 顶部加入 Model 选择器，读取 Settings 中的 deepseek/glm 模型配置。
  - 新会话使用当前选择的模型创建；已有会话切换模型时同步更新会话模型快照。
  - 会话列表不再展示 `provider · model`，改为展示“最近更新 x分钟前/小时前”。
  - 会话 item 增加 `⋯` 菜单，支持重命名和删除。
  - 增加重命名弹窗，限制 40 字并禁止空标题。
  - 删除当前会话后自动切换到列表中下一个会话；无会话时清空消息区。
- 后端 API：
  - 新增 `PATCH /api/v1/agent/sessions/{sessionId}` 更新标题、模型、maxSteps、RAG 设置。
  - 新增 `DELETE /api/v1/agent/sessions/{sessionId}` 删除会话，并删除对应消息。
  - 会话列表排序从 createdAt 改为 updatedAt 倒序。
  - 发送消息后 touch 会话更新时间。
  - 当会话标题仍为默认“新会话/研伴对话”且是第一条用户消息时，调用聚合 `chatModelProvider` 生成简洁标题；失败时回退到首条消息截断标题。
- 测试：
  - `AgentControllerIntegrationTest` 增加自动取名测试。
  - `AgentControllerIntegrationTest` 增加重命名、切换模型、删除测试。

**验证**
- `cd private-helper-agent && cmd.exe /c mvn -pl yanban-api -am -Dtest=AgentControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，7 tests。
- `cd private-helper-agent && cmd.exe /c mvn -pl yanban-api -am test`：通过，35 tests。
- `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅 Vite chunk size warning。

**修改文件**
- `private-helper-agent/yanban-core/src/main/java/com/yanban/core/agent/AgentSession.java`
- `private-helper-agent/yanban-core/src/main/java/com/yanban/core/agent/AgentSessionRepository.java`
- `private-helper-agent/yanban-core/src/main/java/com/yanban/core/agent/AgentMessageRepository.java`
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/agent/AgentController.java`
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/agent/AgentService.java`
- `private-helper-agent/yanban-api/src/main/java/com/yanban/api/agent/UpdateSessionRequest.java`
- `private-helper-agent/yanban-api/src/test/java/com/yanban/api/agent/AgentControllerIntegrationTest.java`
- `private-helper-agent/frontend/src/api/agent.ts`
- `private-helper-agent/frontend/src/views/ChatPage.vue`
- `private-helper-agent/frontend/src/styles.css`

### 修订 51：工作台 Focus 控件与面板折叠动画

**问题背景**
- 左下角用户区只有登出图标，用户不容易理解其含义。
- Chat 工作台左右面板固定显示，占用横向空间；用户希望 Recent Conversations 和 Research Agent 都可隐藏，隐藏后中间对话自动扩展。
- 顶部 Research Copilot 区域占用垂直空间，需要支持隐藏，隐藏后下方工作区向上扩展。

**修改内容**
- `AppLayout`：
  - 左下角登出按钮从纯 `↗` 改为 `Sign out ↗`。
  - 顶部 Research Copilot / route header 增加中下部 `⌃` 收起按钮。
  - 顶部收起后显示居中的 `⌄` 恢复按钮。
  - 顶部折叠状态写入 `localStorage`：`yanban.app.topbarCollapsed`。
- `ChatPage`：
  - Recent Conversations 标题区增加 `+` 和 `⟨`，`⟨` 用于隐藏左侧会话列表。
  - 左侧隐藏后显示 `☰` 浮动恢复按钮。
  - Research Agent 标题区增加 `Live` 和 `⟩`，`⟩` 用于隐藏右侧 Agent 面板。
  - 右侧隐藏后显示 `✦` 浮动恢复按钮。
  - 左右面板折叠状态写入 `localStorage`：
    - `yanban.chat.sessionsCollapsed`
    - `yanban.chat.agentCollapsed`
- CSS：
  - 使用 `grid-template-columns` transition 实现中间聊天区自动扩展动画。
  - 面板隐藏时增加 opacity、translate、scale、blur 过渡，避免硬切。
  - 顶部 header 隐藏时压缩高度并淡出，下方 workspace 自动上移。

**验证**
- `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅保留 Vite chunk size warning。

**修改文件**
- `private-helper-agent/frontend/src/components/AppLayout.vue`
- `private-helper-agent/frontend/src/views/ChatPage.vue`
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/revision-log.md`

### 修订 52：折叠状态下 Chat 主体动态扩展

**问题背景**
- 左右面板折叠后，外层 grid 已释放空间，但 Chat 内部仍受 `width: min(1120px, 100%)` 等 max-width 限制，导致左右仍出现大空白，composer 和消息区没有真正扩大。

**修改内容**
- 使用 CSS 变量统一控制 Chat 内容宽度：
  - `--chat-content-width`
  - `--assistant-bubble-width`
  - `--user-bubble-width`
  - `--chat-content-align-margin`
- 根据折叠状态动态调整：
  - 左右都展开：内容 `min(1120px, 100%)`，保持原工作台阅读宽度。
  - 仅折叠左侧或右侧：内容 `min(1280px, 100%)`，单侧扩展。
  - 左右都折叠：内容 `min(1480px, calc(100% - 80px))`，进入接近全宽 Focus 状态。
- intro、messages、composer 全部使用统一动态宽度，保证输入框也随布局扩展。
- assistant/user 气泡宽度跟随状态动态扩大，但用户气泡仍保持比助手窄。
- 单侧折叠时通过 margin 让内容向被释放的一侧扩展，双侧折叠时保持居中。

**验证**
- `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅保留 Vite chunk size warning。

**修改文件**
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/revision-log.md`

### 修订 53：修复 Chat/Paper 页面滚轮失效

**问题背景**
- 为了让 Chat composer 固定底部，之前将 `.app-frame`、`.app-content-shell` 等全局容器设置为 `height: 100vh` / `overflow: hidden`。
- 该规则影响了非 Chat 页面，导致 Paper 页面不能正常向下滚动。
- Chat 页面在部分折叠/动态宽度状态下也可能出现消息列表没有被正确约束为独立滚动区，导致鼠标滚轮看起来失效或底部消息被 composer 遮住。

**修改内容**
- `AppLayout` 为 Chat 路由增加 `.app-workspace--chat` class。
- 全站恢复默认文档滚动：
  - `.app-frame` 改回 `height: auto`、`overflow-y: visible`。
  - `.app-workspace`、`.app-content-shell` 默认允许 visible overflow。
- 仅在 `.app-workspace--chat` 下启用 viewport-locked 工作台布局：
  - Chat 页面高度固定为 viewport。
  - Chat 内部 `chat-messages` 作为独立滚动区。
  - `chat-workspace-panel` 和 Naive Card content 使用明确 grid 行约束。
  - composer 保持底部区域，不参与页面滚动。
- 给 Chat 消息区增加底部 padding，降低 composer 遮挡底部消息的风险。

**验证**
- `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅保留 Vite chunk size warning。

**修改文件**
- `private-helper-agent/frontend/src/components/AppLayout.vue`
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/revision-log.md`

### 修订 54：Chat 消息轨道固定宽度优化

**问题背景**
- 左右面板折叠后，主工作区扩展正常，但消息气泡也随之变得过宽，用户/AI 头像和气泡视觉距离过大，阅读不够紧凑。

**修改内容**
- 保留 intro / composer 的动态扩展，但将消息区单独设为固定阅读轨道。
- 新增 CSS 变量：
  - `--chat-message-lane-width`
  - `--assistant-fixed-bubble-width`
  - `--user-fixed-bubble-width`
- 根据默认、单侧折叠、双侧折叠三个状态设置消息轨道上限。
- assistant / user 气泡改用固定最大宽度，避免在 Focus 状态下被拉得过宽。

**验证**
- `cd private-helper-agent/frontend && cmd.exe /c pnpm build`：通过，仅保留 Vite chunk size warning。

**修改文件**
- `private-helper-agent/frontend/src/styles.css`
- `memory-bank/revision-log.md`
