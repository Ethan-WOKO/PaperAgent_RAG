# 论文质量样例集与评价记录（E-09）

> 用途：记录可重复对比的论文质量验收样例、三件套结果与人工评价。该文件不存放源代码，仅记录样例路径、验收过程和结论。

## 1. 样例索引

| 编号 | 语言 | 输入形态 | 样例路径 | 验收重点 |
|---|---|---|---|---|
| Q-ZH-01 | 中文 | `main.tex` + `refs.bib` | `private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/zh-rag-polish/` | 中文章节、figure/ref/cite/math 保护、推荐文献可追溯 |
| Q-EN-01 | 英文 | `main.tex` + `refs.bib` | `private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/en-literature-gap/` | 英文章节、equation/ref/math 保护、gap/review 记录 |
| Q-INLINE-01 | 英文 | 仅 `main.tex`，内联 `thebibliography` | `private-helper-agent/yanban-paper/src/test/resources/paper-quality-samples/inline-bibliography/` | 无 `.bib` 场景、内联 bibitem 解析 |

## 2. 端到端评价记录

### Q-ZH-01 中文 RAG 论文样例

- 原文记录：`zh-rag-polish/main.tex`，包含摘要、引言、方法、实验、结论。
- 原始文献：`zh-rag-polish/refs.bib`，包含 `lewis2020rag`。
- 三件套结果记录：
  - 润色后 `.tex`：真实任务产物应记录 `paper_task_artifacts.type=polished_tex` 的 object key；当前离线基线为“保持原结构，不破坏 cite/ref/figure/math”。
  - `suggested.bib`：真实任务产物应记录 `paper_task_artifacts.type=suggested_bib`；推荐条目必须来自当前任务已选真实文献卡片。
  - 审查报告：真实任务产物应记录 `paper_task_artifacts.type=review_report`；应包含章节状态、建议、evidence 与 AI 自查免责声明。
- 人工评价：
  - 推荐文献是否真实可溯源：待真实模型/检索任务执行后核对；标准为 DOI/arXiv/OpenAlex/S2/title 任一身份可回溯。
  - 原始引用是否保留：`lewis2020rag` 必须保留。
  - 受保护元素是否保留：`figure`、`includegraphics`、`label`、`ref`、`cite`、数学 `$q$` 与 Top-$k$ 不得被破坏。
  - 语言质量关注点：中文表达应更学术，但不得增加未经 evidence 支撑的实验结论。
- 当前结论：离线样例完整性已建立；自动解析测试覆盖章节、引用、浮动体、受保护元素与 lint。真实端到端人工分数待前端/模型环境复测后补充。

### Q-EN-01 English literature-gap sample

- 原文记录：`en-literature-gap/main.tex`，包含 Abstract、Introduction、Method、Discussion、Conclusion。
- 原始文献：`en-literature-gap/refs.bib`，包含 `lewis2020rag`。
- 三件套结果记录：
  - Polished `.tex`: real task output should be recorded from `paper_task_artifacts.type=polished_tex`; baseline requirement is to preserve equation/ref/cite/math.
  - `suggested.bib`: real task output should be recorded from `paper_task_artifacts.type=suggested_bib`; every entry must be backed by selected literature cards.
  - Review report: real task output should be recorded from `paper_task_artifacts.type=review_report`; it should separate advocacy patches from critique-only suggestions.
- 人工评价：
  - Literature traceability: pending real retrieval/model execution; every recommendation must be traceable to actual OpenAlex/arXiv/API-returned cards.
  - Placeholder preservation: Equation `eq:score`, citation `lewis2020rag`, inline math and display equation must remain valid.
  - Gap quality: acceptable suggestions should focus on compile validation, human patch acceptance, and multimodal limitations already stated by the paper.
- 当前结论：离线样例完整性已建立；自动解析测试覆盖章节、citation、equation/ref、math 与 lint。真实端到端人工分数待前端/模型环境复测后补充。

## 3. 人工评分模板

| 项 | 评分范围 | 说明 |
|---|---:|---|
| 可编译/静态结构 | 0~5 | 无残留占位符、括号/环境配平、cite key 存在 |
| 受保护元素保持 | 0~5 | cite/ref/label/math/figure/table/equation 不被改坏 |
| 语言润色质量 | 0~5 | 更流畅、更学术、不过度改写技术含义 |
| 建议 grounded 程度 | 0~5 | 每条 ADVOCACY 是否有真实 evidence |
| 文献真实性 | 0~5 | 推荐文献是否可通过 DOI/arXiv/OpenAlex/S2/title 回溯 |
| 报告可用性 | 0~5 | 是否清楚区分可直接采纳与需作者补证据的批评 |

## 4. 后续记录要求

- 每次真实端到端运行后，补充 task id、artifact object key、执行时间、模型 provider、检索源。
- 若发现推荐文献无法回溯，必须记录为 blocker，并回退到 E-05/E-06 grounding 校验。
- 若发现 `.tex` 不能静态通过或编译失败，必须记录具体 cite key、环境或占位符问题。
