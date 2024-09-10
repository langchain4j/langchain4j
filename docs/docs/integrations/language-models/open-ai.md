---
sidebar_position: 14
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
    <version>0.34.0</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>0.34.0</version>
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

## Creating `OpenAiChatModel`

### Plain Java
```java
ChatLanguageModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        ...
        .build();
```
This will create an instance of `OpenAiChatModel` with default model parameters (e.g. `gpt-3.5-turbo` model name, `0.7` temperature, etc.).
Default model parameters can be customized by providing values in the builder.

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.base-url=...
langchain4j.open-ai.chat-model.custom-headers=...
langchain4j.open-ai.chat-model.frequency-penalty=...
langchain4j.open-ai.chat-model.log-requests=...
langchain4j.open-ai.chat-model.log-responses=...
langchain4j.open-ai.chat-model.logit-bias=...
langchain4j.open-ai.chat-model.max-retries=...
langchain4j.open-ai.chat-model.max-tokens=...
langchain4j.open-ai.chat-model.model-name=...
langchain4j.open-ai.chat-model.organization-id=...
langchain4j.open-ai.chat-model.parallel-tool-calls=...
langchain4j.open-ai.chat-model.presence-penalty=...
langchain4j.open-ai.chat-model.proxy.host=...
langchain4j.open-ai.chat-model.proxy.port=...
langchain4j.open-ai.chat-model.proxy.type=...
langchain4j.open-ai.chat-model.response-format=...
langchain4j.open-ai.chat-model.seed=...
langchain4j.open-ai.chat-model.stop=...
langchain4j.open-ai.chat-model.strict-schema=...
langchain4j.open-ai.chat-model.strict-tools=...
langchain4j.open-ai.chat-model.temperature=...
langchain4j.open-ai.chat-model.timeout=...
langchain4j.open-ai.chat-model.top-p=
langchain4j.open-ai.chat-model.user=...
```
See the description of some of the parameters above [here](https://platform.openai.com/docs/api-reference/chat/create).

This configuration will create an `OpenAiChatModel` bean,
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

## Structured Outputs
The [Structured Outputs](https://openai.com/index/introducing-structured-outputs-in-the-api/) feature is supported
for both [tools](/tutorials/tools) and [JSON mode](/tutorials/ai-services#json-mode).

### Structured Outputs for tools
To enable Structured Outputs feature for tools, set `.strictTools(true)` when building the model:
```java
OpenAiChatModel.builder()
    ...
    .strictTools(true)
    .build(),
```
Please note that this will automatically make all tool parameters mandatory (`required` in json schema)
and set `additionalProperties=false` for each `object` in json schema. This is due to the current OpenAI limitations.

### Structured Outputs for JSON mode
To enable Structured Outputs feature for JSON mode, set `.responseFormat("json_schema")` and `.strictJsonSchema(true)`
when building the model:
```java
OpenAiChatModel.builder()
    ...
    .responseFormat("json_schema")
    .strictJsonSchema(true)
    .build(),
```
In this case `AiServices` will not append "You must answer strictly in the following JSON format: ..." string
to the end of the last `UserMessage`, but will create a Json schema from the given POJO and pass it to the LLM.
Please note that this works only when method return type is a POJO.
If the return type is something else, (like an enum or a `List<String>`),
the old behaviour is applied (with "You must answer strictly ...").
All return types will be supported in the near future.

## Creating `OpenAiStreamingChatModel`

### Plain Java
```java
OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        ...
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.open-ai.streaming-chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.streaming-chat-model.base-url=...
langchain4j.open-ai.streaming-chat-model.custom-headers=...
langchain4j.open-ai.streaming-chat-model.frequency-penalty=...
langchain4j.open-ai.streaming-chat-model.log-requests=...
langchain4j.open-ai.streaming-chat-model.log-responses=...
langchain4j.open-ai.streaming-chat-model.logit-bias=...
langchain4j.open-ai.streaming-chat-model.max-retries=...
langchain4j.open-ai.streaming-chat-model.max-tokens=...
langchain4j.open-ai.streaming-chat-model.model-name=...
langchain4j.open-ai.streaming-chat-model.organization-id=...
langchain4j.open-ai.streaming-chat-model.parallel-tool-calls=...
langchain4j.open-ai.streaming-chat-model.presence-penalty=...
langchain4j.open-ai.streaming-chat-model.proxy.host=...
langchain4j.open-ai.streaming-chat-model.proxy.port=...
langchain4j.open-ai.streaming-chat-model.proxy.type=...
langchain4j.open-ai.streaming-chat-model.response-format=...
langchain4j.open-ai.streaming-chat-model.seed=...
langchain4j.open-ai.streaming-chat-model.stop=...
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
        ...
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.open-ai.moderation-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.moderation-model.base-url=...
langchain4j.open-ai.moderation-model.custom-headers=...
langchain4j.open-ai.moderation-model.log-requests=...
langchain4j.open-ai.moderation-model.log-responses=...
langchain4j.open-ai.moderation-model.max-retries=...
langchain4j.open-ai.moderation-model.model-name=...
langchain4j.open-ai.moderation-model.organization-id=...
langchain4j.open-ai.moderation-model.proxy.host=...
langchain4j.open-ai.moderation-model.proxy.port=...
langchain4j.open-ai.moderation-model.proxy.type=...
langchain4j.open-ai.moderation-model.timeout=...
```


## Creating `OpenAiTokenizer`

### Plain Java
```java
Tokenizer tokenizer = new OpenAiTokenizer();
// or
Tokenizer tokenizer = new OpenAiTokenizer("gpt-4o");
```

### Spring Boot
The `OpenAiTokenizer` bean is created automatically by the Spring Boot starter.


## Examples
- [OpenAI Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/open-ai-examples/src/main/java)
