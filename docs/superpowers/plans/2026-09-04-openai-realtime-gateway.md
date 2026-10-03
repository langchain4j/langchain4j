# OpenAI Realtime Gateway 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 `langchain4j-famdetxire` 新增模块 `langchain4j-open-ai-realtime`：出站 Realtime WebSocket 会话 + 入站类 OpenAI 事件网关 + 基于 `ToolSpecification`/`ToolExecutor` 的服务端 tools loop。

**架构：** 单模块内分 `tools`（注册表/改写/loop）、`session`（出站 WS）、`gateway`（入站 WS 与一对一桥接）。应用构建时注册 tools pair；客户端 Bearer 用于出站；客户端可省略 `tools`（全量启用）。规格见 `docs/superpowers/specs/2026-09-04-openai-realtime-gateway-design.md`。

**技术栈：** Java（与仓库 parent 一致）、Maven、Jackson 2、JUnit 5、OkHttp WebSocket（出站，parent 已管理）、Java-WebSocket（入站服务端）、`langchain4j-core`（ToolSpecification / ToolExecutor / ToolExecutionRequest）。不依赖 antaios / Spring。

**规格：** `docs/superpowers/specs/2026-09-04-openai-realtime-gateway-design.md`

---

## 文件结构（将创建 / 修改）

| 文件 | 职责 |
|------|------|
| `pom.xml`（仓库根） | 增加 `<module>langchain4j-open-ai-realtime</module>` |
| `langchain4j-bom/pom.xml` | 增加 BOM 条目 |
| `langchain4j-open-ai-realtime/pom.xml` | 新模块 POM |
| `.../tools/RealtimeToolRegistry.java` | 持有 `Map<ToolSpecification, ToolExecutor>`，按 name 查找 |
| `.../tools/SessionUpdateToolsRewriter.java` | 改写客户端 `session.update` JSON 中的 tools |
| `.../tools/OpenAiRealtimeToolJson.java` | `ToolSpecification` → Realtime function tool JSON |
| `.../tools/RealtimeToolLoop.java` | FC 并行执行并回灌 |
| `.../tools/RealtimeOutboundWriter.java` | 出站写抽象（便于测 loop） |
| `.../session/RealtimeTransport.java` | 出站传输接口 |
| `.../session/FakeRealtimeTransport.java`（test） | 测试用传输 |
| `.../session/OkHttpRealtimeTransport.java` | OkHttp 出站实现 |
| `.../session/OpenAiRealtimeSession.java` | 会话：connect/send/listener/串行写出 |
| `.../session/OpenAiRealtimeSessionListener.java` | 出站事件回调 |
| `.../gateway/RealtimeGatewayConfig.java` | bind 地址、线程池等 |
| `.../gateway/RealtimeGatewaySession.java` | 一对一桥接 + tools 改写 + 接 ToolLoop |
| `.../gateway/RealtimeGatewayServer.java` | 入站 WS 服务 |
| `.../OpenAiRealtimeGateway.java` | 对外 Builder 入口（注册 tools、start） |
| `langchain4j-open-ai-realtime/README.md` | 用法与和纯 OpenAI 的差异 |
| `src/test/java/...` | 单元 / 契约测试 |
| `src/test/resources/realtime/fixtures/*.json` | GA 事件 fixture |

包根：`dev.langchain4j.model.openai.realtime`

出站默认 URL：`wss://api.openai.com/v1/realtime?model=` + session model（或 Builder 覆盖）。

---

### 任务 1：Maven 模块骨架

**文件：**
- 创建：`langchain4j-open-ai-realtime/pom.xml`
- 修改：`pom.xml`（根 modules）
- 修改：`langchain4j-bom/pom.xml`（在 `langchain4j-open-ai-official` 条目附近增加）

- [ ] **步骤 1：创建模块 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-parent</artifactId>
        <version>1.20.0-beta30-SNAPSHOT</version>
        <relativePath>../langchain4j-parent/pom.xml</relativePath>
    </parent>
    <artifactId>langchain4j-open-ai-realtime</artifactId>
    <version>1.20.0-beta30-SNAPSHOT</version>
    <name>LangChain4j :: Integration :: OpenAI Realtime</name>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-core</artifactId>
            <version>1.20.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
        </dependency>
        <dependency>
            <groupId>org.java-websocket</groupId>
            <artifactId>Java-WebSocket</artifactId>
            <version>1.5.7</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-core</artifactId>
            <version>1.20.0-SNAPSHOT</version>
            <classifier>tests</classifier>
            <type>test-jar</type>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

（若 parent 未管理 `Java-WebSocket`，版本写死在本模块即可；OkHttp 走 BOM。）

- [ ] **步骤 2：注册根 module 与 BOM**

根 `pom.xml` 在 `langchain4j-open-ai-official` 后增加 module。  
BOM 增加：

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-realtime</artifactId>
    <version>${langchain4j.beta.version}</version>
</dependency>
```

- [ ] **步骤 3：验证模块可编译**

```bash
mvn -pl langchain4j-open-ai-realtime -am -DskipTests compile
```

预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add pom.xml langchain4j-bom/pom.xml langchain4j-open-ai-realtime/pom.xml
git commit -m "build: add langchain4j-open-ai-realtime module skeleton"
```

---

### 任务 2：RealtimeToolRegistry + OpenAiRealtimeToolJson

**文件：**
- 创建：`langchain4j-open-ai-realtime/src/main/java/dev/langchain4j/model/openai/realtime/tools/RealtimeToolRegistry.java`
- 创建：`langchain4j-open-ai-realtime/src/main/java/dev/langchain4j/model/openai/realtime/tools/OpenAiRealtimeToolJson.java`
- 测试：`.../tools/RealtimeToolRegistryTest.java`
- 测试：`.../tools/OpenAiRealtimeToolJsonTest.java`

- [ ] **步骤 1：编写失败的 Registry 测试**

```java
@Test
void lookupByName_returnsExecutor() {
    ToolSpecification spec = ToolSpecification.builder()
            .name("get_weather")
            .description("weather")
            .build();
    ToolExecutor executor = (req, mem) -> "sunny";
    RealtimeToolRegistry registry = RealtimeToolRegistry.from(Map.of(spec, executor));
    assertThat(registry.findExecutor("get_weather")).isSameAs(executor);
    assertThat(registry.findSpecification("get_weather")).isEqualTo(spec);
    assertThat(registry.allSpecifications()).containsExactly(spec);
}

@Test
void unknownName_returnsEmpty() {
    RealtimeToolRegistry registry = RealtimeToolRegistry.from(Map.of());
    assertThat(registry.findExecutor("nope")).isNull();
}
```

- [ ] **步骤 2：运行测试确认失败**

```bash
mvn -pl langchain4j-open-ai-realtime -Dtest=RealtimeToolRegistryTest test
```

预期：编译失败或测试失败（类不存在）

- [ ] **步骤 3：实现 RealtimeToolRegistry**

```java
public final class RealtimeToolRegistry {
    private final Map<String, ToolSpecification> specsByName;
    private final Map<String, ToolExecutor> executorsByName;

    public static RealtimeToolRegistry from(Map<ToolSpecification, ToolExecutor> tools) {
        // copy into LinkedHashMap by toolSpecification.name(); reject duplicate names
    }

    public ToolExecutor findExecutor(String name) { ... }
    public ToolSpecification findSpecification(String name) { ... }
    public List<ToolSpecification> allSpecifications() { ... }
    public Set<String> names() { ... }
}
```

- [ ] **步骤 4：编写 OpenAiRealtimeToolJson 测试**

Realtime function 形态（与 Chat Completions 的 `type/function/function{}` 嵌套不同）：

```json
{ "type": "function", "name": "get_weather", "description": "...", "parameters": { "type": "object", ... } }
```

```java
@Test
void toFunctionTool_mapsNameDescriptionParameters() {
    ToolSpecification spec = ToolSpecification.builder()
            .name("get_weather")
            .description("Get weather")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("city")
                    .required("city")
                    .build())
            .build();
    ObjectNode node = OpenAiRealtimeToolJson.toFunctionTool(spec);
    assertThat(node.get("type").asText()).isEqualTo("function");
    assertThat(node.get("name").asText()).isEqualTo("get_weather");
    assertThat(node.get("parameters").get("type").asText()).isEqualTo("object");
}
```

实现时可参考 `langchain4j-open-ai` 的 `OpenAiUtils.toOpenAiParameters` 思路，或依赖 jackson 把 `JsonObjectSchema` 编成 map（与 `ExternalToolExecuteService.toOpenAiFunctionTool` / core JSON schema 工具对齐）。**不要**引入 antaios 依赖。

- [ ] **步骤 5：实现并通过测试后 Commit**

```bash
mvn -pl langchain4j-open-ai-realtime -Dtest=RealtimeToolRegistryTest,OpenAiRealtimeToolJsonTest test
git add langchain4j-open-ai-realtime
git commit -m "feat(realtime): add tool registry and Realtime function JSON mapping"
```

---

### 任务 3：SessionUpdateToolsRewriter

**文件：**
- 创建：`.../tools/SessionUpdateToolsRewriter.java`
- 测试：`.../tools/SessionUpdateToolsRewriterTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
@Test
void omitTools_usesFullRegistry() throws Exception {
    // registry has get_weather + ping
    String inbound = """
        {"type":"session.update","session":{"type":"realtime","instructions":"hi"}}
        """;
    String outbound = rewriter.rewrite(inbound);
    JsonNode tools = objectMapper.readTree(outbound).path("session").path("tools");
    assertThat(tools).hasSize(2);
}

@Test
void whitelist_filtersByName() throws Exception {
    String inbound = """
        {"type":"session.update","session":{"type":"realtime","tools":[{"type":"function","name":"ping"}]}}
        """;
    String outbound = rewriter.rewrite(inbound);
    JsonNode tools = objectMapper.readTree(outbound).path("session").path("tools");
    assertThat(tools).hasSize(1);
    assertThat(tools.get(0).get("name").asText()).isEqualTo("ping");
}

@Test
void unknownName_throws() {
    String inbound = """
        {"type":"session.update","session":{"tools":[{"type":"function","name":"unknown_tool"}]}}
        """;
    assertThatThrownBy(() -> rewriter.rewrite(inbound))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown_tool");
}
```

- [ ] **步骤 2：实现 rewriter**

逻辑：

1. 解析 JSON；若 `type` 不是 `session.update`，原样返回。
2. 读 `session.tools`：缺失/null/空数组 → 使用 `registry.allSpecifications()`。
3. 若存在：提取每个 tool 的 `name`（支持扁平 Realtime 或嵌套 `function.name`）；未知 name → `IllegalArgumentException`。
4. 用 `OpenAiRealtimeToolJson` 重写 `session.tools` 数组；保留 session 其它字段。

- [ ] **步骤 3：测试通过后 Commit**

```bash
mvn -pl langchain4j-open-ai-realtime -Dtest=SessionUpdateToolsRewriterTest test
git add langchain4j-open-ai-realtime
git commit -m "feat(realtime): rewrite session.update tools from registry"
```

---

### 任务 4：RealtimeToolLoop

**文件：**
- 创建：`.../tools/RealtimeOutboundWriter.java`
- 创建：`.../tools/RealtimeToolLoop.java`
- 测试：`.../tools/RealtimeToolLoopTest.java`
- 创建：`src/test/resources/realtime/fixtures/response-done-two-function-calls.json`

- [ ] **步骤 1：准备 fixture**

`response-done-two-function-calls.json` 需含 `type=response.done`，`response.output` 中两个 `type=function_call` 项（带 `name`、`arguments`、`call_id`）。字段名对齐 GA Realtime（参考 antaios `onResponseDone` 解析）。

- [ ] **步骤 2：编写失败的 loop 测试**

```java
@Test
void parallelFunctionCalls_emitOutputsThenSingleResponseCreate() {
    List<String> sent = Collections.synchronizedList(new ArrayList<>());
    RealtimeOutboundWriter writer = sent::add;
    RealtimeToolRegistry registry = RealtimeToolRegistry.from(Map.of(
            weatherSpec, (r, m) -> "sunny",
            pingSpec, (r, m) -> "pong"));
    RealtimeToolLoop loop = new RealtimeToolLoop(registry, writer, Runnable::run); // sync executor for test

    loop.onServerEvent(loadFixture("response-done-two-function-calls.json"));

    assertThat(sent.stream().filter(s -> s.contains("function_call_output"))).hasSize(2);
    assertThat(sent.stream().filter(s -> s.contains("\"type\":\"response.create\""))).hasSize(1);
    int lastOutputIdx = IntStream.range(0, sent.size())
            .filter(i -> sent.get(i).contains("function_call_output"))
            .max().orElseThrow();
    int responseCreateIdx = IntStream.range(0, sent.size())
            .filter(i -> sent.get(i).contains("\"type\":\"response.create\""))
            .findFirst().orElseThrow();
    assertThat(responseCreateIdx).isGreaterThan(lastOutputIdx);
}

@Test
void executorException_stillEmitsOutputAndResponseCreate() {
    ToolExecutor boom = (r, m) -> { throw new RuntimeException("fail"); };
    // ... assert function_call_output contains error text, still one response.create
}

@Test
void hallucinatedToolName_emitsErrorOutput() {
    // registry empty for that name
}
```

- [ ] **步骤 3：实现 RealtimeToolLoop**

```java
public final class RealtimeToolLoop {
    public RealtimeToolLoop(RealtimeToolRegistry registry,
                            RealtimeOutboundWriter writer,
                            Executor toolExecutor) { ... }

    /** @return true if event was a handled response.done with function calls */
    public boolean onServerEvent(String json) { ... }
}
```

行为：

- 仅处理含 `function_call` 的 `response.done`（无 FC 则 return false，由网关透传）。
- 构建 `ToolExecutionRequest`（id 可用 call_id）。
- 并行 `CompletableFuture`（传入的 Executor）。
- 每个结果：`conversation.item.create` + `item.type=function_call_output` + `call_id` + `output` 字符串。
- 最后一次 `{"type":"response.create"}`。
- 异常 / 未知 name：output 为错误说明字符串。

- [ ] **步骤 4：测试通过后 Commit**

```bash
mvn -pl langchain4j-open-ai-realtime -Dtest=RealtimeToolLoopTest test
git add langchain4j-open-ai-realtime
git commit -m "feat(realtime): server-side function call tool loop"
```

---

### 任务 5：出站传输抽象 + OpenAiRealtimeSession

**文件：**
- 创建：`.../session/RealtimeTransport.java`
- 创建：`.../session/RealtimeTransportListener.java`
- 创建：`.../session/OpenAiRealtimeSessionListener.java`
- 创建：`.../session/OpenAiRealtimeSession.java`
- 创建：`.../session/OkHttpRealtimeTransport.java`
- 测试：`.../session/OpenAiRealtimeSessionTest.java`
- 测试：`.../session/FakeRealtimeTransport.java`（可放 test 源码集）

- [ ] **步骤 1：定义传输接口**

```java
public interface RealtimeTransport extends AutoCloseable {
    void connect(String url, Map<String, String> headers, RealtimeTransportListener listener);
    void sendText(String message);
    void close();
}

public interface RealtimeTransportListener {
    void onOpen();
    void onTextMessage(String text);
    void onClosed(int code, String reason);
    void onFailure(Throwable t);
}
```

- [ ] **步骤 2：Session 单元测试（Fake transport）**

```java
@Test
void sendEvent_isSerializedOnSingleWriter() {
    FakeRealtimeTransport transport = new FakeRealtimeTransport();
    OpenAiRealtimeSession session = OpenAiRealtimeSession.builder()
            .transport(transport)
            .apiKey("sk-test")
            .model("gpt-realtime-2.1")
            .listener(events::add)
            .build();
    session.connect();
    session.sendEvent("{\"type\":\"session.update\",\"session\":{\"type\":\"realtime\"}}");
    assertThat(transport.sent).hasSize(1);
}

@Test
void inboundServerMessage_notifiesListener() {
    // transport.simulateText("{\"type\":\"session.created\"}")
    // assert listener received same JSON
}
```

- [ ] **步骤 3：实现 OpenAiRealtimeSession**

- `connect()`：URL `wss://api.openai.com/v1/realtime?model={model}`（可 override）；header `Authorization: Bearer {apiKey}`；可选 `OpenAI-Safety-Identifier`。
- 出站写：单线程/`BlockingQueue` 串行 `sendText`。
- `sendEvent(String json)` 入队。
- `close()` 关闭 transport。
- Listener：原始 JSON 字符串回调（网关再解析）。

- [ ] **步骤 4：实现 OkHttpRealtimeTransport**

使用 `okhttp3.WebSocket` / `WebSocketListener`。单元测试可不连真网；可选 `@EnabledIfEnvironmentVariable(named="OPENAI_API_KEY")` 的 IT 放到任务 8。

- [ ] **步骤 5：测试通过后 Commit**

```bash
mvn -pl langchain4j-open-ai-realtime -Dtest=OpenAiRealtimeSessionTest test
git add langchain4j-open-ai-realtime
git commit -m "feat(realtime): OpenAiRealtimeSession with pluggable transport"
```

---

### 任务 6：RealtimeGatewaySession（桥接逻辑，无真实端口）

**文件：**
- 创建：`.../gateway/RealtimeGatewaySession.java`
- 测试：`.../gateway/RealtimeGatewaySessionTest.java`

桥接职责（规格核心）：

1. 入站文本帧 → 若 `session.update` 则 rewriter；若客户端 `function_call_output` 则忽略（可回写 error JSON）；其它原样 `session.sendEvent`。
2. 出站文本帧 → 先 `toolLoop.onServerEvent`；若已处理 FC 则**不再**把该 `response.done` 原样转给客户端也可选仍转发（推荐：**仍转发** `response.done`，另由 loop 写回灌事件，与「事件尽量一致」兼容）。若 loop 返回 false，原样转客户端。
3. 持有 `RealtimeToolRegistry`、`SessionUpdateToolsRewriter`、`RealtimeToolLoop`、`OpenAiRealtimeSession`。
4. 入站 close → `session.close()`；出站 failure → 入站发送 error 并 close。

- [ ] **步骤 1：编写失败的桥接测试（Fake 入站 + Fake 出站）**

用简单 `TestClientLink` / `TestServerLink`（队列模拟双向）或直接测 `RealtimeGatewaySession` 的 package 方法：

```java
@Test
void sessionUpdate_rewritesToolsBeforeOutbound() { ... }

@Test
void clientFunctionCallOutput_isIgnored() { ... }

@Test
void responseDoneWithFunctionCall_runsLoopAndForwardsEvents() { ... }
```

- [ ] **步骤 2：实现并通过测试后 Commit**

```bash
mvn -pl langchain4j-open-ai-realtime -Dtest=RealtimeGatewaySessionTest test
git add langchain4j-open-ai-realtime
git commit -m "feat(realtime): gateway session bridge with tool rewrite and loop"
```

---

### 任务 7：RealtimeGatewayServer + OpenAiRealtimeGateway Builder

**文件：**
- 创建：`.../gateway/RealtimeGatewayConfig.java`
- 创建：`.../gateway/RealtimeGatewayServer.java`
- 创建：`.../OpenAiRealtimeGateway.java`
- 测试：`.../gateway/RealtimeGatewayServerTest.java`

- [ ] **步骤 1：Builder API 设计（实现时锁定签名）**

```java
OpenAiRealtimeGateway gateway = OpenAiRealtimeGateway.builder()
        .host("127.0.0.1")
        .port(0) // ephemeral
        .tools(Map.of(spec, executor)) // or .tools(objectWithTools)
        .toolExecutor(Executors.newCachedThreadPool())
        .build();
gateway.start();
URI wsUri = gateway.wsUri(); // ws://127.0.0.1:port/
gateway.stop();
```

入站连接：从 HTTP headers 读 `Authorization: Bearer ...`；缺失则关闭。用该 key 创建 `OpenAiRealtimeSession`（生产 transport = OkHttp）。每个入站连接一个 `RealtimeGatewaySession`。

- [ ] **步骤 2：本地环回测试（出站用 FakeRealtimeTransport 工厂）**

为可测性，Builder 允许 `outboundTransportFactory(apiKey -> RealtimeTransport)`；测试注入 Fake，验证：

1. 客户端连上并带 Bearer。
2. 发无 tools 的 `session.update` → Fake 出站收到的 tools 为全量注册表。
3. 模拟出站 `response.done`+FC → Fake 出站收到 `function_call_output` 与 `response.create`；客户端收到下行事件。

使用 Java-WebSocket 客户端连本地 server。

- [ ] **步骤 3：实现 Server（Java-WebSocket `WebSocketServer`）**

注意：accept 线程与 tool 线程隔离；每个连接独立 session。

- [ ] **步骤 4：测试通过后 Commit**

```bash
mvn -pl langchain4j-open-ai-realtime -Dtest=RealtimeGatewayServerTest test
git add langchain4j-open-ai-realtime
git commit -m "feat(realtime): inbound WebSocket gateway server and public builder"
```

---

### 任务 8：README + 可选 IT

**文件：**
- 创建：`langchain4j-open-ai-realtime/README.md`
- 创建：`.../OpenAiRealtimeSessionIT.java`（可选）

- [ ] **步骤 1：README 必写差异**

必须写明规格 5.4：

1. tools schema 以应用注册表为准；客户端可省略 tools。  
2. function call loop 在服务端。  
3. 入站 Bearer 用于出站 OpenAI。  
4. barge-in：客户端停播 + 客户端 `truncate`。  
5. 最小代码示例：注册 `@Tool` / Map、start gateway、浏览器/ws 客户端连入。

- [ ] **步骤 2：可选 IT**

```java
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiRealtimeSessionIT {
    @Test
    void sessionUpdate_roundTrip() throws Exception {
        // real OkHttp transport; send session.update; await session.updated within timeout
    }
}
```

- [ ] **步骤 3：全模块测试**

```bash
mvn -pl langchain4j-open-ai-realtime -am test
```

预期：非 IT 全部 PASS

- [ ] **步骤 4：Commit**

```bash
git add langchain4j-open-ai-realtime
git commit -m "docs(realtime): README and optional live Realtime IT"
```

---

## 规格覆盖自检

| 规格章节 | 任务 |
|----------|------|
| 出站 Realtime WS | 任务 5 |
| 入站类 OpenAI WS | 任务 6–7 |
| ToolSpecification+ToolExecutor 权威 | 任务 2–3、7 |
| 客户端省略 tools = 全量 | 任务 3 |
| name 白名单 / 未知失败 | 任务 3 |
| 密钥模式 B | 任务 5、7 |
| FC 并行 + 一次 response.create | 任务 4 |
| 忽略客户端 function_call_output | 任务 6 |
| 串行出站写 / 工具线程池 | 任务 4–5、7 |
| 客户端 truncate（网关不自动） | README（任务 8）；无服务端 truncate 代码 |
| 非目标（AiServices 等） | 不实现 |
| 单元/契约/IT | 任务 2–8 |
| 根 POM / BOM | 任务 1 |

## 类型命名一致性

- `RealtimeToolRegistry` / `SessionUpdateToolsRewriter` / `RealtimeToolLoop` / `RealtimeOutboundWriter`
- `RealtimeTransport` / `OpenAiRealtimeSession` / `OpenAiRealtimeSessionListener`
- `RealtimeGatewaySession` / `RealtimeGatewayServer` / `OpenAiRealtimeGateway`

---

## 执行交接

计划已完成并保存到 `docs/superpowers/plans/2026-09-04-openai-realtime-gateway.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代  

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点  

选哪种方式？
