---
sidebar_position: 1
---

# Amazon Bedrock

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-bedrock</artifactId>
    <version>1.6.0</version>
</dependency>
```

## AWS credentials
In order to use Amazon Bedrock models, you need to configure AWS credentials.
One of the options is to set the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables. More information can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/security-iam.html). Alternatively, set the `AWS_BEARER_TOKEN_BEDROCK` environment variable locally for API Key authentication. For additional API key details, refer to [docs](https://docs.aws.amazon.com/bedrock/latest/userguide/api-keys.html).

## BedrockChatModel
:::note
Guardrails is not supported by the current implementation.
:::

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

:::note
Guardrails is not supported by the current implementation.
:::

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


## Additional Model Request Fields

The field `additionalModelRequestFields` in the `BedrockChatRequestParameters` is a `Map<String, Object>`.
As explained [here](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#bedrock-runtime_Converse-request-additionalModelRequestFields)
it allows to add inference parameters for a specific model that is not covered by common `InferenceConfiguration`.


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
- Claude 3.5 Sonnet
- Claude 3.5 Haiku  
- Claude 3.7 Sonnet
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
        .modelId("us.anthropic.claude-3-7-sonnet-20250219-v1:0")
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
        .modelId("us.anthropic.claude-3-7-sonnet-20250219-v1:0")
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
