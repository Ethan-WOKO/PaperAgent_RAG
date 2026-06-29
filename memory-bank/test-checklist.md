# 阶段 B 手动测试清单

> 文档作用概述：记录**手动测试顺序、门禁验证步骤、已测结果与待补测试项**。当页面或命令的真实验收结果发生变化时，优先更新本文件。
>
> 目的：用于按顺序补齐阶段 B 门禁手测记录。
> 当前偏好：后端在 IDEA 里运行，主要通过前端页面验证。

---

## 0. 启动前检查

### 后端
- 在 IDEA 中启动 `yanban-api`
- 访问：
  - `http://localhost:8080/actuator/health`
- 预期：
  - 返回 `{"status":"UP"}`

### 中间件
需确认以下服务可用：
- MySQL
- Redis
- Elasticsearch
- Kafka
- MinIO

### 前端
- 进入 `private-helper-agent/frontend`
- 执行：
  - `pnpm dev`
- 打开：
  - `http://localhost:5173`

---

## 1. G-B2：知识库页手测

### 页面
- `/knowledge-base`

### 步骤
1. 登录账号
2. 上传一个内容明确的文档（建议带唯一关键词）
3. 观察文档状态从：
   - `PROCESSING`
   - 到 `READY`
4. 在列表页确认文档可见
5. 进入 `/knowledge-base/search-debug`
6. 搜索刚才的唯一关键词

### 预期
- 文档上传成功
- 文档状态变为 `READY`
- 检索调试页能命中该文档内容

### 当前状态
- ✅ 已通过真实手测
- 已确认：上传成功、检索命中成功

---

## 2. G-B3：对话 + RAG 手测

### 页面
- `/chat`

### 步骤
1. 新建会话
2. 默认开启知识库时，提问知识库中明确存在的问题
3. 观察回答是否引用知识库内容
4. 打开“本次不使用知识库”
5. 再次提问同样问题
6. 对比回答差异

### 预期
- 默认模式下应能利用知识库回答
- 关闭知识库后回答应明显变差或无法命中

### 当前状态
- ❌ 当前未通过
- 已确认问题：
  - 检索调试页可以命中
  - 但聊天页普通 WebSocket 对话路径未真正接入 RAG
  - 现象是回答“不知道”或声称无法访问知识库
  - 已通过真实页面对话复现

---

## 3. G-B4：论文页手测

### 页面
- `/paper`

### 步骤
1. 准备一个 `.docx` 文件
2. 上传并创建任务
3. 观察任务状态变化与 SSE 事件流
4. 等待任务完成
5. 点击下载结果文档

### 预期
- 能创建任务
- 能看到状态变化 / SSE 事件
- 最终可下载 docx

### F-05 真实产物生成与下载复测

- [ ] 重启后端 `yanban-api` 与前端。
- [ ] 重新上传 `.tex` 主文件和可选 `.bib`。
- [ ] 如触发结构确认，完成所有确认项。
- [ ] 观察 SSE/阶段状态依次进入：`PROFILE`、`RETRIEVE`、`GAP_ANALYSIS`、`POLISH`、`ASSEMBLE`、`COMPLETE`。
- [ ] 任务完成后点击“下载结果文件”。
- [ ] 解压 zip，确认至少包含：
  - [ ] `polished.tex`
  - [ ] `suggested.bib`
  - [ ] `review-report.md`
- [ ] 打开 `review-report.md`，确认不再只有 `No suggestions generated`。
- [ ] 打开 `suggested.bib`，确认在检索服务正常联网且返回候选时包含真实推荐文献条目。
- [ ] 如 `suggested.bib` 仍为空，收集后端日志中的 `RETRIEVE` 阶段结果数量、任务 id、页面 artifacts 列表。

## 说明
- 当前论文链路是 skeleton-first 实现
- 下载到的 docx 主要用于验证链路，不代表最终高质量润色结果

### 当前状态
- 🟡 待复测
- 已复现问题：
  - 上传论文时曾触发 `MaxUploadSizeExceededException`
- 当前说明：
  - 后端已放宽 multipart 限制到 `50MB`
  - 需在重启后端后重新做一轮真实上传验证

---

## 4. G-B8：CLI 最短手测

### 建议命令
1. `yanban login`
2. `yanban kb list`
3. `yanban paper status <taskId>`

### 预期
- `login` 成功
- `kb list` 能看到与 Web 同一批文档
- `paper status` 能返回任务状态

### 当前状态
- 🟡 部分通过
- 已确认：
  - CLI `login` 已成功
- 未完成：
  - 仍需继续验证：
    - `kb list`
    - `paper status <taskId>`
  - 当前 CLI 仍缺真正终端命令化入口，现阶段更接近“Java 主类可运行 + 逻辑可测”

---

## 5. 后续补测项

### G-B5：GLM
- 需要真实 `GLM_API_KEY`
- 验证切换 glm 后新会话可正常对话
- 当前状态：
  - ✅ 已通过真实手测
  - 说明：切换默认 Provider 后，需在**新建会话**中验证；旧会话仍保留旧快照

### G-B6：MCP
- 需要本机可用 Node / npx
- 验证：
  - GitHub MCP tool discovery / 调用
  - filesystem MCP 白名单生效
- 当前状态：
  - 🟡 部分通过
  - 已确认：filesystem MCP 可用
  - 未确认：GitHub MCP 真实联调结果仍未补

### G-B7：Skill
- 需要 filesystem MCP 可用
- 验证 `code-review` Skill 的真实效果
- 当前状态：
  - 🟡 部分通过
  - 已确认：`code-review` Skill 可触发 filesystem MCP 读取项目内文件
  - 已观察到的体验问题：
    - 对话中会直接展示 Skill prompt / 处理性说明
    - 大模型读取的代码片段会在聊天内容中直接回显一部分
    - 当前交互体验不理想，后续需单独优化展示与中间过程暴露问题

### G-B10：遗留隔离
- 需要在可见 `.git` 元数据的环境做最终核验

---

## 6. 建议测试顺序

按当前效率，建议这样执行：

1. `/knowledge-base`
2. `/knowledge-base/search-debug`
3. `/chat`
4. `/paper`
5. CLI
6. GLM / MCP / Skill

---

## 7. 最近已确认结果汇总

- ✅ 知识库上传成功
- ✅ 检索调试页命中成功
- ❌ `/chat` 普通 WebSocket 对话未真正接入 RAG
- ✅ GLM 新会话对话成功
- ✅ CLI `login` 成功
- ✅ filesystem MCP 可用
- 🟡 `code-review` Skill 可工作，但当前会暴露处理性提示词与部分读取到的代码片段，体验待优化
- 🟡 论文上传限制问题已修复配置，待重启后复测

## 8. 每轮测试建议记录内容

建议每次手测后记录：
- 测试日期
- 页面 / 命令
- 输入内容
- 实际结果
- 是否通过
- 若失败，附错误信息或后端日志

---

# 阶段 E：论文质量专项测试清单

## E-09：论文质量样例集与评价记录

### 样例路径
- 中文样例：`private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/zh-rag-polish/`
- 英文样例：`private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/en-literature-gap/`
- 无 `.bib` 内联 bibliography 样例：`private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/inline-bibliography/`
- 评价记录：`memory-bank/paper-quality-evaluation.md`

### 自动化检查
1. 执行 `mvn -pl yanban-paper test`。
2. 确认 `PaperQualitySampleTest` 通过。
3. 核对中文、英文、内联 bibliography 样例均无 BLOCKER lint。

### 手动端到端检查
1. 在论文页上传中文样例，执行到 ASSEMBLE。
2. 下载并保存三件套：`polished.tex`、`suggested.bib`、`review_report.md`。
3. 核对：
   - 无残留 `[[YANBAN_*]]` 占位符。
   - 原始 cite/ref/label/math/figure/equation 未被破坏。
   - `suggested.bib` 每条推荐文献均可回溯到真实检索卡片。
   - 审查报告区分可直接采纳建议与需要作者补证据的批评。
4. 对英文样例重复以上流程。
5. 将 task id、artifact object key、模型 provider、检索源和人工评分补入 `paper-quality-evaluation.md`。

### 当前状态
- ✅ 样例集与离线评价记录已建立。
- ✅ 自动化解析验收已覆盖中文、英文、内联 bibliography 三类样例。
- 🟡 真实前端/模型端到端产物与人工评分待复测补充。

---

## 阶段 E 门禁核验记录（2026-06-17）

| # | 门禁项 | 核验结果 | 证据/说明 |
|---|---|---|---|
| G-E1 | LaTeX 解析 | ✅ 通过离线自动化 | `PaperQualitySampleTest`、`LatexParserServiceTest` 覆盖章节、受保护元素、外部 bib、内联 bibliography、硬 lint。 |
| G-E2 | 角色识别 + 检查点 | ✅ 通过服务地基自动化 | `LatexRoleRecognitionServiceTest`、clarification 服务/接口地基已实现；真实前端交互属于 F-02。 |
| G-E3 | 研究画像 | ✅ 通过服务地基自动化 | `PaperResearchProfileServiceTest` 覆盖结构化画像、JSON 降级与落库。 |
| G-E4 | 文献地基 | ✅ 通过服务地基自动化 | `OpenAlexLiteratureSourceTest`、`ArxivLiteratureSourceTest`、`LiteratureServiceTest` 覆盖真实源解析、去重、任务关联与概念阶梯。 |
| G-E5 | gap 分析 | ✅ 通过服务地基自动化 | `PaperGapAnalysisServiceTest` 覆盖 evidence 只来自当前任务已选真实卡片，以及无 evidence ADVOCACY 转 CRITIQUE。 |
| G-E6 | 分章润色 | ✅ 通过服务地基自动化 | `LatexMaskingServiceTest`、`PaperSectionPolishServiceTest` 覆盖占位保护、掉占位拒绝、lint 与 review retry。 |
| G-E7 | 三件套 | ✅ 通过服务地基自动化，🟡 真编译待手测 | `PaperAssembleServiceTest` 覆盖 basic/advanced artifact、suggested.bib evidence 来源与任务完成状态；VSCode/latexmk 真编译后置手测。 |

### 本轮自动化命令
- `mvn -pl yanban-paper test`：通过，30 tests。
- `mvn test`：E-09 时已通过，28 tests；F-01 后继续复跑。
- `pnpm build`：通过；仅提示 chunk size warning。

### 阶段 E 门禁结论
- ✅ 阶段 E 后端/离线验收门禁通过，可进入阶段 F。
- 🟡 真实模型、真实前端端到端产物下载、VSCode 真编译与人工评分仍按 F/G 阶段手测补充。

---

## F-02：结构确认检查点交互 UI 手测清单

### 页面
- `/paper?taskId=<taskId>`

### 自动化/构建检查
- `pnpm build`：应通过。

### 手动步骤
1. 准备一个会触发结构歧义的论文任务，或在后端测试数据中创建 `PENDING` clarification。
2. 打开论文页并连接 SSE。
3. 收到 `clarification_needed` 后，右侧应出现“结构确认”面板。
4. 核对：
   - 问题列表展示 message、type、相关章节序号。
   - 阻塞类显示“必须答”，提示类显示“可跳过”。
   - 默认选项高亮为“保持原样”或后端 `defaultOption`。
   - “全部保持原样”可批量提交。
   - 非阻塞问题可单独跳过。
5. 核对“章节角色”面板：
   - 展示章节标题、角色、confidence/source。
   - 修改下拉角色后能调用后端接口并刷新。

### 当前状态
- ✅ 前端 UI 与 API 调用已接入。
- ✅ 前端构建通过。
- 🟡 真实歧义任务端到端触发与批量确认仍待浏览器手测。

---

## F-03：在线预览 + 逐条采纳手测清单

### 页面
- `/paper?taskId=<taskId>`

### 自动化/构建检查
- `mvn -pl yanban-paper test`：应通过。
- `pnpm build`：应通过。
- `mvn test`：应通过。

### 手动步骤
1. 准备一个已产生 section `diff_json/review_json` 与 suggestions 的论文任务。
2. 打开论文页，查看“在线预览与逐条采纳”面板。
3. 切换两个入口：
   - 基础版：只推荐，不改原文。
   - 进阶版：改原文 + 补 cite。
4. 核对章节 diff/review 是否以源码 JSON 预览方式展示。
5. 核对建议分级：
   - A 类：有 evidence、applicable、ADVOCACY，可勾选采纳。
   - B 类：仅骨架/批评展示，不允许直接采纳。
6. 勾选 A 类建议，确认后端状态变为 `ACCEPTED`。
7. 取消勾选或点击拒绝，确认状态变为 `PROPOSED` / `REJECTED`。
8. 若已有 artifact，核对 suggested.bib / polished.tex 版本数量展示。

### 当前状态
- ✅ 后端 suggestions/artifacts 查询接口已接入。
- ✅ suggestion 状态更新接口已接入。
- ✅ 前端在线预览、A/B 诚实分级与逐条采纳 UI 已接入。
- ✅ 自动化测试与前端构建通过。
- 🟡 “按已采纳 patch 重新组装改后 tex”的强触发按钮仍待后续增强；当前 E-08 assemble 服务地基已具备，F-03 先完成预览与采纳状态闭环。

---

## F-04：审查报告 + suggested.bib 展示手测清单

### 页面
- `/paper?taskId=<taskId>`

### 自动化/构建检查
- `mvn -pl yanban-paper test`：应通过。
- `pnpm build`：应通过。
- `mvn test`：应通过。

### 手动步骤
1. 准备一个已有 suggestions 且挂载 evidence cards 的论文任务。
2. 打开论文页，查看“审查报告与 suggested.bib”面板。
3. 核对每条审查建议：
   - 展示 severity、category、track、statement。
   - 若无 evidence，应显示“禁止直接写入论文”。
   - 若有 evidence，应展示真实 card 链接。
4. 核对推荐文献列表：
   - 展示标题、作者、年份、venue。
   - DOI、URL、PDF 可点击。
   - OpenAlex id 等可回溯信息可见。
5. 核对底部免责声明可见。

### 当前状态
- ✅ 后端 suggestion response 已携带 evidence card 详情。
- ✅ 前端已展示审查报告、真实 evidence 链接与推荐文献列表。
- ✅ 自动化测试与前端构建通过。
- 🟡 suggested.bib 原文内容预览/复制按钮可在后续增强；当前已展示由真实 evidence cards 支撑的文献列表。

---

## F-05：浏览器手测与缺陷收敛清单

### 页面
- `/paper`

### 自动化/构建检查
- `mvn -pl yanban-paper test`：已通过，30 tests。
- `pnpm build`：已通过；仅 Vite chunk size warning。
- `mvn test`：已通过，28 tests。

### 上传入口重点核对
1. 打开论文页，确认上传区显示 LaTeX 论文任务，不再要求上传 `.docx`。
2. `.tex` 主文件为必填；未选择 `.tex` 时提交应提示“请先选择 .tex 主文件”。
3. `.bib` 文件为可选；可只上传 `.tex` 创建任务。
4. 同时上传 `.tex` + `.bib` 时，任务应创建成功。
5. 尝试把主文件换成 `.pdf` / `.docx`，应被后端拒绝。
6. 创建成功后，任务信息应显示 `inputFormat=LATEX`，上传 bib 时 `mode=LATEX_BIB`。

### 阶段 F 页面联调核对
1. 阶段进度条与阶段链是否正常显示。
2. SSE 日志是否持续刷新，折叠原始 JSON 是否可展开。
3. 结构确认面板是否能显示、默认保持原样、提交/跳过。
4. 章节角色列表是否能显示并修改角色。
5. 在线预览与逐条采纳面板是否能展示 suggestions、diff/review、artifact 摘要。
6. 审查报告与 recommended literature 是否展示真实 evidence cards 与免责声明。

### 当前状态
- ✅ 上传入口已纠偏为 `.tex` 必填 + `.bib` 可选。
- ✅ 自动化测试与前端构建通过。
- 🟡 真实浏览器端到端手测待执行。

### F-05 缺陷 1 复测：上传后秒完成但未真实处理
1. 上传 `.tex` + 可选 `.bib` 后观察 SSE：
   - 应出现“开始读取 LaTeX 源文件”。
   - 应出现“LaTeX 解析完成：识别到 N 个章节”。
   - 应出现“章节角色识别完成”。
2. 打开“章节角色”面板：
   - 应能看到从真实 `.tex` 解析出来的章节，而不是固定 Introduction 占位。
3. 若论文触发结构歧义：
   - 任务应进入 `WAITING_INPUT`。
   - 页面应显示结构确认问题。
   - 不应直接显示假完成。
4. 若无阻塞结构问题：
   - 任务可完成基础版组装。
   - artifacts 中应出现 `suggested_bib` / `review_report`。
5. 后端日志中不应再出现旧骨架语义：
   - `original-docx`
   - `summary draft`
   - `openalex placeholder`
   - `论文任务已完成（当前为最小骨架流程）`

### F-05 缺陷 1 当前状态
- ✅ 后端已接入真实 LaTeX 解析、章节落库、角色识别、结构确认与基础组装。
- ✅ 自动化测试与前端构建通过。
- 🟡 研究画像、文献检索、Gap 分析、分章模型润色尚未全量串入异步编排，后续继续收敛。

## 2026-06-24：F-05 产物质量复测补充

- 重新上传 `reference/IEEE_TAES_regular_template_latex_v6.tex` 与 `reference/paper_v2_rewrite_refs.bib`。
- 等待任务完成并下载 artifacts zip。
- 检查 `polished.tex`：全文 `\\end{document}` 只出现 1 次。
- 检查 `review-report.md`：包含 `Retrieval Diagnostics`；若 Gap 建议较少，应包含 `Supplemental Bibliography Candidates`。
- 检查 `suggested.bib`：应来自真实检索卡片；除强 evidence 外，可包含 selected 候选弱推荐，人工核验后再引用。

## 2026-06-24：F-05 artifacts (4) 复核后的重测点

- 重新上传同一组 IEEE TAES tex/bib 后，检查 `polished.tex`：
  - 保留 `\\title`、作者、`abstract`、`IEEEkeywords`。
  - `\\end{document}` 只出现 1 次。
  - `\\label{}`、`\\ref{}`、`\\cite{}` 不被模型改名；若模型尝试改结构，应保留原章节并在报告中体现失败/保守状态。
  - 不应出现 `REF_WITHOUT_LABEL` lint。
- 检查 `suggested.bib`：
  - 保留强 evidence 推荐。
  - 不再混入 CP2K、泛 6G、O-RAN 等低相关 supplemental 候选。
