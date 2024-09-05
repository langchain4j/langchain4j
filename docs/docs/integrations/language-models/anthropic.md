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
    <version>0.34.0</version>
</dependency>
```

## AnthropicChatModel

```java
AnthropicChatModel model = AnthropicChatModel.withApiKey(System.getenv("ANTHROPIC_API_KEY"));
String answer = model.generate("Say 'Hello World'");
System.out.println(answer);
```

### Customizing
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
    .timeout(...)
    .maxRetries(...)
    .logRequests(...)
    .logResponses(...)
    .build();
```
See the description of some of the parameters above [here](https://docs.anthropic.com/claude/reference/messages_post).

## AnthropicStreamingChatModel
```java
AnthropicStreamingChatModel model = AnthropicStreamingChatModel.withApiKey(System.getenv("ANTHROPIC_API_KEY"));

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

### Customizing

Identical to the `AnthropicChatModel`, see above.

## Tools

Anthropic supports [tools](/tutorials/tools), but only in a non-streaming mode.

Anthropic documentation on tools can be found [here](https://docs.anthropic.com/claude/docs/tool-use).

## Quarkus

See more details [here](https://docs.quarkiverse.io/quarkus-langchain4j/dev/anthropic.html).

## Spring Boot

Import Spring Boot starter for Anthropic:
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-anthropic-spring-boot-starter</artifactId>
    <version>0.34.0</version>
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
