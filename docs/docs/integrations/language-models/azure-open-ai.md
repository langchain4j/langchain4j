---
sidebar_position: 3
---

# Azure OpenAI

:::note
If you are using Quarkus, please refer to the
[Quarkus LangChain4j documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/openai.html#_azure_openai).
:::

Azure OpenAI provides a few language models (`gpt-35-turbo`, `gpt-4`, `gpt-4o`, etc.)
that can be used for various natural language processing tasks.


## Azure OpenAI Documentation

- [Azure OpenAI Documentation](https://learn.microsoft.com/en-us/azure/ai-services/openai/)

## Maven Dependency

### Plain Java

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai</artifactId>
    <version>0.34.0</version>
</dependency>
```

### Spring Boot

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai-spring-boot-starter</artifactId>
    <version>0.34.0</version>
</dependency>
```

:::note
Before using any of the Azure OpenAI models, you need to [deploy](https://learn.microsoft.com/en-us/azure/ai-services/openai/how-to/create-resource?pivots=web-portal) them.
:::

## Creating `AzureOpenAiChatModel` with an API Key

### Plain Java

```java
ChatLanguageModel model = AzureOpenAiChatModel.builder()
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("gpt-4o")
        .endpoint("https://langchain4j.openai.azure.com/")
        ...
        .build();
```

This will create an instance of `AzureOpenAiChatModel` with default model parameters (e.g. `0.7` temperature, etc.)
and an API key stored in the `AZURE_OPENAI_KEY` environment variable.
Default model parameters can be customized by providing values in the builder.

### Spring Boot

Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.chat-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.chat-model.deployment-name=gpt-4o
langchain4j.azure-open-ai.chat-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.chat-model.frequency-penalty=...
langchain4j.azure-open-ai.chat-model.log-requests-and-responses=...
langchain4j.azure-open-ai.chat-model.max-retries=...
langchain4j.azure-open-ai.chat-model.max-tokens=...
langchain4j.azure-open-ai.chat-model.organization-id=...
langchain4j.azure-open-ai.chat-model.presence-penalty=...
langchain4j.azure-open-ai.chat-model.response-format=...
langchain4j.azure-open-ai.chat-model.seed=...
langchain4j.azure-open-ai.chat-model.stop=...
langchain4j.azure-open-ai.chat-model.temperature=...
langchain4j.azure-open-ai.chat-model.timeout=...
langchain4j.azure-open-ai.chat-model.top-p=
```
See the description of some of the parameters above [here](https://learn.microsoft.com/en-us/azure/ai-services/openai/reference#completions).

This configuration will create an `AzureOpenAiChatModel` bean (with default model parameters),
which can be either used by an [AI Service](https://docs.langchain4j.dev/tutorials/spring-boot-integration/#langchain4j-spring-boot-starter)
or autowired where needed, for example:

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

## Creating `AzureOpenAiChatModel` with Azure Credentials

API key can have a few security issues (can be committed, can be passed around, etc.).
If you want to improve security, it is recommended to use Azure Credentials instead.
For that, it is necessary to add the `azure-identity` dependency to the project.

```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <scope>compile</scope>
</dependency>
```

Then, you can create an `AzureOpenAiChatModel` using the [DefaultAzureCredentialBuilder](https://learn.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredentialbuilder?view=azure-java-stable) API:  

```java
ChatLanguageModel model = AzureOpenAiChatModel.builder()
        .deploymentName("gpt-4o")
        .endpoint("https://langchain4j.openai.azure.com/")
        .tokenCredential(new DefaultAzureCredentialBuilder().build())
        .build();
```

:::note
Notice that you need to deploy your model using Managed Identities. Check the [Azure CLI deployment script](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/script/deploy-azure-openai-security.sh) for more information.
:::


## Creating `AzureOpenAiStreamingChatModel`

### Plain Java
```java
StreamingChatLanguageModel model = AzureOpenAiStreamingChatModel.builder()
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("gpt-4o")
        .endpoint("https://langchain4j.openai.azure.com/")
        ...
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.streaming-chat-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.streaming-chat-model.deployment-name=gpt-4o
langchain4j.azure-open-ai.streaming-chat-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.streaming-chat-model.frequency-penalty=...
langchain4j.azure-open-ai.streaming-chat-model.log-requests-and-responses=...
langchain4j.azure-open-ai.streaming-chat-model.max-retries=...
langchain4j.azure-open-ai.streaming-chat-model.max-tokens=...
langchain4j.azure-open-ai.streaming-chat-model.organization-id=...
langchain4j.azure-open-ai.streaming-chat-model.presence-penalty=...
langchain4j.azure-open-ai.streaming-chat-model.response-format=...
langchain4j.azure-open-ai.streaming-chat-model.seed=...
langchain4j.azure-open-ai.streaming-chat-model.stop=...
langchain4j.azure-open-ai.streaming-chat-model.temperature=...
langchain4j.azure-open-ai.streaming-chat-model.timeout=...
langchain4j.azure-open-ai.streaming-chat-model.top-p=...
```


## Creating `AzureOpenAiTokenizer`

### Plain Java
```java
Tokenizer tokenizer = new AzureOpenAiTokenizer();
// or
Tokenizer tokenizer = new AzureOpenAiTokenizer("gpt-4o");
```

### Spring Boot
The `AzureOpenAiTokenizer` bean is created automatically by the Spring Boot starter.


## APIs

- `AzureOpenAiChatModel`
- `AzureOpenAiStreamingChatModel`
- `DefaultAzureCredentialBuilder`
- `AzureOpenAiTokenizer`


## Examples

- [Azure OpenAI Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/azure-open-ai-examples/src/main/java)
- [AzureOpenAiSecurityExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/java/AzureOpenAiSecurityExamples.java) with its [Azure CLI deployment script](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/script/deploy-azure-openai-security.sh)
