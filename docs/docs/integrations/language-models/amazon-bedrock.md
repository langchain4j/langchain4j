---
sidebar_position: 1
---

# Amazon Bedrock

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-bedrock</artifactId>
    <version>1.19.0</version>
</dependency>
```

## AWS credentials
In order to use Amazon Bedrock models, you need to configure AWS credentials.
One of the options is to set the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables. More information can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/security-iam.html). Alternatively, set the `AWS_BEARER_TOKEN_BEDROCK` environment variable locally for API Key authentication. For additional API key details, refer to [docs](https://docs.aws.amazon.com/bedrock/latest/userguide/api-keys.html).

## BedrockChatModel
Supported models and their features can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference-supported-models-features.html).

Models ids can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html).

### Configuration
```java
ChatModel model = BedrockChatModel.builder()
        .client(BedrockRuntimeClient)
        .region(...)
        .modelId("us.amazon.nova-lite-v1:0")
        .returnThinking(...)
        .sendThinking(...)
        .timeout(...)
        .maxRetries(...)
        .logRequests(...)
        .logResponses(...)
        .listeners(...)
        .defaultRequestParameters(BedrockChatRequestParameters.builder()
                .modelName(...)
                .temperature(...)
                .topP(...)
                .maxOutputTokens(...)
                .stopSequences(...)
                .toolSpecifications(...)
                .toolChoice(...)
                .additionalModelRequestFields(...)
                .additionalModelRequestField(...)
                .enableReasoning(...)
                .promptCaching(...)
                .build())
        .build();
```

### Examples

- [BedrockChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/converse/BedrockChatModelExample.java)

## BedrockStreamingChatModel
Supported models and their features can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference-supported-models-features.html).

Models ids can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html).

### Configuration
```java
StreamingChatModel model = BedrockStreamingChatModel.builder()
        .client(BedrockRuntimeAsyncClient)
        .region(...)
        .modelId("us.amazon.nova-lite-v1:0")
        .returnThinking(...)
        .sendThinking(...)
        .timeout(...)
        .logRequests(...)
        .logResponses(...)
        .listeners(...)
        .defaultRequestParameters(BedrockChatRequestParameters.builder()
                .modelName(...)
                .temperature(...)
                .topP(...)
                .maxOutputTokens(...)
                .stopSequences(...)
                .toolSpecifications(...)
                .toolChoice(...)
                .additionalModelRequestFields(...)
                .additionalModelRequestField(...)
                .enableReasoning(...)
                .promptCaching(...)
                .build())
        .build();
```

### Examples

- [BedrockStreamingChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/converse/BedrockStreamingChatModelExample.java)

## Amazon Bedrock Guardrails

`BedrockChatModel` and `BedrockStreamingChatModel` support [Amazon Bedrock Guardrails](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails.html).
They are evaluated by Amazon Bedrock and are separate from LangChain4j [guardrails](/tutorials/guardrails); you can use both together.

Configure a guardrail through `BedrockChatRequestParameters` and set them as the model's `defaultRequestParameters`
to apply the guardrail to every request, or pass them in a `ChatRequest` to apply it to a single request:

```java
BedrockChatRequestParameters parameters = BedrockChatRequestParameters.builder()
        .guardrailConfiguration(BedrockGuardrailConfiguration.builder()
                .guardrailIdentifier("guardrail-id")
                .guardrailVersion("1")
                .build())
        .build();

ChatModel model = BedrockChatModel.builder()
        .modelId("us.amazon.nova-lite-v1:0")
        .defaultRequestParameters(parameters)
        .build();
```

When the guardrail intervenes, the response's `finishReason()` is `FinishReason.CONTENT_FILTER`,
and the assessment details are available via `((BedrockChatResponseMetadata) response.metadata()).guardrailAssessmentSummary()`.

### Evaluating only selected user messages

By default, the guardrail evaluates the text of all user and AI messages in the request.
Bedrock never evaluates tool results, tool definitions, tool calls or the system prompt.

To reduce cost and latency, set `guardContentPlacement` to select which user messages the guardrail evaluates:
- `LAST_USER_MESSAGE`: only the last user message.
- `ALL_USER_MESSAGES`: only the user messages, all of them.

```java
BedrockGuardrailConfiguration guardrailConfiguration = BedrockGuardrailConfiguration.builder()
        .guardrailIdentifier("guardrail-id")
        .guardrailVersion("1")
        .guardContentPlacement(BedrockGuardContentPlacement.LAST_USER_MESSAGE)
        .build();
```

The selected user messages are sent as Bedrock [`guardContent`](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-converse-api.html) blocks,
and the guardrail evaluates **only** those as input: other user messages and AI messages are not evaluated.
The model's response is still evaluated.
Depending on the policies the guardrail uses, Bedrock may still evaluate some content outside `guardContent` blocks.

:::warning
Content outside the selected messages is not checked.
For example, with `LAST_USER_MESSAGE` and [chat memory](/tutorials/chat-memory), a user message that the guardrail blocked
is sent to the model without evaluation on later turns.
If you use the guardrail as a security control rather than to reduce cost, prefer the default (no `guardContentPlacement`).
:::

Only text and PNG/JPEG images can be sent as `guardContent`.
If a selected message contains other content (for example, a PDF or a GIF image),
`guardContentPlacement` is ignored for that request, the guardrail evaluates all user and AI messages as by default,
and a warning is logged. The same default applies when the request contains no user message.

## Additional Model Request Fields

The field `additionalModelRequestFields` in the `BedrockChatRequestParameters` is a `Map<String, Object>`.
As explained [here](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#bedrock-runtime_Converse-request-additionalModelRequestFields)
it allows you to add inference parameters for a specific model that is not covered by common `InferenceConfiguration`.


## Thinking / Reasoning

To enable Claude thinking process, call `enableReasoning` on `BedrockChatRequestParameters` and set it via
`defaultRequestParameters` when building the model:
```java
BedrockChatRequestParameters parameters = BedrockChatRequestParameters.builder()
        .enableReasoning(1024) // token budget
        .build();

ChatModel model = BedrockChatModel.builder()
        .modelId("us.anthropic.claude-sonnet-4-20250514-v1:0")
        .defaultRequestParameters(parameters)
        .returnThinking(true)
        .sendThinking(true)
        .build();
```

The following parameters also control thinking behaviour:
- `returnThinking`: controls whether to return thinking (if available) inside `AiMessage.thinking()`
and whether to invoke `StreamingChatResponseHandler.onPartialThinking()` and `TokenStream.onPartialThinking()`
callbacks when using `BedrockStreamingChatModel`.
Disabled by default. If enabled, tinking signatures will also be stored and returned inside the `AiMessage.attributes()`.
- `sendThinking`: controls whether to send thinking and signatures stored in `AiMessage` to the LLM in follow-up requests.
Enabled by default.

## Prompt Caching

AWS Bedrock supports prompt caching to improve performance and reduce costs when making repeated API calls with similar prompts. This feature can reduce latency by up to 85% and costs by up to 90% for cached content.

### How It Works

Prompt caching allows you to mark specific points in your conversation to be cached. When you make subsequent API calls with the same cached content, Bedrock can reuse the cached portion, significantly reducing processing time and costs. The cache has a 5-minute TTL (Time To Live) which resets on each cache hit.

### Supported Models

Prompt caching is supported on the following models:
- Claude Opus 4.5
- Claude Opus 4.1
- Claude Opus 4
- Claude Sonnet 4.5
- Claude Haiku 4.5
- Claude Sonnet 4
- Claude 3.7 Sonnet
- Claude 3.5 Sonnet
- Claude 3.5 Haiku 
- Amazon Nova models

### Configuration

To enable prompt caching, use the `promptCaching()` method in `BedrockChatRequestParameters`:

```java
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.bedrock.BedrockCachePointPlacement;

BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
        .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
        .temperature(0.7)
        .maxOutputTokens(500)
        .build();

ChatModel model = BedrockChatModel.builder()
        .modelId("us.amazon.nova-micro-v1:0")
        .region(Region.US_EAST_1)
        .defaultRequestParameters(params)
        .build();
```

### Cache Point Placement Options

The `BedrockCachePointPlacement` enum provides three options for where to place the cache point in your conversation:

- **`AFTER_SYSTEM`**: Places the cache point after the system message. This is ideal when you have a consistent system prompt that you want to reuse across multiple conversations.
- **`AFTER_USER_MESSAGE`**: Places the cache point after the user message. Useful when you have a standard user prompt or context that remains the same.
- **`AFTER_TOOLS`**: Places the cache point after tool definitions. This is beneficial when you have a consistent set of tools that you want to cache.

### Examples

#### Basic Usage with System Message Caching

```java
// Configure prompt caching to cache after system message
BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
        .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
        .build();

ChatModel model = BedrockChatModel.builder()
        .modelId("us.anthropic.claude-sonnet-4-6")
        .defaultRequestParameters(params)
        .build();

// First request - establishes the cache
ChatRequest request1 = ChatRequest.builder()
        .messages(Arrays.asList(
                SystemMessage.from("You are a helpful coding assistant with expertise in Java."),
                UserMessage.from("What is dependency injection?")
        ))
        .build();

ChatResponse response1 = model.chat(request1);

// Second request - benefits from cached system message
ChatRequest request2 = ChatRequest.builder()
        .messages(Arrays.asList(
                SystemMessage.from("You are a helpful coding assistant with expertise in Java."),
                UserMessage.from("What is the singleton pattern?")
        ))
        .build();

ChatResponse response2 = model.chat(request2); // Faster response due to caching
```

#### Combining with Other Features

Prompt caching can be combined with other Bedrock features like reasoning:

```java
BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
        .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
        .enableReasoning(1000)  // Enable reasoning with 1000 token budget
        .temperature(0.3)
        .maxOutputTokens(2000)
        .build();

ChatModel model = BedrockChatModel.builder()
        .modelId("us.anthropic.claude-sonnet-4-6")
        .defaultRequestParameters(params)
        .build();
```

### Best Practices

1. **Cache Stable Content**: Use caching for content that doesn't change frequently, such as system prompts, tool definitions, or common context.
2. **Choose Appropriate Placement**: 
   - Use `AFTER_SYSTEM` when your system prompt is consistent across conversations
   - Use `AFTER_TOOLS` when you have a stable set of tool definitions
   - Use `AFTER_USER_MESSAGE` for scenarios with repeated user contexts
3. **Monitor Cache Hits**: The 5-minute TTL resets on each cache hit, so frequent requests with the same cached content will maintain the cache.
4. **Cost Optimization**: Caching is particularly beneficial for long system prompts or tool definitions that are used repeatedly.

### Additional Resources

- [AWS Bedrock Prompt Caching Documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html)
