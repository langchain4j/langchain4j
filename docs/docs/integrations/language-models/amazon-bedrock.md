---
sidebar_position: 1
---

# Amazon Bedrock

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-bedrock</artifactId>
    <version>1.2.0</version>
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
