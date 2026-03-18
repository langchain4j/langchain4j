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
    <version>1.7.1</version>
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
    <version>1.7.1-beta14</version>
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
