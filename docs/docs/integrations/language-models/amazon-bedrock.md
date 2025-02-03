---
sidebar_position: 1
---

# Amazon Bedrock

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-bedrock</artifactId>
    <version>1.0.0-alpha1</version>
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

## ChatLanguageModel using ConverseAPI
Guardrails and streaming are not supported by the current implementation.

Supported models and their features can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference-supported-models-features.html).

Models ids can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html).

### Configuration
```java
ChatLanguageModel model = BedrockChatModel.builder()
        .modelId("us.amazon.nova-lite-v1:0")
        .maxRetries(...)
        .timeout(...)
        .logRequests(...)
        .logResponses(...)
        .defaultRequestParameters(ChatRequestParameters.builder()
                .topP(...)
                .temperature(...)
                .maxOutputTokens(...)
                .stopSequences(...)
                .toolSpecifications(...)
                .build())
        .build();
```
### Examples

- [BedrockChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/converse/BedrockChatModelExample.java)

## ChatLanguageModel using InvokeAPI

### AI21 Models
- `BedrockAI21LabsChatModel`

### Anthropic Models
- `BedrockAnthropicMessageChatModel`: supports new Messages API
- `BedrockAnthropicCompletionChatModel`: supports old Text Completions API
- `BedrockAnthropicStreamingChatModel`

Example:
```java
ChatLanguageModel model = BedrockAnthropicMessageChatModel.builder()
.model("anthropic.claude-3-sonnet-20240229-v1:0")
.build();
```

### Cohere Models
- `BedrockCohereChatModel`

### Meta Llama Models
- `BedrockLlamaChatModel`

### Mistral Models
- `BedrockMistralAiChatModel`

### Titan Models
- `BedrockTitanChatModel`
- `BedrockTitanEmbeddingModel`

### Examples

- [BedrockChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/invoke/BedrockChatModelExample.java)
- [BedrockStreamingChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/invoke/BedrockStreamingChatModelExample.java)
