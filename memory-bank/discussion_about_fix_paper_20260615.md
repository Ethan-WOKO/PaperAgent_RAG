# 论文审查 / 润色方向讨论纪要（2026-06-15）

> 文档作用概述：记录关于"研伴 Agent 论文模块第二期重新定位"的讨论过程、已确认决定、待定与后置事项。本文是**讨论纪要**，不是实施计划，也不是最终设计；正式落地前还需转化为 implementation.md / design 文档。结论可能随后续讨论调整。

---

## 1. 背景与方向转变

最初第二期把论文模块定位为"把骨架版论文润色做成真实润色流水线"。讨论后方向被显著拔高：

- 不满足于"语言润色"，要能对**质量较低的论文**给出**修改方向**：该增设哪些部分、该补哪些实验、图是否合适及如何改。
- 这些建议必须建立在**大量文献检索与调研**之上，因此"文献检索 + 总结"不是附属功能，而是整个项目的**地基**。
- 输入输出绑定 **LaTeX + 本地 .bib + 自有后端**，输出可直接在 VSCode 编译运行；不用 Word（格式不可控、公式难处理）。

新的项目定位（讨论共识）：

> **面向 LaTeX 论文的、有据可查的 AI 审稿 / 改稿与文献补全助手**。润色只是入门能力，**文献地基 + 研究级修改建议 + 无 bib 自动生成**才是卖点。

---

## 2. 能力分层（讨论用，非最终）

```text
L0  LaTeX 解析 + 结构分析（地基，第一版不真编译）
L1  语言/表达润色（章节级）
L2  结构审查：缺章节、论证断裂、图表引用问题（纯 tex 可做）
L3  文献地基：多源检索 + 读候选摘要 + 结构化卡片 + 对比矩阵   ← 基础
L4  研究级建议：补哪些实验/章节/baseline，每条挂真实引用     ← 卖点
L5  多模态图 critique（编译出图给视觉模型看）               ← 后置
```

判断：**L0 + L3 是地基，L4 建立在 L3 上，L5 后置**。文献地基预计是整个项目工作量最大的一块。

---

## 3. 已确认的决定

### 3.1 输入输出与格式

- 绑定 LaTeX：输入 `.tex`，输出可编译的 `.tex`。
- 第一版**不装 TeX 环境、不真跑 latexmk**，只做静态 tex 结构分析；真编译校验后置到后续升级。
- 必需 `.tex + .bib`；但**用户没有 .bib（甚至没有任何文献）时，由我们的文献系统反向生成 .bib**（项目特色）。

### 3.2 文献检索

- 第一版接入 **OpenAlex + arXiv** 两个免费源。
- **Semantic Scholar 做成可插拔 provider 接口**（统一 `LiteratureSource` 抽象），后端填入 key 即启用，不填则跳过。
- 读取深度：**只读摘要**（用户 .bib 已有的 + 各源检索回来的相关论文摘要；启用 S2 后也读 S2 结果）。
- 每个任务默认检索/读 **25 篇**，**前端可设置范围**（用户有时需要更大范围）。
- 相关性 / 创新性的判断与排序：**后续单独讨论**。

### 3.3 防幻觉铁律（不可破坏）

```text
任何被推荐的论文，必须来自检索 API 的真实返回结果（有可解析 DOI / arXiv id）。
模型只能"从我们给出的候选列表里选并解释"，绝不允许凭空写出引用。
```

### 3.4 交付物：三件套

```text
1) 润色 / 修改后的 .tex
2) 审查报告（结构/实验/相关工作缺口，每条挂真实引用）
3) suggested.bib（推荐补引的文献）
```

### 3.5 推荐与 .bib 处理（问题 G 的结论）

- 第一版：推荐/生成**只产出 suggested.bib**，不自动往正文插 `\cite`，不改用户原 `.bib`。
- **但后续必须做"自动插入 \cite"能力**：理由是写引言/相关工作是论文最大痛点，解决这块价值极高；即使章节审查没做完美，这部分也要做好。
- 落地形态：前端提供**两个选项 / 两个按钮**：
  - **基础版**：不修改用户原文与原 .bib，只生成推荐（suggested.bib + 报告）。
  - **进阶版**：修改用户原文 `.tex` 与 `.bib`（自动补 `\cite` 与文献条目）。

### 3.6 润色深度（问题 H 的结论，重要）

- **不同意"只改措辞"**。只改措辞很多时候达不到效果，甚至作者的章节本身就有问题。
- 第一版润色应允许**实质性修改**：在**严格审查**的前提下，可以调整结构、补足论证、重组段落，而不仅是语言层面。
- 理由：只做措辞润色体现不出优势，用户大可自己丢给大模型；我们的价值在于"严格审查 + 有依据的实质修改"。

### 3.7 章节角色识别（问题 I 的结论）

- 第一版做**章节角色识别**：把 `\section{...}` 启发式映射到标准角色（Intro / Related Work / Method / Experiments / Conclusion 等），识别不了标 unknown。
- 这是"判断缺哪部分""定位实验/相关工作"的前提。

### 3.8 gap 分析对比维度（问题 J 的结论）

第一版固定对比维度（本文 vs 检索到的同类工作）：

```text
任务/问题、数据集、baseline/对比方法、消融或额外实验、评价指标、相关工作覆盖
```

每个维度输出："本文有没有 / 同类普遍有没有 / 缺口建议 + 证据引用"。

### 3.9 推荐去重（问题 K 的结论）

- 推荐文献前，先与用户 `.bib` **按 DOI / 标题去重**，不重复推荐已引文献。

### 3.10 Summary 升级为"结构化研究画像"（问题 F 的结论）

summary 不再是简单五字段，至少抽取：

```text
问题/方法/贡献/用到的数据集/用到的baseline/任务类型/关键词
```

它同时是**检索查询的种子**与 **gap 分析的基准**。

### 3.11 图 critique（问题 1 的结论）

- 第一版只做**基于 caption / 正文引用的图建议**（是否被引用、caption 是否自洽、该有图却没有、位置是否合理等）。
- **多模态看图 critique 后置**（需编译出 PDF → 渲染 → 视觉模型）。

### 3.12 审稿建议范围（问题 5 的结论）

- 第一版覆盖**结构 / 实验 / 相关工作缺口**。
- 暂不碰**方法是否正确 / 创新性是否足够**这类最主观的判断。

---

## 4. 待定 / 后续讨论

- **输入形态（单 .tex vs zip）**：已决定，见第 7.1 节。第一版同时支持 zip + 单文件并拍平分析；进阶版改原文先只支持单文件。
- **相关性 / 创新性排序算法**：L3/L4 质量核心，后续专门讨论。
- baseline / 数据集的精确抽取：第一版靠 LLM 读 Experiments 正文，不强解析 `tabular`。
- 审查报告的输出语言（zh / en）：待定，倾向用户可选。

---

## 5. 已知风险与张力（需在设计阶段解决）

1. **"实质性修改"（3.6）与"不破坏可编译"的张力**：允许结构性改写后，破坏 `\cite/\ref/\label/`公式/环境 的风险上升。第一版不真编译，更难自动发现破坏。
   - 待定缓解方向：对数学环境、引用、标签等"危险元素"做保护性处理或改前改后比对校验；这部分需要在正式设计时给出明确方案，而不是简单"只改措辞"。
2. **进阶版自动改原文 / 原 bib（3.5）**：直接改用户文件，错误代价高，必须可回滚、可对比、可关闭（默认基础版）。
3. **防幻觉（3.3）在"无 bib 自动生成"场景下压力最大**：完全没有用户文献时，全靠检索结果支撑，必须严格校验每条引用真实存在。
4. **成本与时延**：25 篇（可调）× 多阶段 LLM/HTTP 调用，单任务可能数分钟；需复用现有任务/SSE/pause-resume-stop，并做检索缓存与限流。
5. **隐私**：上传未发表论文到外部 LLM / 检索 API，需提示。

---

## 6. 正式文档转化状态（2026-06-16 已完成）

本纪要的全部设计结论已系统性转化进正式文档（修订 18）：

- ✅ `implementation.md`：第二期 D-01~D-07 / E-01~E-09 / F-01~F-05 / G + 总门禁按"LaTeX + 文献地基 + 研究建议"重写，原 docx 步骤作废。
- ✅ `paper-quality-plan.md`：整体重写为 LaTeX 方向（L0–L5、阶段链、占位保护、文献地基、gap、L1/L2、Prompt、三件套、验收）。
- ✅ `second-phase-design.md`：范围与卖点整体更新为 LaTeX 方向。
- ✅ `paper-polish-research.md`：第 12 节"绑定 LaTeX 工具链"由"刻意不采纳"改为"采纳"，顶部加方向变更横幅。
- ✅ `architecture.md`：新增第 18 节（论文模块架构：L0 解析、LiteratureService、研究画像/gap、数据模型、可恢复阶段步编排、两模式）。
- ✅ `abstract.md`：索引新增本纪要、刷新第二期阅读顺序。

---

## 7. 输入形态与可编译保护方案（已对齐）

### 7.1 输入形态（问题 A 结论）

- 第一版**同时支持 zip 项目包与单 .tex**；内部将项目**拍平成单一逻辑文档**后再分析。
- 要求能找到**唯一 main 入口**（含 `\documentclass` / `\begin{document}` 的 .tex），找不到则报错并提示。
- zip 内第一版只关心 `.tex` 与 `.bib`；图片二进制、`.cls/.sty` 等可忽略（不真编译、图建议只靠 caption）。
- `\input/\include` 递归展开（防循环）。
- **进阶版（改原文）第一版只支持单文件**；多文件的“采纳写回”需要 source map（每段对应哪个子文件/哪段行），后置。

### 7.2 实质修改的可编译保护（问题 B 结论）

**占位保护（mask）机制**：送模型前，把非正文的危险元素抠出换成不透明占位符（如 ⟦MATH_1⟧、⟦CITE_1⟧、⟦REF_1⟧、⟦FIG_1⟧）：

- 被保护：`$...$` / `\[..\]` / equation、align 等数学环境、`\cite \ref \label \eqref`、`\includegraphics`、figure/table/tabular 环境、未知自定义 `\命令`（宁可过度保护）。
- 模型规则：**正文可大改**（重组、补足论证、修逻辑顺序），但占位符只能保留/移动，**不能新造、不能删**。
- 返回后校验：输出占位符集合 ⊆ 输入集合，再原样换回真实 LaTeX。效果：正文大改也不会动坏公式/引用/标签。

**关键边界（重写已有 vs 新增内容）**：

- 进入“润色后 .tex”的：对**【已有内容】**的实质性重写、重组、强化论证、修逻辑断裂。
- 默认不自动进 .tex、只进【报告】的：增设新章节、补做新实验、新增引用（“无中生有”的部分）。

**静态 lint 兑底（代替编译）**：花括号配平、`\begin/\end` 配对、`\ref/\eqref` 有对应 `\label`、`\cite` key 在 .bib 存在、改前改后受保护元素数量一致、无残留占位符。

**三道安全网**：preamble 只读（不发给模型改）；按章节处理、爆炸半径小；进阶版改原文可回滚、可对比、默认关。

### 7.3 在线预览与逐条一键采纳（用户补充，已认可）

- 原“新增内容只能进报告”升级为：**新增内容做成可选补丁，用户预览后逐条决定加不加**；不愿意的可自行下载手动加。

### 7.4 由 7.3 引出、已确认的两点

1. **预览是源码级，不是渲染 PDF**（已确认）：第一版不编译，预览 = 展示 .tex 源码 + 改动/新增高亮（类 diff）+ 逐条勾选采纳 → 生成新 .tex。渲染效果预览后置（需先上编译）。
2. **新增内容诚实分级**（已确认）：
   - A 类·可插真内容（grounded）：相关工作段落、补充引用 —— 基于检索到的真实论文、用 suggested.bib 里 API 验证过的 key，可直接插。
   - B 类·只能插骨架：“补 XX 实验 / 加 XX 小节” —— 没有真实结果/数据，一键采纳只插入【小节骨架 + 占位说明 + 理由】，**绝不编造实验数字/结论**，占位处由用户自填。
3. 采纳后仍过静态 lint（已确认）；多文件的“采纳写回”仍后置（需 source map，已确认）。

---

## 8. L3 文献地基详细设计（已对齐）

### 8.0 可复用的现有栈

项目已有：ES（`ElasticsearchKnowledgeIndexService` 等）、DashScope 嵌入（`DashScopeEmbeddingClient`/`VectorizationService`）、混合检索（`HybridKnowledgeSearchService`）。L3 应**复用**这些，不重建。

### 8.1 身份主键 identity（问题 1，认可）

不用纯标题做 key。优先级：`DOI > arXiv id > OpenAlex id / S2 id > 规范化标题哈希`。每条卡片同时存所有源 id；标题仅显示 + 模糊兑底。

### 8.2 缓存分三层

- L3a 原始返回(raw)：可选，TTL 短或不存。
- L3b 规范化卡片(card)：必存，长期。
- L3c LLM 分析(method/datasets/baselines...)：必存，长期，与卡片绑定。摸过一次永久复用，避免重复调 LLM。

### 8.3 存储分工：MySQL + ES（问题 2，认可后置但留入口）

- **MySQL = 账本**：卡片权威记录 + identity 唯一约束 + 多源 id + L3c 分析 + 任务-文献关联与排序分。
- **ES = 语义检索引擎**（复用 Hybrid 栈）：跨任务/跨用户语料召回与排序。
- **分期**：第一版卡片+分析进 MySQL，25 篇候选用**内存向量 cosine** 排序；ES 后置。**但需预留入口**（排序/召回抽象），后续接 ES 直接插入。

### 8.4 文献卡片字段（问题 3，加 tasks[]/keywords[]）

```text
identity: doi / arxivId / openAlexId / s2Id / titleHash
title, authors[], year, venue, abstract, url/pdfUrl
citationCount, referencedWorks(可选), fieldsOfStudy[], source[]
-- LLM 分析(L3c) --
problem, method, datasets[], baselines[], metrics[],
contributions[], tasks[], keywords[], analyzedAt(含模型版本)
```

### 8.5 相关性排序（问题 4，认可，接受源信号丰富度不同）

相对“用户这篇论文”打分，信号：语义相似度(主)、引用重叠(强，OpenAlex 有/arXiv 无)、关键词/领域重叠、时效性(轻)、影响力(轻)、已在 .bib 降权/标记。第一版简单加权和，权重可配。

### 8.6 “创新性排序”（问题 5，待用户最终确认）

用户原意是“对召回文献做相关性与创新性的判断与排序”。两种含义：
- A 去冗余/覆盖度（MMR）：用于选 top N，保证多样性。
- B 找差异/SOTA：用于 L4 gap 分析，挑“它有你没有”。

**初步推荐（待确认）**：L3 选片阶段的“创新性”按 A（MMR 去冗余）理解；B 作为 L4 责任，不单独当一个排序项。用户当前未最终拍板。

### 8.7 L3 完整流水

```text
研究画像 → 生成多个检索 query → 各源召回(较多 60~100)
  → identity 去重 + 与用户 .bib 去重标记
  → 查 MySQL 缓存：命中取卡片+分析，未命中才往下走
  → 读摘要 → LLM 抽取(L3c) → 落 MySQL
  → 相关性打分 + MMR 去冗余 → 选 top N(默认25,可调)
  → 交给 L4
```

原则：先召回多、去重、查缓存，最贵的 LLM 分析放最后且只对“未命中且入选”的那批做。

---

## 9. L3 召回策略与立场区分（已对齐）

### 9.1 铁律：两件立场相反的事必须分开

```text
引言 / 相关工作生成 = 站在【用户这边】（辞护 / advocacy）
   目的：凸显用户论文的贡献与优势
   手段：找【现有方法的局限】来反衷 → “别人有局限X，本文解决了X”
   绝不：找一堆比用户更强的论文来贬低用户

审稿意见 / review = 站在【审稿人这边】（批评 / critique）
   目的：帮用户发现不足、明确改进方向
   手段：可指出“别人比你强”“你这里站不住”
```

同一批召回文献，用法相反：局限性文献→进引言反衷用户；“比用户强”文献→进审稿意见，不进引言、不在引言里贬低用户。

### 9.2 概念阶梯节点 = 优势组 + 劣势组，各自挂文献

```text
某体制/方法
  ├─ 优势：…（引 2~3 篇）
  └─ 劣势：…（引 2~3 篇） ← 劣势承上启下，通向下一层/本文贡献
```

### 9.3 多轴拆解 + 分层配额选片

- 拆解轴：体制/架构阶梯 + 任务/问题轴 + 方法/技术轴 + 特征轴；每轴/每层各生成 query。
- 召回双驱动：引言驱动（用户已对标的）+ 内容驱动（方法/实验揭露的真实邻域）并集。
- 选片：**分层配额（每层保底 K 篇，含奠基作）+ 层内 MMR 去冗余**，不纯全局 top-N（否则阶梯顶层奠基文献会被挤掉）。
- 中英 + 同义词扩展（极化=polarimetric, FDA=frequency diverse array）。

### 9.4 选片标准随用途分叉

```text
为引言选片：优先“有局限、且该局限正被本文方法解决”的文献 + 阶梯各层代表作
为审稿选片：优先“强竞争/SOTA、暴露本文不足”的文献
```

“相关性”不是单一维度，而是“对当前叙事目标（辞护/批评）的有用性”。

### 9.5 引言充分性评估 → 分支处理

```text
先评估用户引言是否充分、合理（对比维度够不够？有没有凸显本文优势？）
  ├─ 合理：①按引言检索 ②用文献反查引言断言是否真实可靠 ③推荐补充好文献丰富
  └─ 不合理：①指出缺哪些对比维度/怎么重组凸显贡献（辞护生成）
                ②同时把“写得不行”的记进审稿意见（批评）
```

- 反查引言真实性：用真实文献校验用户断言（“X方法有局限Y”文献支持吗？还是X已解决Y？）。
- “丰富引用”是低拒绝率高价值动作。

### 9.6 诚实边界

引言只用**真实、可溯源的局限**反衷；若发现用户其实没优势，**不在引言里硬吹/编造别人局限**，而是在审稿意见里如实告知。能反衷就反衷，反衷不动就进审稿意见。

### 9.7 文献检索作为可复用内置能力（已对齐）

用户可能直接在对话窗口说“查极化FDA-MIMO文献”，所以文献检索做成**全项目共享的内置能力**，论文流水线与对话 Agent 都复用。

**分层（该共享 vs 不该共享）**

```text
不共享（论文专属编排）：概念阶梯拆解、多轴、分层配额、辞护/批评选片、引言充分性评估 —— 留论文模块
共享（通用核心）：查 + 去重 + 缓存 + 卡片 + 排序
```

**架构**

```text
LiteratureSource providers（OpenAlex / arXiv / S2可插拔）
        ↓
LiteratureService（通用内置：查+去重+缓存+卡片+排序，分 LIGHT/DEEP）
        ↓                                   ↓
对话内置工具（Agent 调，默认 LIGHT）      论文 L3 流水线（DEEP + 阶梯/剧本编排）
        ↓                                   ↓
              共用同一个 MySQL 缓存 + 文献卡片 DTO + bib 生成
```

**已确认决定**

1. 通用层只做“查+去重+缓存+卡片+排序”，阶梯/剧本逻辑留论文模块。
2. 分 **LIGHT/DEEP** 两档：LIGHT=检索+基础卡片不调 LLM（对话默认）；DEEP=加 LLM 深度分析（论文流水线，对话可按需升级）。
3. 对话里做成**自家内置工具（function calling，非 MCP）**，Agent 自动调用。
4. 缓存**只存公开文献卡片，绝不存用户上传论文的私有内容**；跨用户共享。
5. 文献语料若上 ES 用**独立索引**，与私有知识库 KB 分开。

**配套注意**：治理集中（限流 / mailto 礼貌池 / S2 key 可插 / 默认25可调 / 每用户配额）；过滤参数（year/type/venue/source）；统一卡片 DTO；bib 导出复用 suggested.bib 逻辑。

---

## 10. L0 LaTeX 解析与章节角色识别（已对齐）

### 10.1 解析策略：务实分词器，非完整解析

LaTeX 图灵完备、通用不可解析；Java 也无可靠解析库。只识别少数关心 token，其余当不透明文本：

```text
1. 分节命令：\part \chapter \section \subsection \subsubsection \paragraph（含 *）
2. 环境边界：\begin{xxx} ... \end{xxx}
3. 受保护内联：$...$  \[..\]  \cite系  \ref系  \label  \includegraphics  \input  \include
4. 注释：%（\% 是转义，不算）
其余全部 = 不透明正文
```

tex 自写轻量分词器（不引第三方 LaTeX 库）；.bib 可用现成库或自写。

### 10.2 文档模型（含 A–E 补充）

```text
Document
├─ preamble        \begin{document} 之前，只读；但抽取元数据 title/authors/keywords  (A)
├─ sections[]      level / title / role / rawRange / blocks[](正文与受保护span交错)
├─ protectedSpans[]  数学/环境/cite/ref/label/includegraphics（占位保护来源）
├─ floats[]        kind / label / caption / graphics[] / referencedBy[] / rawContent(表格原文) (B)
├─ citeUsage[]     认全部引用命令 \cite \citep \citet \citeauthor \parencite \autocite ... (C)
├─ crossRef        认全部 \ref 家族 \ref \eqref \cref \Cref \autoref \pageref (D)
├─ bib             外部 .bib（\bibliography/\addbibresource）+ 内联 \begin{thebibliography} \bibitem (E)
└─ sourceMap       节级 + 节内字符区间（多文件写回用）
```

要点：preamble 永远只读（但抽元数据）；protectedSpans 是 mask 数据源；floats.referencedBy 为空=图未被引用。
**E 最关键：支持内联 thebibliography，否则会拒掉不用 .bib 文件的合法论文。**

后置（F–G）：equations[] 当一等引用实体；footnote 内正文/引用。第一版不做。

### 10.3 章节角色识别：三级方案

```text
第1步 启发式：关键词(中英)+位置+内容信号 → 初步 role + 置信度
第2步 LLM 复核：只发【标题列表+顺序+少量信号】，不发全文；
              LLM 在【固定角色枚举】内确认/纠正 + 指出缺哪些标准角色
第3步 对账：一致→高置信；不一致→采用 LLM 但记日志
UI   用户可改（第一版必须有）：用户是最终权威
```

角色枚举：Intro / RelatedWork / Method / Experiments / Results / Discussion / Conclusion / Abstract / References / Appendix / unknown。
防护：LLM 只能从枚举选，输出按枚举校验，不许发明新角色。
副产物：识别缺失的标准角色（如无 RelatedWork）→ 喂 L2/L4 “你缺 XX 部分”。

### 10.4 边界与特殊情况

```text
verbatim/lstlisting/minted → 整块不透明，不解析内部
\newcommand 重定义分节命令 → 第一版不支持
注释 % → 分析忽略，写回保留（注意 \% 转义）
\section* → 识别并标 unnumbered
appendix → Appendix 角色
ctex/CJK 中文 → 支持
theorem/proof/algorithm → 受保护环境，不拆内部
```

### 10.5 source map 粒度

第一版：**节级 + 节内字符区间**（够 section-level 润色与插入）；多文件细粒度精确写回随多文件进阶版后置。

---

## 11. L4 gap 分析与可采纳补丁（已对齐）

### 11.1 L4 吃什么、吐什么

```text
吃：L0 章节结构/角色/缺失角色/锡点 + L3 研究画像/概念阶梯/选中卡片/gap矩阵
吐：1) tex 可采纳补丁（相关工作/贡献陈述，辞护轨）
     2) 审查报告（缺实验/缺章节/缺baseline/改进方向，批评轨）
     3) suggested.bib
```

### 11.2 统一建议对象 Suggestion

```text
Suggestion {
  id
  track:      ADVOCACY(辞护) | CRITIQUE(批评)        ← 决定进 tex 还是报告
  category:   RelatedWork/Contribution/Citation/Baseline/Ablation/Experiment/Section/Dimension...
  severity:   blocker | minor                          ← 仅批评轨
  statement:  人话描述
  evidence:   [真实文献卡片 id]                     ← grounding（铁律D）
  applicable: 能否变一键补丁
  patch?: { contentType: A(真内容)|B(骨架), anchor, latexSnippet, addedBibKeys[] }
}
```

### 11.3 A/B 判据改为“能否 grounding”（修正早期“新/旧”）

```text
A 类(可插真内容) = 能被【真实文献/事实】支撑
   → 相关工作段、补充引用、靠“对比别人真实局限”得出的贡献陈述；
     哪怕是新建 RelatedWork 节，只要 citation 是真的也是 A 类
B 类(只插骨架) = 需要【我们没有的数据/结果】
   → 补实验、新方法细节、任何带数字/结论的；一键采纳只插骨架+占位+理由
```

判据 = 能不能 grounding，不是新/旧。

### 11.4 两条立场轨道分流

```text
ADVOCACY → tex 补丁：相关工作段(节点=优势引文+劣势引文→本文贡献)、贡献陈述
CRITIQUE → 审查报告：缺baseline/缺消融/缺章节/维度不够/反查发现的站不住断言；
           可带 B 骨架补丁；结尾免责声明（AI 自查、非同行评审）
```

### 11.5 诚实闸门

贡献陈述只在“找到别人真实局限且本文确实解决”时生成；找不到支撑→不在 tex 硬吹，转 CRITIQUE 进报告。绝不编造别人局限来吹用户。

### 11.6 错点靠 L0 角色

```text
相关工作补丁→RelatedWork节；缺失→新建(A类真引用)
贡献陈述→Intro末尾
实验骨架→Experiments节；缺失→新建(B类骨架)
```

### 11.7 grounding 与采纳后校验

每条 evidence 必是 L3 真实卡片；patch 里每个 \cite key 必能解析到 suggested.bib；用户采纳后仍过 L0 静态 lint。

### 11.8 优先级 + 分组 + 配额

按 severity + 影响面排序；按章节分组；每类默认约 5 条可“展开看更多”。

### 11.9 结构确认检查点（已对齐）

问题：Related Work 等放哪是风格选择（有的单列、有的揉进引言）；误判会“一步错步步错”。在 L0 与 L4 之间加一道检查点：

```text
1. 按【内容位置】判是否真缺失（引言里有引用密集对比内容 → “融在引言”而非缺失）
2. 检测到歧义→不下结论，问用户，给选项，默认高亮“保持我的安排”
3. 复用 pause/resume + SSE 做交互检查点
4. 批量问一次 + “全部保持原样”一键通过；只问真歧义
5. 区分阻塞类（先问）vs 提示类（带默认继续）
6. 默认尊重用户结构，只有明确同意才重构
下游：选“保持”→不建议新建、gap 不算缺失；选“拆分”→才生成新建节补丁
```

---

## 12. 整体数据模型 / 表结构（已对齐）

### 12.0 一期现状（已读 V5/V6 + 实体）

```text
paper_tasks       : id,user_id,title,source_filename,object_key(输入→MinIO),
                    final_object_key(结果→MinIO),status,target_language,
                    current_stage,error_message,时间戳
paper_task_rounds : id,task_id,round_number,stage,status,input_text(LONGTEXT),
                    output_text(LONGTEXT),notes,时间戳；唯一键(task_id,round_number,stage)
```

文件已走 MinIO（object_key/final_object_key）；已有“任务头 + 轮次事件日志”两层。

### 12.1 复用/新建 = 混合（决定1）

```text
paper_tasks       → 扩展（加列：input_format(tex/zip)、mode(basic/advanced)、main_entry 等）
paper_task_rounds → 复用，当 SSE 背后的阶段事件日志
新增结构化表   → sections / 文献卡片 / suggestions / 各关联表
```

### 12.2 文件/产物进 MinIO（决定2）
沿用 object_key 模式；润色后 tex / suggested.bib / 审查报告 / 完整解析模型 JSON 各给 object_key（见 12.9）。

### 12.3 文档模型粒度 = 混合 C（决定3）

```text
paper_sections（行）: task_id,order_index,level,title,role,role_confidence,
                      role_source(heuristic/llm/user),char_start,char_end,polish_status,...
完整解析细节（protectedSpans 偏移/floats/citeUsage/blocks）
   → 体量可能大，做成 JSON 产物存 MinIO，orchestrator 整体加载
```

只有 sections 需要按行查/挂状态（按节润色/挂建议/断点续传/用户改角色）；其余整体加载，JSON 最省。

### 12.4 文献身份去重 = 显式 id 列 A + upsert-by-any-id（决定5）

```text
literature_cards 上：doi/arxiv_id/openalex_id/s2_id 各可空唯一索引 + title_hash
去重/合并流程：插入前用【所有已知 id】逐列匹配；
   命中任一 → enrich 既有行（补 id/缺失字段）；全不命中 → 新建
   （解决“先 arxiv 后 DOI”合并，不靠 canonical key）
```

### 12.5 Suggestion 落表（决定6）

```text
suggestions(task_id,section_id FK可空,track,category,severity,statement,
            applicable,patch JSON,status[proposed/accepted/rejected])
suggestion_evidence(suggestion_id,card_id)  —— 多对多→ grounding 完整性
patch 嵌 JSON（整体消费）
```

### 12.6 任务-文献关联（决定7）

```text
paper_task_literature(task_id,card_id,relevance_score,
   narrative_role[advocacy/critique],ladder_node,selected,source_query)
卡片全局共享，每任务的分数/立场放这里，不污染全局卡片。
```

### 12.7 交互检查点状态（决定8）

```text
paper_task_clarifications(task_id,type,question JSON,options JSON,
   status[pending/answered],user_answer,created_at,answered_at)
独立表，pause/resume 需干净的“待回答”状态，不塞 rounds。
```

### 12.8 采纳状态（决定9）
采纳 = suggestions.status。

### 12.9 产物表（决定9）

```text
paper_task_artifacts(task_id,type[polished_tex/suggested_bib/review_report/parsed_model],
   object_key,version,created_at)
多产物 + 可重生版本，用表比在 paper_tasks 堆 object_key 列干净。
```

### 12.10 概念阶梯/分析快照（决定10）

```text
paper_task_analysis(task_id,research_profile JSON,concept_ladder JSON,gap_matrix JSON,...)
按任务持久化为 JSON；支持断点续传 + 可解释展示；整体消费不拆行。
```

### 12.11 缓存版本/失效（决定11）
literature_cards 加 fetched_at / analyzed_at / analysis_model_version；模型版本变→惰性重算。

### 12.12 表清单（公私分离）

```text
全局/公开（无 userId）：
   literature_cards          （卡片 + L3c 分析 + 版本）
私有/按任务（带 userId/taskId）：
   paper_tasks(扩展)          paper_task_rounds(复用,事件日志)
   paper_sections             paper_task_analysis
   suggestions                suggestion_evidence(→全局卡片)
   paper_task_literature(→全局卡片)
   paper_task_clarifications  paper_task_artifacts
```

---

## 13. 整体编排：状态机 / SSE / pause-resume-stop / 交互检查点（已对齐）

### 13.0 一期现状

```text
status: RUNNING / PAUSED / STOPPED / COMPLETED / FAILED
stage : SUMMARY → SECTIONS → PAPER_REVIEW → ABSTRACT → REFERENCES → COMPLETE
pause/resume/stop：内存 ControlState{paused,stopped}；checkpoint() 阶段间调（stopped 抛/paused 忙等）
SSE：PaperEventStreamService（emitter+history 回放）；Executor 跑 runTask
```

机制对，但为“短骨架流程”设计，长流水线需重构。

### 13.1 新阶段列表（决定1，GAP 先 POLISH 后）

```text
PARSE            L0：解压/拍平、tex 分词、建文档模型、解析 bib
ROLE_RECOGNITION L0：启发式 + LLM 角色复核
STRUCTURE_CHECK  交互检查点：检测歧义 → 有阻塞歧义则等用户
PROFILE          L3：结构化研究画像
RETRIEVE         L3：概念阶梯/多轴/多源召回/去重/缓存/卡片(DEEP)/排序/MMR选片
GAP_ANALYSIS     L4：对比矩阵 + suggestions（辞护+批评）
POLISH           L1：占位保护分章润色 + 审查回环
ASSEMBLE         产出润色 tex + suggested.bib + 审查报告 三件套
COMPLETE
```

GAP 先于 POLISH（辞护轨相关工作补丁先就位）。

### 13.2 新状态 WAITING_INPUT（决定2/3）

```text
手动 PAUSE      → 沿用忙等（短）
WAITING_INPUT  → 【释放线程】：写 paper_task_clarifications(pending)，
                 status=WAITING_INPUT，推 SSE clarification_needed，runTask 结束不占线程
用户回答后   → resume 重新派发异步执行，从【下一阶段】继续
```

### 13.3 可恢复阶段步重构（决定3）

```text
每阶段 = 一个可恢复步：从持久化状态读输入 → 干活 → 输出落库/落MinIO
dispatcher：根据 task.current_stage 从当前阶段往后依次跑
PAUSE：协作 flag（阶段内短点用）；WAITING_INPUT：结束线程，答后从下一步重启
```

好处：交互检查点天然支持、顺带崩溃恢复能力、断点续传不丢；代价：每阶段读持久化输入/写持久化输出。

### 13.4 崩溃恢复 v1 做方案 A（决定4）

```text
v1 (A)：阶段可恢复 + 等用户续跑；服务器崩溃后不自动续，RUNNING 任务手动重启/重跑
v1.1 (B)：启动扫 RUNNING 任务从 current_stage 自动续跑（后置）
```

### 13.5 长阶段频繁 checkpoint（决定5）
RETRIEVE 每批文献一次；POLISH 每章/每轮一次。

### 13.6 SSE 事件清单（决定6，保留旧的兼容）

```text
parse_start/parse_done, roles_ready, clarification_needed/clarification_resolved,
profile_ready, retrieve_start/retrieve_progress/cards_ready/ranking_done/selection_done,
gap_analysis_done/suggestions_ready, section_polish_start/section_polished/polish_done,
assemble_done/artifact_ready, complete, error, paused/resumed/stopped
```

---

## 14. L1 润色执行与 L2 结构审查（已对齐）

### 14.1 L1 占位保护润色—逐章执行步骤

```text
对每个 section（preamble 与 References 节除外）：
1. 取章节内容（正文 + 受保护 span）
2. mask：受保护 span → 占位符 ⟦CITE_1⟧⟦MATH_1⟧...
3. 调 section-polish（允许实质重写；必须保留占位符；标签式 <output>/<explanation>；
     带 targetLanguage + 全文画像上下文）
4. 校验输出占位符 ⊆ 输入集合
5. unmask：占位符换回真实 LaTeX
6. 静态 lint（括号/环境配平、占位符全部还原）
7. section-review（独立 prompt，JSON：score + issues[severity] + suggestions）
8. score 不达标且 attempt < maxAttempts → 带审查意见重试
9. 后端自算 diff（词级，给预览）
10. 落库：polish_status / 润色文本 / 审查结果 / diff
```

决定：
- maxAttempts=2（两轮上限）、score 阈值中档（妀80），均可配。
- 掉占位符：拒绝该次输出并重试；重试仍丢 → 保留原章节不润色（宁可不改不破坏）。
- 超长章节：默认整章一次；超上下文则按子节/段落切块。
- 特殊角色：References 不润色；Abstract 用摘要专用 prompt；其余正常。
- 实质重写边界：允许重组/强化论证/修逻辑；不编实验/数据/引用（那些走 L4）。

### 14.2 L2 结构审查—检查项清单（纯静态、不编译）

**硬性 lint（blocker，可早在 PARSE 报）**
```text
- \cite 的 key 不在 bib（悬空引用）
- \ref/\eqref 无对应 \label（断引）
- 括号 / \begin..\end 不配平
- bib 重复 key
```

**软性审查（minor，进报告/建议）**
```text
- 缺标准章节（Intro/Method/Experiments/Conclusion）—仅 STRUCTURE_CHECK 确认后才报
- 章节顺序异常（如 Conclusion 在 Experiments 前）
- 空/过短章节
- bib 条目从未被引用（孤立文献）
- \label 从未被引用
- 图/表从未被正文 \ref（floats.referencedBy 空）
- 图/表无 caption
- 相关工作/引言 引用密度过低
```

> 公式 label 未被 \eqref（需 equations[]，F 项后置）—v1 不做。

决定：
- 严重度：悬空引用/断引/不配平/重复 key = blocker；其余 = minor。
- L2 跑在哪：硬 lint 在 PARSE 阶段就跑（早暴露）；软审查在 GAP_ANALYSIS 汇入 suggestions。
- 缺章节检查必在 STRUCTURE_CHECK 之后（尊重用户“揉进引言”选择）。
- 软审查结果都转成 Suggestion（多为 CRITIQUE；缺章节等结构类经用户确认可转辞护补丁）。

---

## 15. 一句话现状

方向已从"论文润色"演化为"**面向 LaTeX 的、有据可查的 AI 审稿/改稿 + 文献补全助手**"。地基是 L0（LaTeX 解析）+ L3（文献地基），卖点是 L4（研究级建议）与"无 bib 自动生成"。下一步需先敲定输入形态（单文件 vs 项目包）与"实质修改 vs 可编译"的缓解方案，再系统性改文档。
