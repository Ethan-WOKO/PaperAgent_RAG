# 研伴 Agent 论文质量专项计划（LaTeX 方向）

> 文档作用概述：记录第二期「面向 LaTeX 论文的 AI 审稿/改稿 + 文献补全」专项的目标、能力分层、处理流水线、Prompt 分层、检查项与验收方式；不记录具体代码步骤。完整设计见 `discussion_about_fix_paper_20260615.md`（共 15 节）。
>
> 更新日期：2026-06-16（由 docx 方向整体转向 LaTeX 方向）

## 1. 背景与方向

一期 B-10 已打通论文任务、SSE、pause/resume/stop、下载链路（骨架版，按 docx 设计）。第二期方向重大转向：从「docx 论文润色」升级为「**面向 LaTeX 论文的、有据可查的 AI 审稿/改稿 + 文献补全助手**」。

- 输入输出绑定 LaTeX：`.tex` + 本地 `.bib`（无 `.bib` 时由文献系统反向生成）；产出可直接在 VSCode 编译。
- 润色是入门能力；卖点是**文献地基 + 研究级建议 + 无 bib 自动生成**。
- 原 docx 解析路线作废。

## 2. 能力分层（L0–L5）

| 层 | 能力 | 第一版 |
|---|---|---|
| L0 | LaTeX 解析 + 结构分析（地基，不真编译） | ✓ |
| L1 | 语言润色（占位保护） | ✓ |
| L2 | 结构审查（纯静态、不编译） | ✓ |
| L3 | 文献地基（多源检索 + 读摘要 + 卡片 + 对比矩阵） | ✓ |
| L4 | 研究级建议（挂真实引用） | ✓ |
| L5 | 多模态图 critique | 后置 |

## 3. 目标

1. 解析 `.tex` + `.bib`，构建文档模型并识别章节角色。
2. 结构歧义时与用户确认，不一步错步步错。
3. 抽取结构化研究画像，作为检索种子与 gap 基准。
4. 多源检索真实文献，去重缓存、读摘要、产出概念阶梯。
5. gap 分析产出有据可查的 Suggestion（辩护轨 + 批评轨）。
6. 占位保护下分章润色（允许实质重写，不破坏受保护元素）。
7. 产出三件套：润色后 `.tex` + 审查报告（每条挂真实引用）+ suggested.bib。
8. 为每个阶段保留事件、可恢复状态与错误信息。

## 4. 非目标

- 不保证自动达到发表级质量。
- 第一版不真编译（不装 TeX），只做静态结构分析。
- 第一版不碰方法对不对 / 创新性够不够，只覆盖结构 / 实验 / 相关工作缺口。
- 不做多人协同审稿。
- 不直接复制 `paper-agent` 代码，仅参考语义与流程。
- **防幻觉铁律**：任何被推荐论文必须来自检索 API 真实返回，模型只能从候选选并解释，绝不凭空写引用。

## 5. 目标流水线（阶段链）

```text
PARSE            L0：解压/拍平、tex 分词、建文档模型、解析 bib、硬 lint 早报
ROLE_RECOGNITION L0：启发式 + LLM 角色复核（限定枚举）
STRUCTURE_CHECK  交互检查点：内容位置判断、批量问、默认保持原样
PROFILE          L3：结构化研究画像
RETRIEVE         L3：概念阶梯/多轴/多源召回/去重缓存/读摘要抽取/排序/MMR 选片
GAP_ANALYSIS     L4：对比矩阵 + Suggestion（辩护+批评）+ L2 软审查汇入
POLISH           L1：占位保护分章润色 + 审查回环（GAP 先 POLISH 后）
ASSEMBLE         三件套产出（基础/进阶两模式，采纳后过 lint）
COMPLETE
```

## 6. L0 LaTeX 解析与文档模型边界

务实分词（非完整解析），只认分节命令、`\begin/\end` 环境、受保护内联、注释；其余当不透明正文。

| 内容 | 第一版策略 |
|---|---|
| 文档模型字段 | preamble(只读但抽 title/authors/keywords)、sections[level/title/role/rawRange/blocks]、protectedSpans、floats[含 rawContent 表格原文]、citeUsage[认全部引用命令]、crossRef[认全部 `\ref` 家族]、bib、sourceMap |
| bib 来源 | 外部 `.bib`（`\bibliography`/`\addbibresource`）+ 内联 `\begin{thebibliography}` |
| 占位保护 | `$...$`/数学环境/`\cite`系/`\ref`系/`\label`/`\includegraphics`/`\input`/`\include` 换占位符，返回后校验 ⊆ 输入集合再换回 |
| verbatim/lstlisting/minted | 整块不透明 |
| 自定义重定义分节命令 | 第一版不支持 |
| `\section*` / appendix | 标 unnumbered / Appendix |
| 中文 ctex/CJK | 支持 |
| 编译 | 第一版不真编译，只静态分析 |

## 7. 角色识别与结构确认检查点

- 角色枚举：Intro / RelatedWork / Method / Experiments / Results / Discussion / Conclusion / Abstract / References / Appendix / unknown。
- 三级识别：启发式（关键词中英 + 位置 + 内容）→ LLM 复核（限定枚举，不许发明角色）→ **用户可改（第一版必须有）**。
- 结构确认检查点：按**内容位置**判断相关工作等是否真缺失（避免「揉进引言」误报）；歧义批量问一次 + 默认「保持原样」+「全部保持」一键通过；区分阻塞类（先问）vs 提示类（带默认继续）；默认尊重用户结构，只有明确同意才重构。

## 8. L3 文献地基

- `LiteratureSource` 抽象：OpenAlex + arXiv 免费源；Semantic Scholar 可插拔（填 key 启用）。
- `LiteratureService` 通用层：查 + 去重 + 缓存 + 卡片 + 排序，分 LIGHT/DEEP；阶梯/剧本逻辑留论文模块。
- identity 主键：DOI > arXiv id > OpenAlex/S2 id > 规范化标题哈希；三层缓存 raw/card/analysis，分析永久复用。
- 只读摘要；相关性打分（语义相似度为主 + 引用重叠 + 关键词重叠 + 时效/影响力轻权 + 已在 .bib 降权）。
- 分层配额选片（每层保底含奠基作）+ 层内 MMR；产出概念阶梯（优势组/劣势组各挂文献）+ gap。
- 立场铁律：引言/相关工作站用户这边（找现有方法局限反衬）；审稿意见站审稿人这边；同批文献用法相反，绝不在引言贬低用户。
- 诚实边界：只用真实可溯源局限反衬，反衬不动就如实进审稿意见，不编造别人局限硬吹。

## 9. L4 gap 分析与建议

- 统一 Suggestion 对象：track(advocacy/critique)、category、severity(blocker/minor)、statement、evidence[真实卡片 id]、applicable、patch(contentType A/B、anchor、latexSnippet、addedBibKeys)。
- A/B 判据按「能否 grounding」：A 类可插真内容（相关工作/引用/靠真实局限反衬的贡献陈述）；B 类只插骨架（实验/数据，绝不编造）。
- 立场分流：辩护轨进 tex 补丁；批评轨进审查报告（可带 B 骨架）。
- 诚实闸门：吹不动就转批评，绝不编造别人局限。
- 锚点靠 L0 角色，缺失角色 → 新建节补丁；优先级 + 分组 + 配额（每类约 5 条可展开）。
- 对比维度：任务/问题、数据集、baseline/对比方法、消融、评价指标、相关工作覆盖。

## 10. L1/L2 执行

### 10.1 L1 占位保护润色

- 逐章（preamble、References 除外）：mask → section-polish（允许实质重写，必须保留占位符）→ 校验占位符 ⊆ 输入集合 → unmask → 静态 lint → section-review → 不达标且 attempt < maxAttempts(=2) 带审查意见重试。
- 掉占位符：拒绝该次输出并重试；仍丢则保留原章节不润色（宁可不改不破坏）。
- 超长章节按子节/段落切块；Abstract 用摘要专用 prompt。
- 后端自算词级 diff，模型只输出润色文本，不让模型编造 diff。

### 10.2 L2 结构审查（纯静态）

硬性 lint（blocker，PARSE 早报）：悬空 `\cite`、断 `\ref`/`\eqref`、括号/环境不配平、bib 重复 key。

软性审查（minor，GAP 阶段汇入 suggestions）：缺标准章节（仅 STRUCTURE_CHECK 确认后报）、章节顺序异常、空/过短章节、bib 孤立文献、未用 `\label`、图表从未被 `\ref`、图表无 caption、引用密度过低。

## 11. Prompt 分层

> 设计参考（详见 `paper-polish-research.md` 第 12 节）：Theigrams 用**标签式输出**分开「润色正文 + 修改说明」；paper-conductor 强调 **polish 与 review 职责必须分离**、**review→revise 回环硬上限**。

| Prompt | 作用 |
|---|---|
| role-confirm | LLM 角色复核（限定枚举 + 缺失角色） |
| research-profile | 抽取结构化研究画像 |
| section-polish | 占位保护下润色单章 |
| section-review | 审查单章逻辑/表达/格式 |
| literature-extract | 读摘要抽取文献卡片分析（L3c） |
| gap-analysis | 对比矩阵 + 生成 Suggestion |
| relatedwork-gen | 辩护轨相关工作段落生成 |
| contribution-gen | 贡献陈述生成（诚实闸门） |
| abstract | 生成/优化摘要 |

所有 prompt 放入 `yanban-paper/src/main/resources/prompts/`（文件名即类型，动态加载，参考 Theigrams）。

### 11.0 公共 system 守则（所有 paper prompt 复用）

借鉴 `academic-writing-skills`：

- 只改进已有稿件，**不从零写论文、不编造实验/数据/引用/未经支撑的结论**。
- 保留原意与关键信息，不随意增删事实。
- 输出严格按约定格式（标签或 JSON），不输出额外闲聊。
- 推荐文献只能来自检索 API 真实返回的候选，不得凭空生成引用。

### 11.1 Prompt 变量规范

| 变量 | 说明 |
|---|---|
| `targetLanguage` | `zh` 或 `en` |
| `paperTitle` | 论文标题（从 preamble 抽取） |
| `researchProfile` | 结构化研究画像 |
| `sectionTitle` / `sectionText` | 当前章节标题 / 原文或上轮润色文本 |
| `scoreThreshold` | 通过阈值 |
| `reviewComments` | 上轮审查意见 |
| `attemptIndex` / `maxAttempts` | 当前 / 最大尝试次数 |
| `literatureCandidates` | 真实检索返回的候选文献列表 |

### 11.2 模型输出格式规范

**润色输出用标签（借鉴 Theigrams）**：

```text
<output>润色后的章节正文（占位符原样保留）</output>
<explanation>本次主要改动与原因</explanation>
```

后端正则抽取；缺失 output 标记 degraded。

**审查输出用 JSON，问题分级 blocker / minor**：

```json
{"score": 82, "passed": true,
 "issues": [{"severity": "blocker", "message": "..."},
            {"severity": "minor", "message": "..."}],
 "suggestions": ["..."]}
```

**研究画像 JSON**：problem、method、contributions、datasets、baselines、metrics、tasks、keywords。

**Suggestion JSON**：track、category、severity、statement、evidence[卡片 id]、applicable、patch。

**文献抽取 JSON（L3c）**：problem、method、datasets、baselines、metrics、contributions、tasks、keywords、role(background/competitor/baseline)。

解析失败时保留原始文本并标记 degraded，不中断整个任务。

## 12. 评分维度

| 维度 | 说明 |
|---|---|
| clarity | 表达清晰度 |
| academic_tone | 学术表达风格 |
| logic | 逻辑连贯性 |
| consistency | 术语与跨章一致性 |
| completeness | 是否保留原意与关键信息 |
| language | 中英文语法与表达 |

第一版用模型自评分；后续引入更稳定规则或双模型评审。

## 13. 事件与持久化

SSE 事件（保留旧事件兼容）：parse_start/parse_done、roles_ready、clarification_needed/clarification_resolved、profile_ready、retrieve_start/retrieve_progress/cards_ready/ranking_done/selection_done、gap_analysis_done/suggestions_ready、section_polish_start/section_polished/polish_done、assemble_done/artifact_ready、complete、error、paused/resumed/stopped。

持久化（数据模型见讨论纪要第 12 节）：扩展 `paper_tasks`、复用 `paper_task_rounds`；新增 `paper_sections`、`paper_task_analysis`、`suggestions`、`suggestion_evidence`、`paper_task_literature`、`paper_task_clarifications`、`paper_task_artifacts`；全局 `literature_cards`。文件/产物走 MinIO。

## 14. 交付物三件套要求

- 润色后 `.tex`：受保护元素数量与输入一致、无残留占位符、可在 VSCode 编译。
- 审查报告：每条挂真实引用，结尾标注「AI 自查结果，仅供参考，非同行评审」。
- suggested.bib：均来自真实检索返回，可被 LaTeX 识别。
- 两模式：基础版（不改原文/原 bib 只推荐）/进阶版（改原文 + 自动补 `\cite`，采纳后过静态 lint）。

## 15. 验收方式

### 自动化

- LaTeX fixture（含内联 bibliography）可解析章节/受保护元素/bib。
- 硬 lint 捕获致命错误；identity 去重与缓存命中生效。
- Mock LLM 下完整 orchestrator 产出三件套；推荐均来自真实返回。
- pause/resume/stop 与 WAITING_INPUT 续跑不破坏状态一致性。
- OpenAlex / arXiv client 用 Mock HTTP 测试解析。

### 手动

- 中文 LaTeX 与英文 LaTeX 各跑完整流程（基础版 + 进阶版）。
- 含无 `.bib`/内联 bibliography 样例。
- 核对推荐文献真实可溯源；进阶版可在 VSCode 编译。

## 16. 迭代记录

结果完成后写入 `progress.md` 与 `test-checklist.md`；prompt 变更同步 `revision-log.md`。
