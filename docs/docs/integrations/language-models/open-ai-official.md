---
sidebar_position: 16
---

# OpenAI Official SDK

:::note

This is the documentation for the `OpenAI Official SDK` integration, that uses the [official OpenAI Java SDK](https://github.com/openai/openai-java).

LangChain4j provides 3 different integrations with OpenAI for using chat models, and this is #2 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).
- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.

:::

## Use cases for this integration

This integration uses the [OpenAI Java SDK GitHub Repository](https://github.com/openai/openai-java), and will work for all OpenAI models which can be provided by:

- OpenAI
- Microsoft Foundry
- GitHub Models

It will also work with models supporting the OpenAI API, such as DeepSeek.

## OpenAI Documentation

- [OpenAI Java SDK GitHub Repository](https://github.com/openai/openai-java)
- [OpenAI API Documentation](https://platform.openai.com/docs/introduction)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-official</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

## Configuring the models

:::note
This configuration, as well as the next section about its usage, is for non-streaming mode (also known as "blocking" or "synchronous" mode).
Streaming mode is detailed 2 sections below: it allows for real-time chat with the model, but is more complex to use.
:::

To use OpenAI models, you usually need an endpoint URL, an API key, and a model name. This depends on where the model is hosted, and this integration tries
to make it easier with some auto-configuration:

### Generic configuration

```java
import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;

import static com.openai.models.ChatModel.GPT_5_MINI;

// ....

ChatModel model = OpenAiOfficialChatModel.builder()
        .baseUrl(System.getenv("OPENAI_BASE_URL"))
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(GPT_5_MINI)
        .build();
```

### OpenAI configuration

The OpenAI `baseUrl` (`https://api.openai.com/v1`) is the default, so you can omit it:

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(GPT_5_MINI)
        .build();
```

### Azure OpenAI configuration

#### Generic configuration

For Azure OpenAI, setting a `baseUrl` is mandatory, and Azure OpenAI will be automatically detected if that URL ends with `openai.azure.com`:

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .modelName(GPT_5_MINI)
        .build();
```

If you want to force the usage of Azure OpenAI, you can also use the `isAzure()` method:

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .isAzure(true)
        .modelName(GPT_5_MINI)
        .build();
```

#### Passwordless authentication

You can authenticate to Azure OpenAI using "passwordless" authentication, which is more secure as you won't manage the API key.

To do so, you must first configure your Azure OpenAI instance to support managed identity, and then give access to this application, for example:

```bash
# Enable system managed identity on the Azure OpenAI instance
az cognitiveservices account identity assign \
    --name <your-openai-instance-name> \
    --resource-group <your-resource-group>

# Get your logged-in identity
az ad signed-in-user show \
    --query id -o tsv
    
# Give access to the Azure OpenAI instance
az role assignment create \
    --role "Cognitive Services OpenAI User" \
    --assignee <your-logged-identity-from-the-previous-command> \
    --scope "/subscriptions/<your-subscription-id>/resourceGroups/<your-resource-group>"
```

Then, you need to add the `azure-identity` dependency to your Maven `pom.xml`:

```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
</dependency>
```

When no API key is configured, LangChain4j will then automatically use passwordless authentication with Azure OpenAI.

### GitHub Models configuration

For GitHub Models, you can use the default `baseUrl` (`https://models.inference.ai.azure.com`):

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .baseUrl("https://models.inference.ai.azure.com")
        .apiKey(System.getenv("GITHUB_TOKEN"))
        .modelName(GPT_5_MINI)
        .build();
```

Or you can use the `isGitHubModels()` method to force the usage of GitHub Models, which will automatically set the `baseUrl`:

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .apiKey(System.getenv("GITHUB_TOKEN"))
        .modelName(GPT_5_MINI)
        .isGitHubModels(true)
        .build();
```

As GitHub Models are usually configured using the `GITHUB_TOKEN` environment variable, which is automatically filled up when using GitHub Actions or GitHub Codespaces, it will be automatically detected:

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .modelName(GPT_5_MINI)
        .isGitHubModels(true)
        .build();
```

This last configuration is easier to use, and more secure as the `GITHUB_TOKEN` environment variable is not exposed in the code or in the GitHub logs.

## Using the models

In the previous section, an `OpenAiOfficialChatModel` object was created, which implements the `ChatModel` interface.

It can be either used by an [AI Service](https://docs.langchain4j.dev/tutorials/spring-boot-integration/#langchain4j-spring-boot-starter) or used directly in a Java application.

In this example, it is autowired as a Spring Bean:

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
OpenAiOfficialChatModel.builder()
        // ...
        .strictTools(true)
        .build();
```

Please note that this will automatically make all tool parameters mandatory (`required` in json schema)
and set `additionalProperties=false` for each `object` in json schema. This is due to the current OpenAI limitations.

### Structured Outputs for Response Format
To enable the Structured Outputs feature for response formatting when using AI Services,
set `supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))` and `.strictJsonSchema(true)` when building the model:

```java
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

// ...

OpenAiChatModel.builder()
        // ...
        .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
        .strictJsonSchema(true)
        .build();
```

In this case AI Service will automatically generate a JSON schema from the given POJO and pass it to the LLM.

## Configuring the models for streaming

:::note
In the two sections above, we detailed how to configure the models for non-streaming mode (also known as "blocking" or "synchronous" mode).
This section is for streaming mode, which allows for real-time chat with the model, but is more complex to use.
:::

This is similar to the non-streaming mode, but you need to use the `OpenAiOfficialStreamingChatModel` class instead of `OpenAiOfficialChatModel`:

```java
StreamingChatModel model = OpenAiOfficialStreamingChatModel.builder()
        .baseUrl(System.getenv("OPENAI_BASE_URL"))
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(GPT_5_MINI)
        .build();
```

You can also use the specific `isAzure()` and `isGitHubModels()` methods to force the usage of Azure OpenAI or GitHub Models, as detailed in the non-streaming configuration section.

## Prompt Caching

OpenAI caches long, repeated prompt prefixes and bills a cache read at a fraction of the input rate.
The controls below work with both the Chat Completions API models and the Responses API models.

More info on prompt caching can be found [here](https://developers.openai.com/api/docs/guides/prompt-caching).

### `promptCacheKey` and `promptCacheOptions`

`promptCacheKey` is an optional string that steers routing so that related requests are more likely to
reach a machine holding the cache entry. It does not pin a request to a machine, nor guarantee a cache hit.

`gpt-5.6` and later match a cached prefix exactly at a *breakpoint*, without falling back to a shorter
unmarked prefix. `promptCacheOptions` controls where breakpoints come from:

- `implicit` - OpenAI places a breakpoint at the end of the newest eligible message.
- `explicit` - only the breakpoints you set are used. Without any breakpoint, nothing is cached.

For the Responses API models both are available on the model builder:

```java
OpenAiOfficialResponsesChatModel model = OpenAiOfficialResponsesChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5.6")
        .promptCacheKey("satisfaction_judge_v1")
        .promptCacheOptions(OpenAiOfficialPromptCacheOptions.builder()
                .mode(OpenAiOfficialPromptCacheOptions.MODE_EXPLICIT)
                .ttl(OpenAiOfficialPromptCacheOptions.TTL_30M)
                .build())
        .build();
```

For the Chat Completions API models they are set through `OpenAiOfficialChatRequestParameters`, either as
model defaults or per request:

```java
OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5.6")
        .defaultRequestParameters(OpenAiOfficialChatRequestParameters.builder()
                .promptCacheKey("satisfaction_judge_v1")
                .promptCacheOptions(OpenAiOfficialPromptCacheOptions.explicit())
                .build())
        .build();
```

`OpenAiOfficialPromptCacheOptions.implicit()` and `OpenAiOfficialPromptCacheOptions.explicit()` are
shorthands for options that only set the mode.

`promptCacheOptions.ttl` supersedes `promptCacheRetention`, which applies to models older than `gpt-5.6`.
OpenAI rejects a request that carries both.

### `promptCacheBreakpoint`

`SystemMessage`, `UserMessage` and `ToolExecutionResultMessage` can each be marked as a prompt cache
breakpoint by setting the `prompt_cache_breakpoint` attribute to `explicit`. Because prompt caching is
prefix-based, the breakpoint is applied to the **last content block** of the marked message, so that
everything up to and including that message forms the cached prefix.

`SystemMessage` and `UserMessage` expose a mutable attributes map:

```java
SystemMessage systemMessage = SystemMessage.from(SHARED_INSTRUCTIONS);
systemMessage.attributes().put(OpenAiOfficialPromptCacheBreakpoint.ATTRIBUTE_KEY,
                               OpenAiOfficialPromptCacheBreakpoint.MODE_EXPLICIT);
```

They can also be built with the attribute set upfront, which is the only option for the immutable
`ToolExecutionResultMessage`:

```java
SystemMessage systemMessage = SystemMessage.builder()
        .text(SHARED_INSTRUCTIONS)
        .attributes(Map.of(OpenAiOfficialPromptCacheBreakpoint.ATTRIBUTE_KEY,
                           OpenAiOfficialPromptCacheBreakpoint.MODE_EXPLICIT))
        .build();

ToolExecutionResultMessage toolResult = someToolExecutionResultMessage.toBuilder()
        .attributes(Map.of(OpenAiOfficialPromptCacheBreakpoint.ATTRIBUTE_KEY,
                           OpenAiOfficialPromptCacheBreakpoint.MODE_EXPLICIT))
        .build();
```

Marking a message makes LangChain4j send that message's content as a list of content blocks, since the
plain string form cannot carry a breakpoint.

`AiMessage` cannot carry a breakpoint: assistant output blocks are not among the block types OpenAI
accepts one on. Marking an `AiMessage`, or using any mode other than `explicit`, fails fast instead of
producing an HTTP 400.

Each request supports up to four cache writes, one of which is consumed by the `implicit` mode.

### Reading cache token counts

`OpenAiOfficialTokenUsage.InputTokensDetails` reports how many input tokens were read from and written to
the prompt cache:

```java
OpenAiOfficialTokenUsage tokenUsage = (OpenAiOfficialTokenUsage) chatResponse.tokenUsage();

tokenUsage.inputTokensDetails().cachedTokens();      // tokens read from the cache
tokenUsage.inputTokensDetails().cacheWriteTokens();  // tokens written to the cache
```

`cacheWriteTokens()` returns `null` when the model provider did not report it, which is distinct from a
reported zero.

## OpenAI Responses API

:::note
This feature is experimental and may change in future releases.
:::

OpenAI's [Responses API](https://platform.openai.com/docs/api-reference/responses) (`/v1/responses`) is an alternative to the Chat Completions API.

### Creating `OpenAiOfficialResponsesChatModel`

```java
ChatModel model = OpenAiOfficialResponsesChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5.4")
        .build();
```

### Creating `OpenAiOfficialResponsesStreamingChatModel`

```java
StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(GPT_5_MINI)
        .build();
```

You can also use `OpenAiOfficialResponsesChatRequestParameters` to configure default request parameters:
```java
StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .defaultRequestParameters(OpenAiOfficialResponsesChatRequestParameters.builder()
                .modelName("gpt-4o-mini")
                .previousResponseId("resp_abc123")
                .reasoningEffort("medium")
                .store(true)
                .build())
        .build();
```

### `OpenAiOfficialResponsesChatRequestParameters`

`OpenAiOfficialResponsesChatRequestParameters` extends `DefaultChatRequestParameters` with Responses API-specific fields:
`previousResponseId`, `maxToolCalls`, `parallelToolCalls`, `topLogprobs`, `truncation`, `include`,
`serviceTier`, `safetyIdentifier`, `promptCacheKey`, `promptCacheRetention`, `promptCacheOptions`,
`reasoningEffort`, `reasoningSummary`, `textVerbosity`, `streamIncludeObfuscation`, `store`, `strictTools`,
`strictJsonSchema`.

These parameters can be configured as defaults when creating the model (via `defaultRequestParameters` on the builder),
or passed per-request via `ChatRequest` (per-request parameters override the defaults):
```java
ChatRequest chatRequest = ChatRequest.builder()
        .messages(UserMessage.from("Hello"))
        .parameters(OpenAiOfficialResponsesChatRequestParameters.builder()
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

To enable reasoning summaries, set `reasoningSummary` to `Reasoning.Summary.AUTO` on the builder
(or via `OpenAiOfficialResponsesChatRequestParameters`).
You can also control how much effort the model puts into reasoning with `reasoningEffort`.

```java
ChatModel model = OpenAiOfficialResponsesChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5-mini")
        .reasoningEffort(ReasoningEffort.LOW)
        .reasoningSummary(Reasoning.Summary.AUTO)
        .build();

ChatResponse response = model.chat("What is the capital of Germany?");
response.aiMessage().text();     // "The capital of Germany is Berlin."
response.aiMessage().thinking(); // reasoning summary text
```

When `reasoningSummary` is set for `OpenAiOfficialResponsesStreamingChatModel`,
the `StreamingChatResponseHandler.onPartialThinking()` callback will be invoked
as reasoning summary tokens are streamed:

```java
StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5-mini")
        .reasoningEffort(ReasoningEffort.LOW)
        .reasoningSummary(Reasoning.Summary.AUTO)
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
ChatModel model = OpenAiOfficialResponsesChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-5-mini")
        .reasoningEffort(ReasoningEffort.MEDIUM)
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

This works identically for `OpenAiOfficialResponsesStreamingChatModel`.

### `OpenAiOfficialResponsesChatResponseMetadata`

The response metadata for the Responses API provides additional fields beyond the standard `ChatResponseMetadata`:

```java
OpenAiOfficialResponsesChatResponseMetadata metadata =
        (OpenAiOfficialResponsesChatResponseMetadata) chatResponse.metadata();

metadata.id();               // Response ID (can be used as previousResponseId)
metadata.modelName();        // Model name used for the request
metadata.finishReason();     // Finish reason (STOP, LENGTH, TOOL_EXECUTION, CONTENT_FILTER, OTHER)
metadata.tokenUsage();       // Returns OpenAiOfficialTokenUsage with detailed token counts
metadata.createdAt();        // Timestamp when the response was created
metadata.completedAt();      // Timestamp when the response was completed
metadata.serviceTier();      // Service tier used for the request
```
