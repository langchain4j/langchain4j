---
sidebar_position: 15
---

# OpenAI

:::note

This is the documentation for the `OpenAI` integration, that uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).

If you are using Quarkus, please refer to the
[Quarkus LangChain4j documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/openai.html).

LangChain4j provides 4 different integrations with OpenAI for using chat models, and this is #1 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).
- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.
- [GitHub Models](/integrations/language-models/github-models) uses the Azure AI Inference API to access GitHub Models.

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
    <version>1.0.1</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

## API Key

To use OpenAI models, you will need an API key.
You can create one [here](https://platform.openai.com/api-keys).

<details>
<summary>What if I don't have an API key?</summary>

If you don't have your own OpenAI API key, don't worry.
You can temporarily use `demo` key, which we provide for free for demonstration purposes.
Be aware that when using the `demo` key, all requests to the OpenAI API need to go through our proxy,
which injects the real key before forwarding your request to the OpenAI API.
We do not collect or use your data in any way.
The `demo` key has a quota, is restricted to the `gpt-4o-mini` model, and should only be used for demonstration purposes.

```java
OpenAiChatModel model = OpenAiChatModel.builder()
    .baseUrl("http://langchain4j.dev/demo/openai/v1")
    .apiKey("demo")
    .modelName("gpt-4o-mini")
    .build();
```
</details>

## Creating `OpenAiChatModel`

### Plain Java
```java
ChatModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .build();


// You can also specify default chat request parameters using ChatRequestParameters or OpenAiChatRequestParameters
ChatModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .defaultRequestParameters(OpenAiChatRequestParameters.builder()
                .modelName("gpt-4o-mini")
                .build())
        .build();
```
This will create an instance of `OpenAiChatModel` with the specified default parameters.

### Spring Boot
Add to the `application.properties`:
```properties
# Mandatory properties:
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.model-name=gpt-4o-mini

# Optional properties:
langchain4j.open-ai.chat-model.base-url=...
langchain4j.open-ai.chat-model.custom-headers=...
langchain4j.open-ai.chat-model.frequency-penalty=...
langchain4j.open-ai.chat-model.log-requests=...
langchain4j.open-ai.chat-model.log-responses=...
langchain4j.open-ai.chat-model.logit-bias=...
langchain4j.open-ai.chat-model.max-retries=...
langchain4j.open-ai.chat-model.max-completion-tokens=...
langchain4j.open-ai.chat-model.max-tokens=...
langchain4j.open-ai.chat-model.metadata=...
langchain4j.open-ai.chat-model.organization-id=...
langchain4j.open-ai.chat-model.parallel-tool-calls=...
langchain4j.open-ai.chat-model.presence-penalty=...
langchain4j.open-ai.chat-model.project-id=...
langchain4j.open-ai.chat-model.reasoning-effort=...
langchain4j.open-ai.chat-model.response-format=...
langchain4j.open-ai.chat-model.seed=...
langchain4j.open-ai.chat-model.service-tier=...
langchain4j.open-ai.chat-model.stop=...
langchain4j.open-ai.chat-model.store=...
langchain4j.open-ai.chat-model.strict-schema=...
langchain4j.open-ai.chat-model.strict-tools=...
langchain4j.open-ai.chat-model.supported-capabilities=...
langchain4j.open-ai.chat-model.temperature=...
langchain4j.open-ai.chat-model.timeout=...
langchain4j.open-ai.chat-model.top-p=
langchain4j.open-ai.chat-model.user=...
```
See the description of most of the parameters above [here](https://platform.openai.com/docs/api-reference/chat/create).

This configuration will create an `OpenAiChatModel` bean,
which can be either used by an [AI Service](https://docs.langchain4j.dev/tutorials/spring-boot-integration/#langchain4j-spring-boot-starter)
or autowired where needed, for example:

```java
@RestController
class ChatModelController {

    ChatModel chatModel;

    ChatModelController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping("/model")
    public String model(@RequestParam(value = "message", defaultValue = "Hello") String message) {
        return chatModel.chat(message);
    }
}
```

## Structured Outputs
The [Structured Outputs](https://openai.com/index/introducing-structured-outputs-in-the-api/) feature is supported
for both [tools](/tutorials/tools) and [response format](/tutorials/ai-services#json-mode).

See more info on Structured Outputs [here](/tutorials/structured-outputs).

### Structured Outputs for Tools
To enable Structured Outputs feature for tools, set `.strictTools(true)` when building the model:
```java
OpenAiChatModel.builder()
    ...
    .strictTools(true)
    .build(),
```
Please note that this will automatically make all tool parameters mandatory (`required` in json schema)
and set `additionalProperties=false` for each `object` in json schema. This is due to the current OpenAI limitations.

### Structured Outputs for Response Format
To enable the Structured Outputs feature for response formatting when using AI Services,
set `.supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)` and `.strictJsonSchema(true)` when building the model:
```java
OpenAiChatModel.builder()
    ...
    .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
    .strictJsonSchema(true)
    .build();
```
In this case AI Service will automatically generate a JSON schema from the given POJO and pass it to the LLM.

## Creating `OpenAiStreamingChatModel`

### Plain Java
```java
StreamingChatModel model = OpenAiStreamingChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .build();

// You can also specify default chat request parameters using ChatRequestParameters or OpenAiChatRequestParameters
StreamingChatModel model = OpenAiStreamingChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .defaultRequestParameters(OpenAiChatRequestParameters.builder()
                .modelName("gpt-4o-mini")
                .build())
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
# Mandatory properties:
langchain4j.open-ai.streaming-chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.streaming-chat-model.model-name=gpt-4o-mini

# Optional properties:
langchain4j.open-ai.streaming-chat-model.base-url=...
langchain4j.open-ai.streaming-chat-model.custom-headers=...
langchain4j.open-ai.streaming-chat-model.frequency-penalty=...
langchain4j.open-ai.streaming-chat-model.log-requests=...
langchain4j.open-ai.streaming-chat-model.log-responses=...
langchain4j.open-ai.streaming-chat-model.logit-bias=...
langchain4j.open-ai.streaming-chat-model.max-retries=...
langchain4j.open-ai.streaming-chat-model.max-completion-tokens=...
langchain4j.open-ai.streaming-chat-model.max-tokens=...
langchain4j.open-ai.streaming-chat-model.metadata=...
langchain4j.open-ai.streaming-chat-model.organization-id=...
langchain4j.open-ai.streaming-chat-model.parallel-tool-calls=...
langchain4j.open-ai.streaming-chat-model.presence-penalty=...
langchain4j.open-ai.streaming-chat-model.project-id=...
langchain4j.open-ai.streaming-chat-model.reasoning-effort=...
langchain4j.open-ai.streaming-chat-model.response-format=...
langchain4j.open-ai.streaming-chat-model.seed=...
langchain4j.open-ai.streaming-chat-model.service-tier=...
langchain4j.open-ai.streaming-chat-model.stop=...
langchain4j.open-ai.streaming-chat-model.store=...
langchain4j.open-ai.streaming-chat-model.strict-schema=...
langchain4j.open-ai.streaming-chat-model.strict-tools=...
langchain4j.open-ai.streaming-chat-model.temperature=...
langchain4j.open-ai.streaming-chat-model.timeout=...
langchain4j.open-ai.streaming-chat-model.top-p=...
langchain4j.open-ai.streaming-chat-model.user=...
```


## Creating `OpenAiModerationModel`

### Plain Java
```java
ModerationModel model = OpenAiModerationModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("text-moderation-stable")
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
# Mandatory properties:
langchain4j.open-ai.moderation-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.moderation-model.model-name=text-moderation-stable

# Optional properties:
langchain4j.open-ai.moderation-model.base-url=...
langchain4j.open-ai.moderation-model.custom-headers=...
langchain4j.open-ai.moderation-model.log-requests=...
langchain4j.open-ai.moderation-model.log-responses=...
langchain4j.open-ai.moderation-model.max-retries=...
langchain4j.open-ai.moderation-model.organization-id=...
langchain4j.open-ai.moderation-model.project-id=...
langchain4j.open-ai.moderation-model.timeout=...
```


## Creating `OpenAiTokenCountEstimator`

```java
TokenCountEstimator tokenCountEstimator = new OpenAiTokenCountEstimator("gpt-4o-mini");
```


## HTTP Client

### Plain Java
When using the `langchain4j-open-ai` module,
the JDK's `java.net.http.HttpClient` is used as the default HTTP client.

You can customize it or use any other HTTP client of your choice.
More information can be found [here](/tutorials/customizable-http-client).

### Spring Boot
When using the `langchain4j-open-ai-spring-boot-starter` Spring Boot starter,
the Spring's `RestClient` is used as the default HTTP client.

You can customize it or use any other HTTP client of your choice.
More information can be found [here](/tutorials/customizable-http-client).

## Examples
- [OpenAI Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/open-ai-examples/src/main/java)
