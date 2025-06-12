---
sidebar_position: 1
---

# Amazon Bedrock

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-bedrock</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

## AWS credentials
In order to use Amazon Bedrock models, you need to configure AWS credentials.
One of the options is to set the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables.
More information can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/security-iam.html).

## Difference Between InvokeAPI and ConverseAPI
Amazon Bedrock offers two primary model invocation API operations for inference:
- [Converse](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html) – Amazon recommend using the Converse API as it provides consistent API, that works with all Amazon Bedrock models that support messages.
- [InvokeModel](https://docs.aws.amazon.com/bedrock/latest/userguide/inference-invoke.html) – Originally aimed at single calls to obtain a response to a single prompt.

## ChatModel using ConverseAPI
Guardrails is not supported by the current implementation.

Supported models and their features can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference-supported-models-features.html).

Models ids can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html).

### Configuration
```java
ChatModel model = BedrockChatModel.builder()
        .modelId("us.amazon.nova-lite-v1:0")
        .region(...)
        .maxRetries(...)
        .timeout(...)
        .logRequests(...)
        .logResponses(...)
        .listeners(...)
        .defaultRequestParameters(BedrockChatRequestParameters.builder()
                .topP(...)
                .temperature(...)
                .maxOutputTokens(...)
                .stopSequences(...)
                .toolSpecifications(...)
                .additionalModelRequestFields(...)
                .build())
        .build();
```

The field `additionalModelRequestFields` is a `Map<String, Object>`. As explained [here](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#bedrock-runtime_Converse-request-additionalModelRequestFields) 
it allows to add inference parameters for a specific model that is not covered by common inferenceConfig. 
BedrockChatRequestParameters has a convenience method to enable Claude 3.7 thinking process through adding inference
parameters in additionalModelRequestFields.

### Examples

- [BedrockChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/converse/BedrockChatModelExample.java)

## StreamingChatModel using ConverseAPI
Guardrails is not supported by the current implementation.

Supported models and their features can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference-supported-models-features.html).

Models ids can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html).

### Configuration
```java
StreamingChatModel model = BedrockStreamingChatModel.builder()
        .modelId("us.amazon.nova-lite-v1:0")
        .region(...)
        .maxRetries(...)
        .timeout(...)
        .logRequests(...)
        .logResponses(...)
        .listeners(...)
        .defaultRequestParameters(BedrockChatRequestParameters.builder()
                .topP(...)
                .temperature(...)
                .maxOutputTokens(...)
                .stopSequences(...)
                .toolSpecifications(...)
                .additionalModelRequestFields(...)
                .build())
        .build();
```

The field `additionalModelRequestFields` is a `Map<String, Object>`. As explained [here](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#bedrock-runtime_Converse-request-additionalModelRequestFields)
it allows to add inference parameters for a specific model that is not covered by common inferenceConfig.
BedrockChatRequestParameters has a convenience method to enable Claude 3.7 thinking process through adding inference
parameters in additionalModelRequestFields.

### Examples

- [BedrockStreamingChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/converse/BedrockStreamingChatModelExample.java)

## ChatModel using InvokeAPI

### AI21 Models
- `BedrockAI21LabsChatModel` (deprecated, please use `BedrockChatModel`)

### Anthropic Models
- `BedrockAnthropicMessageChatModel`: (deprecated, please use `BedrockChatModel`) supports new Messages API
- `BedrockAnthropicCompletionChatModel`: (deprecated, please use `BedrockChatModel`) supports old Text Completions API
- `BedrockAnthropicStreamingChatModel`

Example:
```java
ChatModel model = BedrockAnthropicMessageChatModel.builder()
.model("anthropic.claude-3-sonnet-20240229-v1:0")
.build();
```

### Cohere Models
- `BedrockCohereChatModel` (deprecated, please use `BedrockChatModel`)

### Meta Llama Models
- `BedrockLlamaChatModel` (deprecated, please use `BedrockChatModel`)

### Mistral Models
- `BedrockMistralAiChatModel` (deprecated, please use `BedrockChatModel`)

### Titan Models
- `BedrockTitanChatModel` (deprecated, please use `BedrockChatModel`)
- `BedrockTitanEmbeddingModel`

### Examples

- [BedrockChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/converse/BedrockChatModelExample.java)
- [BedrockStreamingChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/invoke/BedrockStreamingChatModelExample.java)
