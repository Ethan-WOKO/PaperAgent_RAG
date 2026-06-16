# 论文润色工具与方案调研

> 文档作用概述：在正式实现研伴 Agent 第二期论文润色质量专项前，调研市面常见论文润色 / 学术写作工具与相关 Agent 思路，提炼可借鉴能力，并反推本项目第二期实现重点。
>
> 更新日期：2026-06-15
> 说明：本文已使用联网抓取（curl / Wikipedia / GitHub）对主要工具与开源项目做过二次核对，成熟商业产品能力迭代较快，具体以官方最新说明为准；核对要点见第 11 节。
>
> ⚠️ **方向变更（2026-06-16）**：第二期已由「docx 论文润色」整体转向「面向 LaTeX 论文的 AI 审稿/改稿 + 文献补全」。本文第 1–11 节中的 docx 解析/结果 docx/步骤编号等内容已被取代，仅作调研历史保留；最新落地以 `discussion_about_fix_paper_20260615.md`、`paper-quality-plan.md`、`implementation.md` 第二期为准。第 12 节已同步更新。

---

## 1. 调研目的

当前研伴 Agent 一期已经打通论文任务、SSE、下载链路，但真实论文润色仍是骨架版。第二期如果直接实现，容易陷入“只让模型改写文本”的简单方案，缺少成熟论文润色工具常见的：

- 修改前后对比
- 修改理由
- 学术风格控制
- 章节级审查
- 结构一致性检查
- 可回溯的评分与建议
- 用户可理解的结果展示

因此，先调研常见工具形态，再确定本项目实现方案。

---

## 2. 调研对象分类

### 2.1 商业写作 / 润色工具

| 工具 | 实际定位（联网核对） | 可借鉴点 | 对本项目启发 |
|---|---|---|---|
| Grammarly | 写作助手：语法/拼写/语气/查重；**2025 起集成生成式 AI**（生成正文、**自动插入引用**、humanize、预测成绩） | 即时问题标注、修改建议、原因说明、严重程度 | 不应只输出润色后文本，应保存 issue / suggestion / reason |
| Paperpal | 面向科研的学术写作工具 + research assistant：语法检查、paraphraser、**查重**、AI 写作、**引用生成** | 学术表达、期刊语气、上下文相关建议 | 论文润色应区别普通语法修复与学术表达增强 |
| Writefull | “automated writing and proofreading for academics”，面向学术英语 | 学术短语、句式建议、语言质量检查 | 可引入 academic tone 评分维度 |
| Wordvice AI | AI writing assistant & text editor（依托人工润色服务 Wordvice） | 按用途选择润色 / 改写 / 摘要等模式 | 后续可支持 polishMode，目前先预留字段 |
| QuillBot | 改写 / 语法 / 摘要工具，**已被 Course Hero 收购** | 多种改写模式、简洁/正式/流畅 | 第一版可默认“学术润色”，后续扩展模式 |
| DeepL Write | 语言改写与表达优化 | 句级改写、替代表达、语气优化 | 可借鉴“保留原意 + 多版本表达”的理念 |
| ChatGPT / Claude | 通用 LLM 写作助手 | 可做多轮 critique-revise，解释修改原因 | 适合本项目的 Orchestrator：summary → polish → review → revise |

> 取舍提醒：**引用生成 / 查重是 Grammarly、Paperpal 已有的能力**，本项目第一版刻意后置（只做文献推荐、不自动插入引用），是为了避免幻觉与格式风险，而不是“没人做”。

### 2.2 论文审查 / Reviewer Agent 思路

| 思路 | 典型做法 | 可借鉴点 |
|---|---|---|
| Critique-Revise Loop | 先生成改写，再自评审查，再根据意见重写 | 对应本项目 section polish → section review → retry |
| Self-Refine | 同一模型反思自己的输出并迭代 | 可用于低分章节自动重试 |
| Multi-Agent Review | 不同角色负责语言、逻辑、结构、引用 | 第一版不必多 Agent，但可以用评分维度模拟多角色 |
| Paper Reviewer Agent | 生成论文审稿意见 | 可用于跨章 PaperReview，不只做语言润色 |
| RAG-assisted Writing | 引入背景知识、文献或术语表 | 后续可结合知识库和 OpenAlex，但第一版先做文献推荐 |

### 2.3 开源项目（已联网核对的真实仓库）

以下为实际搜到的代表项目（star 数为核对时取值，会变化）：

| 项目 | star | 说明与可借鉴 |
|---|---|---|
| `kody-black/The-Strict-Precise-AI-Academic-Reviewer` | ~371 | AI 论文审查 prompt 集，验证“审查以 prompt 为核心”是主流 |
| `bahayonghang/academic-writing-skills` | ~335 | 写后工具包：格式校验 + 语法/风格润色，印证“校验+润色分开” |
| `Theigrams/Academic-Writing-Assistant` | ~53 | 中文 LLM 学术写作辅助 |
| `FanBroWell/AI-paper-reviewer` | ~13 | LLM 论文审查工具 |
| `jiayou20021120-afk/paper-conductor` | - | 起草 → 润色 → 审查 → 规划全流程 skill，与本项目 Orchestrator 思路一致 |
| `ShiqianTan/PolishlingYourPaper` | - | LLM 论文润色 agent |
| `yumuzhihan/PaperPilot` | - | 自动文献检索 + 写作辅助 |

另有学术 Agent 方向的知名项目（如 SakanaAI 的 AI-Scientist）侧重自动写论文/做实验，与“润色已有论文”场景不同，可作为思路参考。

开源项目常见实现方式通常包括：

1. 按段落 / 章节切分长文。
2. 对每段调用 LLM 改写。
3. 输出 Markdown / LaTeX / docx。
4. 用 diff 展示前后差异。
5. 可选加入 review prompt。

但很多开源实现存在不足：

- 只处理纯文本 / Markdown，不处理 docx 结构。
- 只改写，不审查。
- 不记录每轮评分。
- 无任务状态与 SSE。
- 无前端可视化对比。

研伴 Agent 的优势可以是：

- 有任务系统。
- 有 SSE 进度。
- 有 docx 输入输出。
- 有论文页工作流。
- 可以保存章节级结构化结果。

---

## 3. 成熟工具的共性能力

调研后可以归纳出论文润色工具的共性能力：

### 3.1 修改建议不是最终答案的附属品，而是核心结果

成熟工具通常不会只给用户一段“改好后的文本”，而是会告诉用户：

- 哪里有问题
- 为什么这么改
- 改动属于语法、表达、逻辑还是风格
- 改动是否可选

对本项目意味着数据结构至少应保留：

```text
originalText
polishedText
issues
suggestions
score
reason / explanation
```

### 3.2 修改前后对比很重要

论文用户通常不信任完全黑盒改写，需要看到：

- 原文
- 润色后
- 差异
- 修改理由

本项目第一版可以不做 Word track changes，但应保存 diff 所需数据，前端后续可展示章节级对比。

### 3.3 学术风格和普通语言润色不同

论文润色不是简单“语法正确”，还包括：

- 学术语气
- 术语一致性
- 逻辑衔接
- 句式正式程度
- 表达是否过度夸张
- 是否保留原意

因此评分维度不应只有一个总分，应包含多个维度。

### 3.4 长文需要先全局理解，再局部处理

成熟长文处理通常不能直接逐段孤立改写，否则容易出现：

- 术语不一致
- 章节之间逻辑断裂
- 摘要与正文不一致
- 引言与结论表达不一致

因此本项目的 Summary → 分章处理 → 跨章审查是合理的。

### 3.5 用户需要阶段化反馈

长任务不能只显示 loading。用户需要知道：

- 正在总结全文
- 正在处理第几章
- 当前章第几次尝试
- 当前评分
- 是否进入跨章审查
- 是否正在生成摘要 / 文献推荐

这与现有 SSE 架构契合。

---

## 4. 对研伴 Agent 的方案吸收

### 4.1 第二期最小可用目标

不追求“像商业产品一样完整”，第二期最小可用目标建议是：

1. 能解析 docx 成章节。
2. 能生成全文 Summary。
3. 能逐章润色。
4. 能逐章审查并给出结构化评分。
5. 能低分重试。
6. 能做跨章审查。
7. 能生成 / 优化摘要。
8. 能推荐 OpenAlex 文献。
9. 能生成真实结果 docx。
10. 前端能展示阶段进度、章节进度、评分与建议。

### 4.2 不建议第一版做的能力

| 能力 | 原因 | 后续方向 |
|---|---|---|
| Word track changes 修订模式 | 实现复杂，POI 支持有限 | 后续研究 docx revision 或前端 diff |
| 接受 / 拒绝单条修改 | 需要复杂交互与局部回写 | 后续做章节级编辑器 |
| 多版本候选润色 | 增加模型成本与 UI 复杂度 | 后续支持 polishMode |
| 复杂排版保持 | Word 文档格式复杂 | 第一版保留标题和段落顺序即可 |
| 多 Agent 并行审稿 | 编排复杂，成本高 | 用单模型多 prompt 模拟 |
| 引文自动插入正文 | 容易产生幻觉和格式问题 | 第二期只推荐文献，不自动插入引用 |

---

## 5. 推荐的论文润色结果数据结构

调研后建议不要只存最终文本，而是保存章节级结构化结果。

### 5.1 任务级

```text
paper_tasks
  id
  status
  current_stage
  source_filename
  target_language
  final_object_key
  summary_json / summary_text
  paper_review_json / paper_review_text
  references_json
```

如果不想扩展太多字段，也可将 summary / review / references 存入 `paper_task_rounds` 或单独 JSON 文件，但前端展示会不方便。

### 5.2 章节级

建议新增或等价实现：

```text
paper_sections
  id
  task_id
  section_index
  title
  original_text
  polished_text
  review_json
  score
  status
  attempt_count
  created_at
  updated_at
```

### 5.3 尝试 / 轮次级

继续使用或扩展：

```text
paper_task_rounds
  task_id
  stage
  section_index
  attempt_index
  input_preview
  output_preview
  score
  status
  error_message
```

---

## 6. 推荐 Prompt 输出格式

### 6.1 Summary 输出

```json
{
  "topic": "论文研究主题",
  "method": "核心方法",
  "contributions": ["贡献1", "贡献2"],
  "problems": ["潜在问题1"],
  "keywords": ["keyword1", "keyword2"]
}
```

### 6.2 Section Review 输出

```json
{
  "score": 82,
  "passed": true,
  "issues": [
    {
      "type": "clarity",
      "severity": "medium",
      "message": "句子过长，影响可读性"
    }
  ],
  "suggestions": ["建议拆分第二句并明确主语"]
}
```

### 6.3 Paper Review 输出

```json
{
  "overallScore": 78,
  "consistencyIssues": ["第 2 章和第 4 章对核心术语表述不一致"],
  "structureIssues": ["实验部分缺少对比基线说明"],
  "suggestions": ["统一术语，并在实验章节补充 baseline 描述"]
}
```

解析失败时不应直接失败整个任务，应保存原始文本并标记 degraded。

---

## 7. 前端论文页建议

第二期论文页不应只显示 SSE 日志，建议展示：

### 7.1 任务总览

- 当前阶段
- 总章节数
- 已完成章节数
- 当前章节
- 当前尝试次数

### 7.2 章节列表

每章展示：

- 标题
- 状态
- 分数
- 问题数
- 尝试次数

### 7.3 审查意见

展示：

- 最近章节审查意见
- 跨章审查意见
- 分数
- 建议

### 7.4 推荐文献

展示：

- 标题
- 年份
- 作者
- DOI / URL

### 7.5 结果下载

下载真实润色后的 docx。

第一版可以不做全文 diff，但应为后续 diff 预留数据。

---

## 8. 推荐实施顺序

结合当前项目状态，建议顺序为：

1. 数据结构确认。
2. docx 解析。
3. Prompt 变量与 JSON 输出规范。
4. Summary。
5. 分章润色。
6. 分章审查评分。
7. 跨章审查。
8. Abstract。
9. OpenAlex。
10. 结果 docx。
11. 论文页展示增强。
12. 中文 / 英文样例验收。

这与当前 `implementation.md` 的 D/E/F 阶段一致。

---

## 9. 对当前计划的影响

### 9.1 对 `implementation.md`

建议在 D 阶段保留：

- 数据结构确认
- docx 解析边界确认
- Prompt 输出格式确认
- 前端展示范围确认

这些是必要前置。

### 9.2 对 `paper-quality-plan.md`

应持续维护：

- Prompt 变量表
- JSON 输出格式
- 评分维度
- docx 解析限制
- OpenAlex 策略

### 9.3 对 `architecture.md`

真正进入实现后，需要补充：

- `paper_sections` 或等价存储策略
- Orchestrator 阶段状态机
- 结果 docx 生成策略

---

## 10. 最终建议

第二期应避免做成“LLM 把全文重写一遍”。更合理的目标是：

> **让用户看到每一章为什么被改、改得怎么样、还有什么问题，并能下载一份真实润色后的 docx。**

因此，本项目第二期论文润色的核心不是单纯 prompt，而是：

```text
结构化文档解析
+ 全文理解
+ 分章润色
+ 结构化审查
+ 可追踪评分
+ 结果 docx
+ 前端可解释展示
```

这会比普通“论文润色按钮”更符合研伴 Agent 的定位。

---

## 11. 联网核对来源与说明

本文的核对于 2026-06-15 通过环境内 curl / Python 联网完成，主要来源：

- Wikipedia：Grammarly、QuillBot（归属 Course Hero）、DeepL Translator 词条摘要。
- 官网 meta 描述：paperpal.com、writefull.com、wordvice.ai。
- GitHub Search API：论文润色 / 审查 / 学术写作类仓库及 star 数。

核对结论：

1. 原文对各工具的定位方向基本正确，但偏保守：Grammarly、Paperpal 实际已做生成式写作、查重与引用生成。
2. QuillBot 已被 Course Hero 收购，原文未标注。
3. 开源部分原文只有关键词，现已替换为真实仓库。
4. 核心方法论（critique-revise、分章、审查 prompt、结构化结果）与主流开源/商业实践一致。

注意：star 数与商业产品能力会随时间变化，本节仅代表核对时点的快照。


---

## 12. 真实项目 README / 源码精华提炼（已抓取核对）

> 已抓取并阅读：`Theigrams/Academic-Writing-Assistant`（README + app.py + rewriter.py + utils.py）、`jiayou20021120-afk/paper-conductor`（README + references/stage_cards.md）、`bahayonghang/academic-writing-skills`（README）。以下按“值得采纳”与“刻意不采纳”两栏提炼，避免全盘照搬。

### 12.1 Theigrams/Academic-Writing-Assistant（最贴近我们“章节级润色”）

值得采纳：

1. **Prompt 即文件**：prompts 以 prompts 目录下 .md 存放，文件名即服务类型（如 academic_rewriting、translation），运行时动态加载。印证我们 E-02 用 resources/prompts 下 .md，并支持按类型选择。
2. **标签式结构化输出**：system prompt 要求模型返回 output 标签与 explanation 标签，代码用正则抽取。对“润色正文 + 修改说明”这类长文本输出，标签比严格 JSON 更稳（JSON 里塞大段正文容易转义出错）。
3. **diff 自己算，不让模型产出**：用 difflib 的 SequenceMatcher 在本地生成 ins/del 标记，模型只输出润色后文本。我们可照此在后端/前端自算章节级 diff，避免模型编造 diff。
4. **结果展示分层**：对比 diff → 干净润色文本 → 修改说明 → 折叠原始输出 → 折叠 prompt。直接映射到我们论文页章节展示。
5. **调试模式**：可临时编辑 prompt 但不保存，便于调参。

刻意不采纳 / 不足：

- 只做单段改写，无章节编排、无评分、无重试、无 docx、无任务系统——正是我们要补的部分。

### 12.2 paper-conductor（编排哲学，不是直接代码）

值得采纳：

1. **按“手上有什么产物”判断当前阶段**：可用于我们根据已有数据（是否有 Summary、是否有章节结果）推断 task 当前 stage，而不是只靠状态字段。
2. **review → revise 回环，硬上限两轮**：给了一个具体且克制的重试上限参考，避免无限润色。
3. **职责分离铁律**：“润色管读起来怎样，审稿管立得住吗”——polish prompt 与 review prompt 目的必须分开，不要混在一个 prompt 里。
4. **阶段卡模板**：每阶段回答“识别信号 / 自己做什么 / 输入 / 产出 / 交接 / 常见坑”。这是我们 D 阶段给论文流水线写阶段文档的好骨架。
5. **常见坑可直接变成 prompt 约束**：如“综述别写成罗列”“先内容后格式”“别把 AI 审稿当真同行评审”。

刻意不采纳：

- 它是多 skill 路由/交接（8 阶段含选题、答辩、投稿、出图），范围远超我们“润色已有论文”。选题/答辩/出图/投稿信等阶段第一版不做。

### 12.3 bahayonghang/academic-writing-skills（边界与安全）

值得采纳：

1. **明确范围：只改进已有稿，不从零写论文、不编实验/引用**。与我们第二期定位完全一致，应写进 prompt 守则。
2. **审查输出区分 blocker 与 polish 两类**（submission gate：blockers vs polish issues）。建议我们章节/全文审查也把问题分级为“必须改(blocker) / 建议改(minor)”。
3. **结果带免责声明**：“审查报告仅供参考，请自行核实”。我们论文页审查结果也应标注“AI 自查，非同行评审”。

刻意不采纳：

- 它强绑定 Claude Code 生态与本地 .bib 检索脚本，我们用自有后端 + Web，不引入其工具链。

> **方向更新（2026-06-16）**：第二期已由 docx 整体转向 LaTeX 方向，**采纳「绑定 LaTeX 工具链」这一条**——输入输出绑定 `.tex` + 本地 `.bib`，产出可直接在 VSCode 编译。原「我们是 docx，不绑 LaTeX」的判断已作废。本地 `.bib` 检索思路也被吸收为 L3 文献地基（详见 `discussion_about_fix_paper_20260615.md` 第 8、9 节）。

### 12.4 提炼出的、将落入我们设计的决定

| 决定 | 来源 | 落点 |
|---|---|---|
| 润色输出用标签式（正文 + 说明），审查输出用 JSON | Theigrams 标签法 + 我们 JSON 评分需求 | paper-quality-plan.md 输出格式 |
| 章节 diff 后端自算（difflib 等价），不让模型产出 | Theigrams utils | E-07 / F-03 |
| 审查问题分级 blocker / minor | academic-writing-skills | review JSON 增加 severity |
| review 回环硬上限（如 2 轮） | paper-conductor | E-07 maxAttempts=2 |
| polish 与 review prompt 严格分离 | paper-conductor | E-03 prompt 拆分 |
| prompt 守则：不编实验/引用、只改已有稿 | academic-writing-skills | E-03 公共 system 约束 |
| 审查结果标注“AI 自查，非同行评审” | academic-writing-skills | F-04 前端文案 |
| 阶段卡文档骨架（识别/做什么/输入/产出/交接/坑） | paper-conductor | D 阶段论文流程文档 |
| **绑定 LaTeX 工具链 + 本地 .bib 检索** | academic-writing-skills | **L0 解析 + L3 文献地基（方向更新后采纳）** |
