---
sidebar_position: 2
---

# Anthropic

- [Anthropic Documentation](https://docs.anthropic.com/claude/docs)
- [Anthropic API Reference](https://docs.anthropic.com/claude/reference)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-anthropic</artifactId>
    <version>1.9.1</version>
</dependency>
```

## AnthropicChatModel

```java
AnthropicChatModel model = AnthropicChatModel.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .modelName(CLAUDE_3_5_SONNET_20240620)
    .build();
String answer = model.chat("Say 'Hello World'");
System.out.println(answer);
```

### Customizing AnthropicChatModel
```java
AnthropicChatModel model = AnthropicChatModel.builder()
    .httpClientBuilder(...)
    .baseUrl(...)
    .apiKey(...)
    .version(...)
    .beta(...)
    .modelName(...)
    .temperature(...)
    .topP(...)
    .topK(...)
    .maxTokens(...)
    .stopSequences(...)
    .toolSpecifications(...)
    .toolChoice(...)
    .toolChoiceName(...)
    .disableParallelToolUse(...)
    .serverTools(...)
    .toolMetadataKeysToSend(...)
    .cacheSystemMessages(...)
    .cacheTools(...)
    .thinkingType(...)
    .thinkingBudgetTokens(...)
    .returnThinking(...)
    .sendThinking(...)
    .timeout(...)
    .maxRetries(...)
    .logRequests(...)
    .logResponses(...)
    .listeners(...)
    .defaultRequestParameters(...)
    .userId(...)
    .customParameters(...)
    .build();
```
See the description of some of the parameters above [here](https://docs.anthropic.com/claude/reference/messages_post).

## AnthropicStreamingChatModel
```java
AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .modelName(CLAUDE_3_5_SONNET_20240620)
    .build();

model.chat("Say 'Hello World'", new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        // this method is called when a new partial response is available. It can consist of one or more tokens.
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        // this method is called when the model has completed responding
    }

    @Override
    public void onError(Throwable error) {
        // this method is called when an error occurs
    }
});
```

### Customizing AnthropicStreamingChatModel

Identical to the `AnthropicChatModel`, see above.

## Tools

Anthropic supports [tools](/tutorials/tools) in both streaming and non-streaming mode.

Anthropic documentation on tools can be found [here](https://docs.anthropic.com/claude/docs/tool-use).


## Tool Choice

Anthropic's [tool choice](https://docs.anthropic.com/en/docs/agents-and-tools/tool-use/implement-tool-use#forcing-tool-use)
feature is available for both streaming and non-streaming interactions
by setting `toolChoice(ToolChoice)` or `toolChoiceName(String)`.

## Parallel Tool Use

By default, Anthropic Claude may use multiple tools to answer a user query,
but you can disable [parallel tool](https://docs.anthropic.com/en/docs/agents-and-tools/tool-use/implement-tool-use#parallel-tool-use) by setting `disableParallelToolUse(true)`.

## Server Tools

Anthropic's [server tools](https://platform.claude.com/docs/en/agents-and-tools/tool-use/overview#server-tools)
are supported via `serverTools` parameter, here is an example of using a [web search tool](https://platform.claude.com/docs/en/agents-and-tools/tool-use/web-search-tool):
```java
AnthropicServerTool webSearchTool = AnthropicServerTool.builder()
        .type("web_search_20250305")
        .name("web_search")
        .addAttribute("max_uses", 5)
        .addAttribute("allowed_domains", List.of("accuweather.com"))
        .build();

ChatModel model = AnthropicChatModel.builder()
        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
        .modelName("claude-sonnet-4-5")
        .serverTools(webSearchTool)
        .logRequests(true)
        .logResponses(true)
        .build();

String answer = model.chat("What is the weather in Munich?");
```

Tools specified via `serverTools` will be included in every request to the Anthropic API.

## Tool Search Tool

Anthropic's [tool search tool](https://platform.claude.com/docs/en/agents-and-tools/tool-use/tool-search-tool)
is supported via `serverTools`, tool `metadata` and `toolMetadataKeysToSend` parameters.

Here is an example when using high-level AI Service and `@Tool` APIs:

```java
AnthropicServerTool toolSearchTool = AnthropicServerTool.builder()
        .type("tool_search_tool_regex_20251119")
        .name("tool_search_tool_regex")
        .build();

class Tools {

    @Tool(metadata = "{\"defer_loading\": true}")
    String getWeather(String location) {
        return "sunny";
    }

    @Tool
    String getTime(String location) {
        return "12:34:56";
    }
}

ChatModel chatModel = AnthropicChatModel.builder()
        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
        .modelName(CLAUDE_SONNET_4_5_20250929)
        .beta("advanced-tool-use-2025-11-20")
        .serverTools(toolSearchTool)
        .toolMetadataKeysToSend("defer_loading") // need to specify it explicitly
        .logRequests(true)
        .logResponses(true)
        .build();

interface Assistant {

    @SystemMessage("Use tool search if needed")
    String chat(String userMessage);
}

Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .tools(new Tools())
        .build();

assistant.chat("What is the weather in Munich?");
```

Here is an example when using low-level `ChatModel` and `ToolSpecification` APIs:
```java
AnthropicServerTool toolSearchTool = AnthropicServerTool.builder()
        .type("tool_search_tool_regex_20251119")
        .name("tool_search_tool_regex")
        .build();

Map<String, Object> toolMetadata = Map.of("defer_loading", true);

ToolSpecification weatherTool = ToolSpecification.builder()
        .name("get_weather")
        .parameters(JsonObjectSchema.builder()
                .addStringProperty("location")
                .required("location")
                .build())
        .metadata(toolMetadata)
        .build();

ToolSpecification timeTool = ToolSpecification.builder()
        .name("get_time")
        .parameters(JsonObjectSchema.builder()
                .addStringProperty("location")
                .required("location")
                .build())
        .build();

ChatModel model = AnthropicChatModel.builder()
        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
        .modelName(CLAUDE_SONNET_4_5_20250929)
        .beta("advanced-tool-use-2025-11-20")
        .serverTools(toolSearchTool)
        .toolMetadataKeysToSend(toolMetadata.keySet()) // need to specify it explicitly
        .logRequests(true)
        .logResponses(true)
        .build();

ChatRequest chatRequest = ChatRequest.builder()
        .messages(UserMessage.from("What is the weather in Munich? Use tool search if needed."))
        .toolSpecifications(weatherTool, timeTool)
        .build();

ChatResponse chatResponse = model.chat(chatRequest);
```

### Programmatic Tool Calling

Anthropic's [programmatic tool calling](https://www.anthropic.com/engineering/advanced-tool-use)
is supported via `serverTools`, tool `metadata` and `toolMetadataKeysToSend` parameters.

Here is an example when using high-level AI Service and `@Tool` APIs:

```java
AnthropicServerTool codeExecutionTool = AnthropicServerTool.builder()
        .type("code_execution_20250825")
        .name("code_execution")
        .build();

class Tools {

    static final String TOOL_METADATA = "{\"allowed_callers\": [\"code_execution_20250825\"]}";
    static final String TOOL_DESCRIPTION = """
            Returns daily minimum and maximum temperatures recorded
            for a specified city for a specified number of previous days.
            Response format: [{"min":0.0,"max":10.0},{"min":0.0,"max":20.0},{"min":0.0,"max":30.0}]
            """;

    record TemperatureRange(double min, double max) {}

    @Tool(value = TOOL_DESCRIPTION, metadata = TOOL_METADATA)
    List<TemperatureRange> getDailyTemperatures(String city, int days) {
        if ("Munich".equals(city) && days == 5) {
            return List.of(
                    new TemperatureRange(0.0, 1.0),
                    new TemperatureRange(0.0, 2.0),
                    new TemperatureRange(0.0, 3.0),
                    new TemperatureRange(0.0, 4.0),
                    new TemperatureRange(0.0, 5.0)
            );
        }

        throw new IllegalArgumentException("Unknown city: " + city + " or days: " + days);
    }

    @Tool(value = "Calculates the average of the specified list of numbers", metadata = TOOL_METADATA)
    Double average(List<Double> numbers) {
        return numbers.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
    }
}

ChatModel chatModel = AnthropicChatModel.builder()
        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
        .modelName(CLAUDE_SONNET_4_5_20250929)
        .beta("advanced-tool-use-2025-11-20")
        .serverTools(codeExecutionTool)
        .toolMetadataKeysToSend("allowed_callers") // need to specify it explicitly
        .logRequests(true)
        .logResponses(true)
        .build();

interface Assistant {

    String chat(String userMessage);
}

Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .tools(new Tools())
        .build();

assistant.chat("What was the average max temperature in Munich in the last 5 days?");
```

Check [Tool Search Tool](/integrations/language-models/anthropic#tool-search-tool) section
to see an example of specifying tool `metadata` in the low-level `ToolSpecification` API.

### Tool Use Examples

Anthropic's [tool use examples](https://www.anthropic.com/engineering/advanced-tool-use)
are supported via tool `metadata` and `toolMetadataKeysToSend` parameters.

Here is an example when using high-level AI Service and `@Tool` APIs:

```java
enum Unit {
    CELSIUS, FAHRENHEIT
}

class Tools {

    // NOTE: if javac "-parameters" option is not enabled, you need to change "location" to "arg0"
    // and "unit" to "arg1" inside the TOOL_METADATA to make it work.
    public static final String TOOL_METADATA = """
            {
                "input_examples": [
                    {
                        "location": "San Francisco, CA",
                        "unit": "FAHRENHEIT"
                    },
                    {
                        "location": "Tokyo, Japan",
                        "unit": "CELSIUS"
                    },
                    {
                        "location": "New York, NY"
                    }
                ]
            }
            """;

    @Tool(metadata = TOOL_METADATA)
    String getWeather(String location, @P(value = "temperature unit", required = false) Unit unit) {
        return "sunny";
    }
}

ChatModel chatModel = AnthropicChatModel.builder()
        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
        .modelName(CLAUDE_SONNET_4_5_20250929)
        .beta("advanced-tool-use-2025-11-20")
        .toolMetadataKeysToSend("input_examples") // need to specify it explicitly
        .logRequests(true)
        .logResponses(true)
        .build();

interface Assistant {

    String chat(String userMessage);
}

Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .tools(new Tools())
        .build();

assistant.chat("What is the weather in Munich in Fahrenheit?");
```

Check [Tool Search Tool](/integrations/language-models/anthropic#tool-search-tool) section
to see an example of specifying tool `metadata` in the low-level `ToolSpecification` API.

## Caching

`AnthropicChatModel` and `AnthropicStreamingChatModel` support caching of system messages and tools.
Caching is disabled by default.
It can be enabled by setting the `cacheSystemMessages` and `cacheTools` parameters, respectively.

When enabled,`cache_control` blocks will be added to the last system message and tool, respectively.

To use caching, please set `beta("prompt-caching-2024-07-31")`.

`AnthropicChatModel` and `AnthropicStreamingChatModel` return `AnthropicTokenUsage` in response which
contains `cacheCreationInputTokens` and `cacheReadInputTokens`.

More info on caching can be found [here](https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching).

## Thinking

Both `AnthropicChatModel` and `AnthropicStreamingChatModel` support
[thinking](https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking) feature.

It is controlled by the following parameters:
- `thinkingType` and `thinkingBudgetTokens`: enable thinking,
  see more details [here](https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking).
- `returnThinking`: controls whether to return thinking (if available) inside `AiMessage.thinking()`
  and whether to invoke `StreamingChatResponseHandler.onPartialThinking()` and `TokenStream.onPartialThinking()`
  callbacks when using `BedrockStreamingChatModel`.
  Disabled by default. If enabled, tinking signatures will also be stored and returned inside the `AiMessage.attributes()`.
- `sendThinking`: controls whether to send thinking and signatures stored in `AiMessage` to the LLM in follow-up requests.
Enabled by default.

Here is an example of how to configure thinking:
```java
ChatModel model = AnthropicChatModel.builder()
        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
        .modelName(CLAUDE_3_7_SONNET_20250219)
        .thinkingType("enabled")
        .thinkingBudgetTokens(1024)
        .maxTokens(1024 + 100)
        .returnThinking(true)
        .sendThinking(true)
        .build();
```

## PDF Support

Anthropic Claude supports processing PDF documents. You can send PDFs either via URL or base64-encoded data.

### Sending PDF via URL
```java
UserMessage message = UserMessage.from(
    PdfFileContent.from(URI.create("https://example.com/document.pdf")),
    TextContent.from("What are the key findings in this document?")
);

ChatResponse response = model.chat(message);
```

### Sending PDF via Base64
```java
String base64Data = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("document.pdf")));

UserMessage message = UserMessage.from(
    PdfFileContent.from(base64Data, "application/pdf"),
    TextContent.from("Summarize this document.")
);

ChatResponse response = model.chat(message);
```

More info on PDF support can be found [here](https://docs.anthropic.com/en/docs/build-with-claude/pdf-support).

## Setting custom chat request parameters

When building `AnthropicChatModel` and `AnthropicStreamingChatModel`,
you can configure custom parameters for the chat request within the HTTP request's JSON body.
Here is an example of how to enable [context editing](https://docs.claude.com/en/docs/build-with-claude/context-editing):
```java
record Edit(String type) {}
record ContextManagement(List<Edit> edits) { }
Map<String, Object> customParameters = Map.of("context_management", new ContextManagement(List.of(new Edit("clear_tool_uses_20250919"))));

ChatModel model = AnthropicChatModel.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .modelName(CLAUDE_SONNET_4_5_20250929)
    .beta("context-management-2025-06-27")
    .customParameters(customParameters)
    .logRequests(true)
    .logResponses(true)
    .build();

String answer = model.chat("Hi");
```

This will produce an HTTP request with the following body:
```json
{
    "model" : "claude-sonnet-4-5-20250929",
    "messages" : [ {
        "role" : "user",
        "content" : [ {
            "type" : "text",
            "text" : "Hi"
        } ]
    } ],
    "context_management" : {
        "edits" : [ {
            "type" : "clear_tool_uses_20250919"
        } ]
    }
}
```

Alternatively, custom parameters can also be specified as a structure of nested maps:
```java
Map<String, Object> customParameters = Map.of(
        "context_management",
        Map.of("edits", List.of(Map.of("type", "clear_tool_uses_20250919")))
);
```

## AnthropicTokenCountEstimator

```java
TokenCountEstimator tokenCountEstimator = AnthropicTokenCountEstimator.builder()
        .modelName(CLAUDE_3_OPUS_20240229)
        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
        .logRequests(true)
        .logResponses(true)
        .build();

List<ChatMessage> messages = List.of(...);

int tokenCount = tokenCountEstimator.estimateTokenCountInMessages(messages);
```

## Quarkus

See more details [here](https://docs.quarkiverse.io/quarkus-langchain4j/dev/anthropic.html).

## Spring Boot

Import Spring Boot starter for Anthropic:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-anthropic-spring-boot-starter</artifactId>
    <version>1.9.1-beta17</version>
</dependency>
```

Configure `AnthropicChatModel` bean:
```
langchain4j.anthropic.chat-model.api-key = ${ANTHROPIC_API_KEY}
```

Configure `AnthropicStreamingChatModel` bean:
```
langchain4j.anthropic.streaming-chat-model.api-key = ${ANTHROPIC_API_KEY}
```


## Examples

- [AnthropicChatModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/anthropic-examples/src/main/java/AnthropicChatModelTest.java)
- [AnthropicStreamingChatModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/anthropic-examples/src/main/java/AnthropicStreamingChatModelTest.java)
- [AnthropicToolsTest](https://github.com/langchain4j/langchain4j-examples/blob/main/anthropic-examples/src/main/java/AnthropicToolsTest.java)
- [AnthropicPdfExample](https://github.com/langchain4j/langchain4j-examples/blob/main/anthropic-examples/src/main/java/AnthropicPdfExample.java)
