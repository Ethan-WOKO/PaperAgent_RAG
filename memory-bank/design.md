# 研伴 Agent 前端视觉细化方案（ChatGPT 风格）

> 文档作用概述：记录**前端视觉方向、交互风格与页面级设计规范**。原 `fronted_fix_plan.md` 中的实施文件清单已并入本文件，后续前端视觉相关内容统一维护在这里。

## 1. 目标

把当前前端从“功能可用”细化为：

- 更像成熟 AI 产品
- 更统一
- 更耐看
- 更适合长时间使用
- 更突出“对话 + 知识库 + 论文流程”主线

整体参考方向：

> **ChatGPT 风格 + 少量管理台能力**

即：

- 对话页尽量像 ChatGPT
- 管理页保留简洁卡片式后台风格
- 全站统一深浅色、圆角、间距、按钮、状态色

---

## 2. 总体设计原则

### 2.1 关键词
- 简洁
- 克制
- 留白
- 层级清晰
- 聚焦内容
- 少装饰

### 2.2 设计目标
用户打开后应感受到：

- 主体内容很突出
- 不乱
- 有“AI 产品感”
- 不像传统后台 CRUD 页

---

## 3. 风格基调

### 3.1 整体布局
建议采用：

- 左侧：导航 / 会话 / 功能入口
- 右侧：主工作区
- 顶部：轻量标题栏或页面工具栏
- 内容区：卡片弱化，主体强化

参考 ChatGPT 的特点：

- 大面积平面背景
- 很少重边框
- 间距舒服
- 输入区明显
- 内容区域滚动自然

### 3.2 颜色建议

#### 浅色主题
主背景：
- `#F7F7F8`

内容面板：
- `#FFFFFF`

次级背景：
- `#F3F4F6`

边框：
- `#E5E7EB`

正文：
- `#111827`

次级文字：
- `#6B7280`

主色：
- `#10A37F`

危险色：
- `#DC2626`

成功色：
- `#16A34A`

警告色：
- `#D97706`

信息色：
- `#2563EB`

#### 深色主题（后续可选）
如果后续做深色模式，可参考 ChatGPT 深色风格：

- 背景偏黑灰，不要纯黑
- 内容区域稍亮一层
- 绿色主色保留

---

## 4. 基础视觉规范

### 4.1 圆角
统一圆角，不要一页一个风格。

建议：
- 小按钮：`8px`
- 输入框：`10px`
- 卡片：`14px`
- 大面板：`16px`

### 4.2 阴影
ChatGPT 风格阴影很轻。

建议：
- 常规卡片：轻阴影或无阴影
- 弹层 / 浮层：中等阴影
- 避免重阴影和强拟物

### 4.3 边框
统一使用浅边框：

- `1px solid #E5E7EB`

避免：
- 粗边框
- 花哨描边
- 多层嵌套边框

### 4.4 间距
建议用统一 spacing 体系：

- 4
- 8
- 12
- 16
- 24
- 32

页面不要出现忽大忽小的留白。

### 4.5 字体层级
建议统一：

- 页面标题：24 / semibold
- 卡片标题：18 / semibold
- 正文：14~16
- 辅助文字：12~13
- 状态标签：12

---

## 5. 页面级细化建议

## 5.1 `/chat` 对话页
这是最核心页面，优先级最高。

### 目标
尽量接近 ChatGPT 使用体验。

### 建议改法

#### 布局
- 左侧会话栏固定宽度
- 右侧对话区占主要空间
- 输入框固定在底部
- 消息区居中、最大宽度限制

#### 会话栏
- 列表项更简洁
- 当前选中项高亮但克制
- 新建按钮更醒目
- 会话 meta 信息弱化显示

#### 消息气泡
建议减少“传统聊天气泡感”，更偏 ChatGPT：

- 用户消息：右侧浅色块
- 助手消息：左侧无强气泡，偏内容块
- 消息宽度不要太满
- 行高加大，提升可读性

#### 输入区
- 做成底部固定输入面板
- textarea 更大更圆润
- 发送按钮更清晰
- Skill 选择 / RAG 开关作为输入区上方轻量工具栏

#### 建议新增
- 助手头像/标识统一
- 代码块样式更精致
- 长回答段落 spacing 优化
- loading 状态更自然

### 当前已知需一起处理
- Skill prompt 不应直接显示为“你”
- 中间工具处理结果不应原样污染最终聊天内容

这个不只是功能修复，也直接影响视觉体验。

---

## 5.2 `/knowledge-base`

### 目标
从“上传列表页”细化成更现代的文档工作台。

### 建议改法
- 顶部做成页面说明 + 上传主操作区
- 文档列表改成更清晰的表格 / 卡片混合样式
- 状态标签统一颜色：
  - READY 绿色
  - PROCESSING 蓝色/橙色
  - FAILED 红色
- 上传过程增加更明确进度反馈
- 删除按钮弱化，避免误触
- 空状态更精致

### 建议突出
- 文档名
- 状态
- 是否公开
- 错误信息
- 上传时间

---

## 5.3 `/knowledge-base/search-debug`

### 目标
保留调试属性，但不显得像“开发临时页”。

### 建议改法
- 顶部说明“用于验证检索命中情况”
- 查询框更大
- 结果卡片化
- 每条命中结果突出：
  - 文件名
  - chunk index
  - score
  - 命中文本高亮

### 适合做成
“搜索框 + 命中列表”的简洁结构。

---

## 5.4 `/paper`

### 目标
做成“三步式工作流页面”，而不是一个普通上传页。

### 建议改法

#### 顶部
展示步骤感：
1. 上传论文
2. 处理中
3. 下载结果

#### 中部
- 左侧：上传与任务操作
- 右侧：SSE 日志 / 当前状态 / 结果摘要

#### 状态展示
- 当前阶段做成 timeline / stepper
- 日志做成可滚动区域
- pause / resume / stop 按钮统一风格

#### 下载区域
- 完成后明显显示“下载结果”
- 避免下载按钮埋得太深

---

## 5.5 `/settings`

### 目标
从“配置堆叠页”优化成分组清晰的设置中心。

### 建议分区
1. 模型设置
   - defaultProvider
   - DeepSeek / GLM key
   - model
   - temperature

2. Agent 设置
   - max_steps
   - 默认 RAG

3. MCP 设置
   - GitHub PAT
   - filesystem roots

4. Skills 设置
   - 可用 skill 列表
   - 启用/禁用开关

### 视觉建议
- 分组卡片
- 每组有标题和说明文字
- 输入框不要过满
- 开关和说明同行展示

---

## 5.6 登录 / 注册页

### 目标
做成简洁 AI 产品登录页，而不是传统表单页。

### 建议改法
- 页面居中
- 卡片更轻
- 品牌标题更突出
- 副标题说明产品定位
- 输入框更大
- 按钮更统一

---

## 6. 组件级统一规范

### 6.1 按钮
建议只保留少量风格：

- 主按钮：绿色实心
- 次按钮：浅底或描边
- 危险按钮：红色
- 文本按钮：弱化

### 6.2 标签 / 状态
统一封装状态样式：

- READY
- PROCESSING
- FAILED
- COMPLETED
- RUNNING
- PAUSED

### 6.3 输入框
统一：
- 高度
- 圆角
- focus 状态
- placeholder 颜色

### 6.4 卡片
卡片不要太重：
- 标题简洁
- 内容留白足够
- 少嵌套

---

## 7. 动效建议

ChatGPT 风格不是炫技型，而是轻动效。

建议：
- hover 微弱变化
- 页面切换不要过度动画
- 消息出现可轻微 fade-in
- 上传/处理中用简洁 loading

避免：
- 复杂弹跳
- 重浮动
- 太花哨的转场

---

## 8. 优先级建议

### 第一优先级
1. `/chat`
2. 全局布局与颜色体系
3. `/paper`

### 第二优先级
4. `/knowledge-base`
5. `/settings`

### 第三优先级
6. `/search-debug`
7. `/login`
8. `/register`

---

## 9. 推荐实施方式

### 第一步：先统一全局设计令牌
先定：
- 颜色
- 圆角
- 间距
- 字体
- 边框
- 按钮规范

### 第二步：先重做 `/chat`
因为这是感知最强的页面。

### 第三步：再做 `/paper` 和 `/knowledge-base`
把最重要业务页风格拉齐。

### 第四步：处理设置页和辅助页

---

## 10. 与当前功能问题的关系

视觉细化前，最好记住这几个功能问题也会影响观感：

1. `/chat` 普通 WS 路径未接 RAG
2. `code-review` 会暴露中间提示词与代码片段
3. CLI 还没命令化入口

其中第 1、2 项会直接影响“看起来是否专业”。

---

## 11. 实施文件清单（已从 `fronted_fix_plan.md` 合并）

### 第一阶段：先打全局基础
- `private-helper-agent/frontend/src/styles.css`
- `private-helper-agent/frontend/src/App.vue`
- `private-helper-agent/frontend/src/main.ts`
- `private-helper-agent/frontend/src/ui.ts`
- `private-helper-agent/frontend/src/components/AppLayout.vue`

### 第二阶段：核心页面 `/chat`
- `private-helper-agent/frontend/src/views/ChatPage.vue`

### 第三阶段：论文 / 知识库 / 设置
- `private-helper-agent/frontend/src/views/PaperPage.vue`
- `private-helper-agent/frontend/src/views/KnowledgeBasePage.vue`
- `private-helper-agent/frontend/src/views/KnowledgeSearchDebugPage.vue`
- `private-helper-agent/frontend/src/views/SettingsPage.vue`

### 第四阶段：认证页 / 收尾
- `private-helper-agent/frontend/src/views/LoginPage.vue`
- `private-helper-agent/frontend/src/views/RegisterPage.vue`
- 必要时补 `private-helper-agent/frontend/src/router/index.ts`

### 当前执行顺序建议
1. 全局样式与布局
2. `/chat`
3. `/paper`、`/knowledge-base`
4. `/settings`
5. `/login`、`/register`

## 12. 结论

### 这套方案的核心
不是简单“美化”，而是：

> **把研伴 Agent 做成更像 ChatGPT 风格的科研 AI 工作台。**

重点是：
- 对话页像 ChatGPT
- 管理页保持简洁
- 全站统一视觉语言
- 减少粗糙感和临时拼装感
