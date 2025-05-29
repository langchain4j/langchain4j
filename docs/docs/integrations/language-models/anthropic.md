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
    <version>1.0.1-beta6</version>
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
    .cacheSystemMessages(...)
    .cacheTools(...)
    .thinkingType(...)
    .thinkingBudgetTokens(...)
    .timeout(...)
    .maxRetries(...)
    .logRequests(...)
    .logResponses(...)
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

## Caching

`AnthropicChatModel` and `AnthropicStreamingChatModel` support caching of system messages and tools.
Caching is disabled by default.
It can be enabled by setting the `cacheSystemMessages` and `cacheTools` parameters, respectively.

When enabled,`cache_control` blocks will be added to all system messages and tools respectively.

To use caching, please set `beta("prompt-caching-2024-07-31")`.

`AnthropicChatModel` and `AnthropicStreamingChatModel` return `AnthropicTokenUsage` in response which
contains `cacheCreationInputTokens` and `cacheReadInputTokens`.

More info on caching can be found [here](https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching).

## Thinking

`AnthropicChatModel` and `AnthropicStreamingChatModel` have a **_limited_** support
for [thinking](https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking) feature.
It can be enabled by setting the `thinkingType` and `thinkingBudgetTokens` parameters:
```java
ChatModel model = AnthropicChatModel.builder()
        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
        .modelName(CLAUDE_3_7_SONNET_20250219)
        .thinkingType("enabled")
        .thinkingBudgetTokens(1024)
        .maxTokens(1024 + 100)
        .logRequests(true)
        .logResponses(true)
        .build();
```

What is currently not supported:
- It not possible to get the thinking content from the LC4j API. It is only visible in logs.
- Thinking content is not [preserved](https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking#preserving-thinking-blocks) in the multi-turn conversations (with [memory](/tutorials/chat-memory))
- etc

More info on thinking can be found [here](https://docs.anthropic.com/en/docs/build-with-claude/extended-thinking).

## Quarkus

See more details [here](https://docs.quarkiverse.io/quarkus-langchain4j/dev/anthropic.html).

## Spring Boot

Import Spring Boot starter for Anthropic:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-anthropic-spring-boot-starter</artifactId>
    <version>1.0.1-beta6</version>
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
