---
sidebar_position: 3
---

# Azure OpenAI

:::note
If you are using Quarkus, please refer to the
[Quarkus LangChain4j documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/openai.html#_azure_openai).
:::

## Azure OpenAI Documentation

- [Azure OpenAI Documentation](https://learn.microsoft.com/en-us/azure/ai-services/openai/)

## Maven Dependency

### Plain Java
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai</artifactId>
    <version>0.31.0</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai-spring-boot-starter</artifactId>
    <version>0.31.0</version>
</dependency>
```

:::note
Before using any of the Azure OpenAI models, you need to [deploy](https://learn.microsoft.com/en-us/azure/ai-services/openai/how-to/create-resource?pivots=web-portal) them.
:::

## Creating AzureOpenAiChatModel

### Plain Java
```java
ChatLanguageModel model = AzureOpenAiChatModel.builder()
        .endpoint("https://langchain4j.openai.azure.com/")
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("gpt-4o")
        .build();
```
This will create an `AzureOpenAiChatModel` with default model parameters (e.g. `0.7` temperature, etc.).
Default model parameters can be customized, see the section below for more information.

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.chat-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.chat-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.chat-model.deployment-name=gpt-4o
```
This configuration will create an `AzureOpenAiChatModel` bean (with default model parameters),
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

## Customizing AzureOpenAiChatModel

### Plain Java
```java
ChatLanguageModel model = AzureOpenAiChatModel.builder()
        .endpoint(...)
        .serviceVersion(...)
        .apiKey(...)
        .nonAzureApiKey(...)
        .tokenCredential(...)
        .deploymentName(...)
        .tokenizer(...)
        .maxTokens(...)
        .temperature(...)
        .topP(...)
        .logitBias(...)
        .user(...)
        .n(...)
        .stop(...)
        .presencePenalty(...)
        .frequencyPenalty(...)
        .dataSources(...)
        .enhancements(...)
        .seed(...)
        .responseFormat(...)
        .timeout(...)
        .maxRetries(...)
        .proxyOptions(...)
        .logRequestsAndResponses(...)
        .openAIClient(...)
        .build();
```

### Spring Boot
```properties
langchain4j.azure-open-ai.chat-model.endpoint=...
langchain4j.azure-open-ai.chat-model.api-key=...
langchain4j.azure-open-ai.chat-model.non-azure-api-key=...
langchain4j.azure-open-ai.chat-model.organization-id=...
langchain4j.azure-open-ai.chat-model.deployment-name=...
langchain4j.azure-open-ai.chat-model.temperature=...
langchain4j.azure-open-ai.chat-model.top-p=
langchain4j.azure-open-ai.chat-model.max-tokens=...
langchain4j.azure-open-ai.chat-model.presence-penalty=...
langchain4j.azure-open-ai.chat-model.frequency-penalty=...
langchain4j.azure-open-ai.chat-model.response-format=...
langchain4j.azure-open-ai.chat-model.seed=...
langchain4j.azure-open-ai.chat-model.stop=...
langchain4j.azure-open-ai.chat-model.timeout=...
langchain4j.azure-open-ai.chat-model.max-retries=...
langchain4j.azure-open-ai.chat-model.log-requests-and-responses=...
```

See the description of some of the parameters above [here](https://learn.microsoft.com/en-us/azure/ai-services/openai/reference#completions).

## Creating AzureOpenAiStreamingChatModel

### Plain Java
```java
StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
        .endpoint("https://langchain4j.openai.azure.com/")
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("gpt-4o")
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.streaming-chat-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.streaming-chat-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.streaming-chat-model.deployment-name=gpt-4o
```

### Customizing AzureOpenAiStreamingChatModel

Similar to the `AzureOpenAiChatModel`, see above.

## Creating AzureOpenAiEmbeddingModel

### Plain Java
```java
EmbeddingModel model = AzureOpenAiEmbeddingModel.builder()
        .endpoint("https://langchain4j.openai.azure.com/")
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("text-embedding-3-small")
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.embedding-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.embedding-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.embedding-model.deployment-name=text-embedding-3-small
```

## Creating AzureOpenAiImageModel

### Plain Java
```java
ImageModel model = AzureOpenAiImageModel.builder()
        .endpoint("https://langchain4j.openai.azure.com/")
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("dall-e-3")
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.image-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.image-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.image-model.deployment-name=dall-e-3
```

## Creating AzureOpenAiTokenizer

### Plain Java
```java
Tokenizer tokenizer = new AzureOpenAiTokenizer();
// or
Tokenizer tokenizer = new AzureOpenAiTokenizer("gpt-4o");
```

### Spring Boot
The `AzureOpenAiTokenizer` bean is created automatically.
