---
sidebar_position: 12
---

# OpenAI

## Documentation

- [OpenAI API Documentation](https://platform.openai.com/docs/introduction)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.29.1</version>
</dependency>
```

## API Key

To use OpenAI models, you will need an API key.
You can create one [here](https://platform.openai.com/api-keys).

:::note
If you don't have your own OpenAI API key, don't worry.
You can temporarily use `demo` key, which we provide for free for demonstration purposes:

```java
String apiKey = "demo";
```

Be aware that when using the `demo` key, all requests to the OpenAI API go through our proxy,
which injects the real key before forwarding your request to the OpenAI API.
We do not collect or use your data in any way.
The `demo` key has a quota and should only be used for demonstration purposes.
:::

## OpenAiChatModel

```java

OpenAiChatModel model = OpenAiChatModel.withApiKey(System.getenv("OPENAI_API_KEY"));
String answer = model.generate("Say 'Hello World'");
System.out.println(answer);
```

### Customizing

```java
OpenAiChatModel model = OpenAiChatModel.builder()
    .baseUrl(...)
    .apiKey(...)
    .organizationId(...)
    .modelName(...)
    .temperature(...)
    .topP(...)
    .stop(...)
    .maxTokens(...)
    .presencePenalty(...)
    .frequencyPenalty(...)
    .logitBias(...)
    .responseFormat(...)
    .seed(...)
    .user(...)
    .timeout(...)
    .maxRetries(...)
    .proxy(...)
    .logRequests(...)
    .logResponses(...)
    .build();
```
See the description of some of the parameters above [here](https://platform.openai.com/docs/api-reference/chat/create).

## OpenAiStreamingChatModel

```java
OpenAiStreamingChatModel model = OpenAiStreamingChatModel.withApiKey(System.getenv("OPENAI_API_KEY"));

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

Identical to the `OpenAiChatModel`, see above.

## OpenAiEmbeddingModel

## OpenAiImageModel

## OpenAiModerationModel

## OpenAiTokenizer

## Quarkus

## Spring Boot
