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

## BedrockChatModel
:::note
Guardrails is not supported by the current implementation.
:::

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
`BedrockChatRequestParameters` has a convenience method to enable Claude 3.7 thinking process through adding inference
parameters in `additionalModelRequestFields`.

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
        .modelId("us.amazon.nova-lite-v1:0")
        .region(...)
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
`BedrockChatRequestParameters` has a convenience method to enable Claude 3.7 thinking process through adding inference
parameters in `additionalModelRequestFields`.

### Examples

- [BedrockStreamingChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/converse/BedrockStreamingChatModelExample.java)
