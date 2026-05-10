---
sidebar_position: 15
---

# OpenAI

:::note

This is the documentation for the `OpenAI` integration, that uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).

If you are using Quarkus, please refer to the
[Quarkus LangChain4j documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/openai.html).

LangChain4j provides 3 different integrations with OpenAI for using chat models, and this is #1 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).
- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.

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
    <version>1.14.1</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.14.1-beta24</version>
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
langchain4j.open-ai.chat-model.return-thinking=...
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

# Optional Property: Custom Parameters (user-defined key=value) 
langchain4j.open-ai.chat-model.custom-parameters.<key>=<value>
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

### Thinking / Reasoning
This setting is intended for [DeepSeek](https://api-docs.deepseek.com/guides/reasoning_model).

When the `returnThinking` parameter is enabled while building `OpenAiChatModel` or `OpenAiStreamingChatModel`,
the `reasoning_content` field of the DeepSeek API response will be parsed
and returned inside `AiMessage.thinking()`.

When the `returnThinking` parameter is enabled for `OpenAiStreamingChatModel`,
the `StreamingChatResponseHandler.onPartialThinking()` and `TokenStream.onPartialThinking()`
callbacks will be invoked when the DeepSeek API streams `reasoning_content`.

Here is an example of how to configure thinking:
```java
ChatModel model = OpenAiChatModel.builder()
        .baseUrl("https://api.deepseek.com/v1")
        .apiKey(System.getenv("DEEPSEEK_API_KEY"))
        .modelName("deepseek-reasoner")
        .returnThinking(true)
        .build();
```

When the `sendThinking` parameter is enabled while building `OpenAiChatModel` or `OpenAiStreamingChatModel`,
the `AiMessage.thinking()` will be sent in the request to the DeepSeek API.
The name of the field can be configured by using the `sendThinking(boolean, String)` builder method.
By default, the `reasoning_content` field name is used.

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
langchain4j.open-ai.streaming-chat-model.return-thinking=...
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

# Optional Property: Custom Parameters (user-defined key=value) 
langchain4j.open-ai.streaming-chat-model.custom-parameters.<key>=<value>
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

## Setting custom chat request parameters

When using `OpenAiChatModel` and `OpenAiStreamingChatModel`,
you can configure custom parameters for the chat request within the HTTP request's JSON body.
Here is an example of how to enable web search:
```java
record ApproximateLocation(String city) {}
record UserLocation(String type, ApproximateLocation approximate) {}
record WebSearchOptions(UserLocation user_location) {}
WebSearchOptions webSearchOptions = new WebSearchOptions(new UserLocation("approximate", new ApproximateLocation("London")));
Map<String, Object> customParameters = Map.of("web_search_options", webSearchOptions);

ChatRequest chatRequest = ChatRequest.builder()
    .messages(UserMessage.from("Where can I buy good coffee?"))
    .parameters(OpenAiChatRequestParameters.builder()
        .modelName("gpt-4o-mini-search-preview")
        .customParameters(customParameters)
        .build())
    .build();

ChatModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .logRequests(true)
        .build();

ChatResponse chatResponse = model.chat(chatRequest);
```

This will produce an HTTP request with the following body:
```json
{
  "model" : "gpt-4o-mini-search-preview",
  "messages" : [ {
    "role" : "user",
    "content" : "Where can I buy good coffee?"
  } ],
  "web_search_options" : {
    "user_location" : {
      "type" : "approximate",
      "approximate" : {
        "city" : "London"
      }
    }
  }
}
```

Alternatively, custom parameters can also be specified as a structure of nested maps:
```java
Map<String, Object> customParameters = Map.of(
    "web_search_options", Map.of(
        "user_location", Map.of(
            "type", "approximate",
            "approximate", Map.of("city", "London")
        )
    )
);
```

## Accessing raw HTTP responses and Server-Sent Events (SSE)

When using `OpenAiChatModel`, you can access the raw HTTP response:
```java
SuccessfulHttpResponse rawHttpResponse = ((OpenAiChatResponseMetadata) chatResponse.metadata()).rawHttpResponse();
System.out.println(rawHttpResponse.body());
System.out.println(rawHttpResponse.headers());
System.out.println(rawHttpResponse.statusCode());
```

When using `OpenAiStreamingChatModel`, you can access the raw HTTP response (see above) and raw Server-Sent Events:
```java
List<ServerSentEvent> rawServerSentEvents = ((OpenAiChatResponseMetadata) chatResponse.metadata()).rawServerSentEvents();
System.out.println(rawServerSentEvents.get(0).data());
System.out.println(rawServerSentEvents.get(0).event());
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

## OpenAI Responses API

:::note
This feature is experimental and may change in future releases.
:::

OpenAI's [Responses API](https://platform.openai.com/docs/api-reference/responses) (`/v1/responses`) is an alternative to the Chat Completions API.

### Creating `OpenAiResponsesChatModel`

```java
ChatModel model = OpenAiResponsesChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5.4")
        .build();
```

### Creating `OpenAiResponsesStreamingChatModel`

```java
StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .build();
```

### `OpenAiResponsesChatRequestParameters`

`OpenAiResponsesChatRequestParameters` extends `DefaultChatRequestParameters` with Responses API-specific fields:
`previousResponseId`, `maxToolCalls`, `parallelToolCalls`, `topLogprobs`, `truncation`, `include`,
`serviceTier`, `safetyIdentifier`, `promptCacheKey`, `promptCacheRetention`, `reasoningEffort`,
`reasoningSummary`, `textVerbosity`, `streamIncludeObfuscation`, `store`, `strictTools`, `strictJsonSchema`.

These parameters can be configured as defaults when creating the model (via `defaultRequestParameters` on the builder),
or passed per-request via `ChatRequest` (per-request parameters override the defaults):
```java
ChatRequest chatRequest = ChatRequest.builder()
        .messages(UserMessage.from("Hello"))
        .parameters(OpenAiResponsesChatRequestParameters.builder()
                .modelName("gpt-4o-mini")
                .previousResponseId("resp_abc123")
                .store(true)
                .build())
        .build();
```

### Thinking / Reasoning
OpenAI reasoning models (e.g. `gpt-5.4`, `gpt-5-mini`) support
[reasoning summaries](https://developers.openai.com/api/docs/guides/reasoning#reasoning-summaries)
that expose a summary of the model's internal reasoning.

To enable reasoning summaries, set `reasoningSummary` to `"auto"` on the builder
(or via `OpenAiResponsesChatRequestParameters`).
You can also control how much effort the model puts into reasoning with `reasoningEffort`.

```java
ChatModel model = OpenAiResponsesChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5-mini")
        .reasoningEffort("low")
        .reasoningSummary("auto")
        .build();

ChatResponse response = model.chat("What is the capital of Germany?");
response.aiMessage().text();     // "The capital of Germany is Berlin."
response.aiMessage().thinking(); // reasoning summary text
```

When `reasoningSummary` is set for `OpenAiResponsesStreamingChatModel`,
the `StreamingChatResponseHandler.onPartialThinking()` callback will be invoked
as reasoning summary tokens are streamed:

```java
StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5-mini")
        .reasoningEffort("low")
        .reasoningSummary("auto")
        .build();
```

The reasoning summary in `AiMessage.thinking()` is informational and does not need to be sent back
in follow-up requests — OpenAI discards it between turns. To actually preserve the model's reasoning
state across turns (e.g. between tool calls), use encrypted reasoning instead, described below.

#### Encrypted Reasoning (Keeping Reasoning in Context)

When `store` is `false` (by default) or your organization has zero data retention,
the model's reasoning context is lost between turns.
To preserve it, request [encrypted reasoning content](https://developers.openai.com/api/docs/guides/reasoning#keeping-reasoning-items-in-context)
via the `include` parameter:

```java
ChatModel model = OpenAiResponsesChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5-mini")
        .reasoningEffort("medium")
        .include(List.of("reasoning.encrypted_content"))
        .build();
```

When `include` contains `"reasoning.encrypted_content"`, the response's reasoning items
will contain an opaque encrypted blob. This is automatically stored in
`AiMessage.attributes()` under the key `"encrypted_reasoning"`.

When you pass that `AiMessage` back in a follow-up request (e.g. after a tool call),
the encrypted reasoning is automatically included in the request,
allowing the model to resume its reasoning context:

```java
// Turn 1: model calls a tool
ChatResponse response1 = model.chat(ChatRequest.builder()
        .messages(userMessage)
        .parameters(ChatRequestParameters.builder()
                .toolSpecifications(weatherTool)
                .build())
        .build());

AiMessage aiMessage1 = response1.aiMessage();
// aiMessage1.attribute("encrypted_reasoning", String.class) is not null

// Turn 2: send tool result back — encrypted reasoning is sent automatically
ChatResponse response2 = model.chat(ChatRequest.builder()
        .messages(
                userMessage,
                aiMessage1, // contains encrypted reasoning in attributes
                ToolExecutionResultMessage.from(aiMessage1.toolExecutionRequests().get(0), "sunny"))
        .parameters(ChatRequestParameters.builder()
                .toolSpecifications(weatherTool)
                .build())
        .build());
```

This works identically for `OpenAiResponsesStreamingChatModel`.

### `OpenAiResponsesChatResponseMetadata`

The response metadata for the Responses API provides additional fields beyond the standard `ChatResponseMetadata`:

```java
OpenAiResponsesChatResponseMetadata metadata =
        (OpenAiResponsesChatResponseMetadata) chatResponse.metadata();

metadata.id();               // Response ID (can be used as previousResponseId)
metadata.modelName();        // Model name used for the request
metadata.finishReason();     // Finish reason (STOP, LENGTH, TOOL_EXECUTION, OTHER)
metadata.tokenUsage();       // Returns OpenAiTokenUsage with detailed token counts
metadata.createdAt();        // Timestamp when the response was created
metadata.completedAt();      // Timestamp when the response was completed
metadata.serviceTier();      // Service tier used for the request

// Raw HTTP access (same as Chat Completions API)
metadata.rawHttpResponse();
metadata.rawServerSentEvents();
```

## Examples
- [OpenAI Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/open-ai-examples/src/main/java)
