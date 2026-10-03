# OpenAI Realtime Gateway（langchain4j-open-ai-realtime）设计规格

**日期：** 2026-09-04  
**仓库：** langchain4j-famdetxire（fork）  
**状态：** 待实现（brainstorming 已批准）

## 1. 目标

在 fork 中新增模块 `langchain4j-open-ai-realtime`，提供：

1. **出站** OpenAI Realtime WebSocket 客户端（GA 事件语义，对齐现网 antaios 已验证路径）。
2. **入站** WebSocket 网关：对外 API 事件形状尽量与 [OpenAI Realtime / Voice agents](https://developers.openai.com/api/docs/guides/voice-agents) 一致。
3. **工具**：以应用构建 Agent/Gateway 时注册的 `ToolSpecification` + `ToolExecutor` 配对为唯一权威；服务端执行 function call loop。

典型部署（验收场景）：

```
浏览器 ──入站 WS（类 OpenAI 事件）──► Gateway（本模块）──出站 WS──► OpenAI Realtime
```

## 2. 非目标（v1）

- 不实现 `ChatModel` / `StreamingChatModel` / `AiServices` 伪装接入。
- 不实现 WebRTC、SIP、Agents SDK handoff/guardrails 产品面。
- 不把 antaios `DynamicToolSpecificationBuilder` / `UnifiedToolExecutor` / HTTP Facade 搬进本模块。
- 不替换 antaios `OpenaiVoiceLlmOutCallProcessor`（可后续另开变更迁移）。
- 不做断线重连协议；不做慢工具填充语；不取消进行中的 tool 执行。

## 3. 架构（方案 1：单模块网关一体）

### 3.1 模块

- **Artifact：** `langchain4j-open-ai-realtime`
- **位置：** 与 `langchain4j-open-ai` 并列的顶层 Maven 模块
- **依赖：** `langchain4j-core`；WebSocket 客户端与服务端库；JSON。不依赖 antaios。`openai-java` 可选（若复用类型再评估，非必须）

### 3.2 内部分层

| 包/层 | 职责 |
|-------|------|
| `gateway` | 入站 WS、一对一会话绑定、`session.update` 中 tools 改写、事件转发 |
| `session` | 出站 `OpenAiRealtimeSession`（connect / sendEvent / server events） |
| `tools` | `RealtimeToolLoop`：注册表、白名单过滤、FC 执行与回灌 |

```
客户端
  │ Inbound WS + Authorization: Bearer <openai-key-or-ek>
  ▼
RealtimeGatewayServer
  │ tools 改写 / 事件转发
  ▼
OpenAiRealtimeSession ──► OpenAI
  │
  └─ function_call → RealtimeToolLoop → function_call_output + response.create
```

### 3.3 与 antaios 的关系

- antaios 现网：RTP ↔ Processor ↔ 出站 Realtime；工具经 HTTP `/agentic/tools/schema|execute`（进程边界 + 业务二次处理）。
- 本模块：入站改为类 OpenAI 的 WS；工具直接使用 langchain 原生 `ToolSpecification`/`ToolExecutor`。
- 后续：Processor 可改用本模块 `OpenAiRealtimeSession`；Facade 二次处理结果应注册为 pair 后再交给本模块。

## 4. 配置与鉴权

### 4.1 初始配置

- **客户端主导**：连接后由客户端发送 `session.update`（model、instructions、audio、VAD、reasoning 等）。
- **密钥模式 B**：入站 `Authorization: Bearer` 携带 OpenAI API key 或 ephemeral `ek_...`，网关**原样用于出站**。适合联调/内网；公网浏览器场景后续可再加应用层鉴权（非 v1 必做）。

### 4.2 工具注册（应用构建时）

应用在构建 Gateway/Agent 时注册：

```text
Map<ToolSpecification, ToolExecutor>
```

或与 `ToolService.tools(...)` 等价的 `@Tool` / `objectsWithTools` 扫描结果。

**这是 tools 的唯一权威来源**（schema + 执行）。

### 4.3 客户端 `session.update.tools`

- **可省略**：省略时出站挂载**全部**已注册工具。
- **若提供**：仅作 **name 白名单**，从注册表过滤；schema 仍来自注册表中的 `ToolSpecification`。
- **未知 name**：该次 `session.update` **整次失败**，下行明确 `error`，出站不应用残缺 tools。

## 5. 事件流

### 5.1 连接

- 一入站连接 ↔ 一出站 Realtime 连接；任一侧断开则关闭另一侧。
- 出站连上后，将 OpenAI 的 `session.created` 等事件转给客户端。

### 5.2 透传（为主）

**上行（客户端 → 出站）：**  
`input_audio_buffer.*`、`response.create` / `response.cancel`、`conversation.item.create`（非 function_call_output）、`conversation.item.truncate` 等。

**下行（出站 → 客户端）：**  
`response.output_audio.delta`（兼容旧名）、transcript/text、`session.updated`、`error` 等。

网关不做 RTP/重采样；音频格式由客户端在 `session.audio` 中约定。

### 5.3 工具循环（服务端）

触发：出站 `response.done`（或等价）中含 `function_call`。

1. 映射为 `ToolExecutionRequest`（name、arguments；`call_id` 用于回灌）。
2. 按 name 查注册表 `ToolExecutor`；并行执行多个 FC。
3. 逐个发送 `conversation.item.create`（`type=function_call_output`）。
4. 全部完成后发送**一次** `response.create`。
5. 后续音频/文本事件照常转客户端。

规则：

- 客户端**不应**发送 `function_call_output`；若发送则忽略（可选下行 `error`）。
- 不实现「取消正在执行的 tool」。
- 执行异常：捕获后以错误文案回灌该 `call_id`，不撕掉整会话；部分失败仍汇齐后一次 `response.create`。
- 模型幻觉未知 tool name：回灌错误文案型 output，再继续。

### 5.4 与纯 OpenAI 客户端的差异（必须文档化）

1. tools schema 以应用注册表为准，不是客户端 JSON 权威。
2. function call loop 在服务端完成。
3. 入站 Bearer 用于出站 OpenAI（模式 B）。

## 6. 打断与生命周期

### 6.1 Barge-in

- **停播**：客户端在收到 `input_audio_buffer.speech_started` 时清空本地播放。
- **`conversation.item.truncate`**：v1 由**客户端**发送（客户端知道已播放时长）。网关不自动 truncate（与 antaios 电话侧服务端 truncate 不同，需文档说明）。

### 6.2 Greeting

- 不强制；由客户端自行 `response.create`。antaios 自动 greeting 留在应用层。

### 6.3 并发

- 工具执行使用独立线程池，避免阻塞音频转发。
- 同一会话出站写入串行化（单写队列），防止帧交错。

### 6.4 可观测（v1 最小）

- 日志：connect、session.update（toolsCount）、每个 FC 的 name/call_id/耗时、出站 error。
- 不强制 OpenTelemetry。

## 7. 测试与验收

### 7.1 Done 标准

1. Bearer 出站可完成 `session.update` ↔ `session.updated`。
2. 构建时注册 tools；客户端省略 tools 时出站为全量注册表。
3. 白名单与未知 name 行为符合第 4.3 节。
4. FC loop：并行执行、回灌、一次 `response.create`。
5. 音频 append / output delta 可经网关往返（PCM fixture 即可）。
6. 任一侧断开，对端关闭，无悬挂会话。
7. 非目标未实现。

### 7.2 测试分层

| 层 | 内容 | 真 OpenAI |
|----|------|-----------|
| 单元 | tools 改写、FC 映射、并行回灌、忽略客户端 function_call_output | 否 |
| 契约 | GA 事件 JSON fixture + 假 WS | 否 |
| IT | 真 key 短会话（update / 工具 / 可选音频） | 是（可 EnabledIf） |
| 手工 | 浏览器采音 + 一个 @Tool | 是 |

## 8. 实现顺序建议（供 writing-plans）

1. 模块骨架与出站 `OpenAiRealtimeSession`（参考 antaios `AntaiosOpenaiVoiceClient` 事件语义，无 RTP）。
2. `RealtimeToolLoop` + 注册表 API。
3. 入站 Gateway + tools 改写与透传。
4. 单元/契约测试 + README 差异说明。
5. 可选 IT / 浏览器手工验收。

## 9. 决策记录

| 决策 | 选择 |
|------|------|
| 仓库 | langchain4j-famdetxire |
| 传输 | 服务端出站 WebSocket |
| 模块形态 | 单模块网关一体（内部分层可日后拆分） |
| langchain 兼容 | ToolSpecification + ToolExecutor；非 AiServices |
| 对外 API | 入站 WS，事件对齐 OpenAI Realtime |
| 初始配置 | 客户端传入 |
| tools 权威 | 应用构建时注册的 pair |
| 客户端 tools | 可省略=全量；提供则 name 白名单 |
| 密钥 | 客户端 Bearer 出站（模式 B） |
| truncate | 客户端负责（v1） |
| antaios Facade | 不进本模块；属应用二次处理 |
