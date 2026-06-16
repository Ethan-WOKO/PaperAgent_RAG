# memory-bank 文档摘要

> 用途：快速查看 `memory-bank/` 中每份文档的名称与作用。后续若文档数量增多，优先先读本摘要，再决定进入哪一份详细文档。

---

## 1. `design-doucment.md`
**作用**：产品设计基线。记录产品目标、范围、功能边界、长期需求决策。

## 2. `tech-stack.md`
**作用**：技术栈与环境基线。记录语言、框架、中间件、版本组合与基础设施选型。

## 3. `implementation.md`
**作用**：执行计划主文档。记录 A/B/C 阶段要做什么、执行顺序、验证方式与后续计划。

## 4. `architecture.md`
**作用**：实现后架构现状文档。记录模块职责、关键技术取舍、与原始设计相比的架构偏差。

## 5. `progress.md`
**作用**：进度与结果文档。只记录已完成事项、已验证结果、当前已确认问题；不再记录未来执行计划。

## 6. `revision-log.md`
**作用**：修订流水。记录每一次实际代码修改、关键配置调整与修复动作。

## 7. `test-checklist.md`
**作用**：手动测试清单。记录门禁测试顺序、真实手测结果、待补测试项。

## 8. `design.md`
**作用**：前端视觉细化主文档。记录前端设计方向、页面风格、交互规范，以及前端实施文件清单。

## 9. `fronted_fix_plan.md`
**作用**：兼容性保留文件。原“前端视觉细化实施文件清单”已合并进 `design.md`，本文件不再单独维护。

## 10. `second-phase-design.md`
**作用**：第二期设计补充（LaTeX 方向）。记录第二期产品范围、优先级、非目标、关键产品改进与风险控制；不记录具体执行步骤。

## 11. `paper-quality-plan.md`
**作用**：论文质量专项计划（LaTeX 方向）。记录能力分层 L0–L5、阶段链、占位保护、文献地基、gap 分析、L1/L2、Prompt 分层与验收方式。

## 12. `paper-polish-research.md`
**作用**：论文润色工具与方案调研（调研历史 + 开源项目精华提炼）。注：第二期已转 LaTeX 方向，本文 docx 部分仅作历史保留。

## 13. `discussion_about_fix_paper_20260615.md`
**作用**：第二期 LaTeX 论文模块的权威设计讨论纪要（共 15 节）。含方向定位、能力分层 L0–L5、占位保护、L3 文献地基与立场区分、L0 解析、L4 gap 分析、整体数据模型、编排状态机、L1/L2；所有决策点均已对齐。推进第二期前应先读。

---

## 当前推荐阅读顺序

### 如果你想了解“产品到底要做成什么”
1. `design-doucment.md`
2. `tech-stack.md`

### 如果你想知道“接下来该做什么”
1. `implementation.md`
2. `progress.md`
3. `test-checklist.md`

### 如果你想知道“现在代码结构是什么样”
1. `architecture.md`
2. `revision-log.md`

### 如果你想继续推进前端细化
1. `design.md`
2. `progress.md`
3. `test-checklist.md`

### 如果你想推进第二期（LaTeX 论文模块）
1. `discussion_about_fix_paper_20260615.md`（权威设计纪要，先读）
2. `second-phase-design.md`
3. `paper-quality-plan.md`
4. `implementation.md`（第二期 D/E/F/G）
5. `test-checklist.md`
6. `paper-polish-research.md`（调研背景，选读）

---

## 当前文档整合说明

- 前端视觉方向与实施清单已统一收口到：
  - `design.md`
- `fronted_fix_plan.md` 仅保留为旧引用入口，不再单独维护。
- C 阶段计划已统一收口到：
  - `implementation.md`
- 第二期设计补充已新增：
  - `second-phase-design.md`
- 第二期论文质量专项计划已新增：
  - `paper-quality-plan.md`
- `progress.md` 不再承载未来执行计划，只保留结果与状态。
