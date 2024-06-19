---
sidebar_position: 12
---

# OpenAI

:::note
If you are using Quarkus, please refer to the
[Quarkus LangChain4j documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/openai.html).
:::

## OpenAI Documentation

- [OpenAI API Documentation](https://platform.openai.com/docs/introduction)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)

## Maven Dependency

### Plain Java
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.31.0</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>0.31.0</version>
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

## Creating OpenAiChatModel

### Plain Java
```java
ChatLanguageModel model = OpenAiChatModel.withApiKey(System.getenv("OPENAI_API_KEY"));
```
This will create an `OpenAiChatModel` with default model parameters (e.g. `gpt-3.5-turbo` model name, `0.7` temperature, etc.).
Default model parameters can be customized, see the section below for more information.

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
```
This configuration will create an `OpenAiChatModel` bean (with default model parameters),
which can be either used by an [AI Service](https://docs.langchain4j.dev/tutorials/spring-boot-integration/#langchain4j-spring-boot-starter)
or autowired directly, for example:

```java
@RestController
class ChatLanguageModelController {

    ChatLanguageModel chatLanguageModel;

    ChatLanguageModelController(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    @GetMapping("/model")
    public String model(@RequestParam(value = "message", defaultValue = "Hello") String message) {
        return chatLanguageModel.generate(message);
    }
}
```

## Customizing OpenAiChatModel

### Plain Java
```java
ChatLanguageModel model = OpenAiChatModel.builder()
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
    .tokenizer(...)
    .customHeaders(...)
    .build();
```

### Spring Boot
```properties
langchain4j.open-ai.chat-model.base-url=...
langchain4j.open-ai.chat-model.api-key=...
langchain4j.open-ai.chat-model.organization-id=...
langchain4j.open-ai.chat-model.model-name=...
langchain4j.open-ai.chat-model.temperature=...
langchain4j.open-ai.chat-model.top-p=
langchain4j.open-ai.chat-model.stop=...
langchain4j.open-ai.chat-model.max-tokens=...
langchain4j.open-ai.chat-model.presence-penalty=...
langchain4j.open-ai.chat-model.frequency-penalty=...
langchain4j.open-ai.chat-model.logit-bias=...
langchain4j.open-ai.chat-model.response-format=...
langchain4j.open-ai.chat-model.seed=...
langchain4j.open-ai.chat-model.user=...
langchain4j.open-ai.chat-model.timeout=...
langchain4j.open-ai.chat-model.max-retries=...
langchain4j.open-ai.chat-model.proxy.type=...
langchain4j.open-ai.chat-model.proxy.host=...
langchain4j.open-ai.chat-model.proxy.port=...
langchain4j.open-ai.chat-model.log-requests=...
langchain4j.open-ai.chat-model.log-responses=...
```

See the description of some of the parameters above [here](https://platform.openai.com/docs/api-reference/chat/create).

## Creating OpenAiStreamingChatModel

### Plain Java
```java
OpenAiStreamingChatModel model = OpenAiStreamingChatModel.withApiKey(System.getenv("OPENAI_API_KEY"));
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.open-ai.streaming-chat-model.api-key=${OPENAI_API_KEY}
```

### Customizing OpenAiStreamingChatModel

Similar to the `OpenAiChatModel`, see above.

## Creating OpenAiEmbeddingModel

### Plain Java
```java
EmbeddingModel model = OpenAiEmbeddingModel.withApiKey(System.getenv("OPENAI_API_KEY"));
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.open-ai.embedding-model.api-key=${OPENAI_API_KEY}
```

## Creating OpenAiImageModel

### Plain Java
```java
ImageModel model = OpenAiImageModel.withApiKey(System.getenv("OPENAI_API_KEY"));
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.open-ai.image-model.api-key=${OPENAI_API_KEY}
```

## Creating OpenAiModerationModel

### Plain Java
```java
ModerationModel model = OpenAiModerationModel.withApiKey(System.getenv("OPENAI_API_KEY"));
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.open-ai.moderation-model.api-key=${OPENAI_API_KEY}
```

## Creating OpenAiTokenizer

### Plain Java
```java
Tokenizer tokenizer = new OpenAiTokenizer();
// or
Tokenizer tokenizer = new OpenAiTokenizer("gpt-3.5-turbo");
```

### Spring Boot
The `OpenAiTokenizer` bean is created automatically.
