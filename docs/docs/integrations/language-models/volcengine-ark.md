---
sidebar_position: 2
---

# Ark

- [Volcengine Ark Documentation](https://www.volcengine.com/docs/82379)
- [Volcengine Ark Reference](https://www.volcengine.com/docs/82379/1263482)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ark</artifactId>
    <version>0.31.0</version>
</dependency>
```

## ArkChatModel

```java
ArkChatModel model = ArkChatModel.builder()
    .apiKey(System.getenv("ARK_API_KEY"))
    .model(System.getenv("ARK_ENDPOINT_ID"))
    .build();
String answer = model.generate("Say 'Hello World'");
System.out.println(answer);
```

### Customizing
```java
ArkChatModel model = ArkChatModel.builder()
    .apiKey(System.getenv("ARK_API_KEY"))
    .model(System.getenv("ARK_ENDPOINT_ID"))
    .topP(...)
    .frequencyPenalty(...)
    .presencePenalty(...)
    .temperature(...)
    .stops(...)
    .maxTokens(...)
    .user(...)
    .build();
```
See the description of some of the parameters above [here](https://www.volcengine.com/docs/82379/1263482).

## ArkStreamingChatModel
```java
ArkStreamingChatModel model = ArkStreamingChatModel.builder()
    .apiKey(System.getenv("ARK_API_KEY"))
    .model(System.getenv("ARK_ENDPOINT_ID"))
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

### Customizing

Identical to the `ArkChatModel`, see above.

## Tools

Model 'doubao-pro-32k-functioncall-240515' supports [tools](/tutorials/tools), but only in a non-streaming mode in current langchain4j-ark version.

## Quarkus

TODO

## Spring Boot

TODO