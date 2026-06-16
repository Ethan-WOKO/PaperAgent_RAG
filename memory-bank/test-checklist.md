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

### 说明
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
