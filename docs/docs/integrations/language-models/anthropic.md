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
    <version>0.36.0</version>
</dependency>
```

## AnthropicChatModel

```java
AnthropicChatModel model = AnthropicChatModel.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .modelName(CLAUDE_3_5_SONNET_20240620)
    .build();
String answer = model.generate("Say 'Hello World'");
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

model.generate("Say 'Hello World'", new StreamingResponseHandler<AiMessage>() {

    @Override
    public void onNext(String token) {
        // this method is called when a new token is available
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
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

## Quarkus

See more details [here](https://docs.quarkiverse.io/quarkus-langchain4j/dev/anthropic.html).

## Spring Boot

Import Spring Boot starter for Anthropic:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-anthropic-spring-boot-starter</artifactId>
    <version>0.36.0</version>
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
