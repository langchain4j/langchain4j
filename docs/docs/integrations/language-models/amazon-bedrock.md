---
sidebar_position: 1
---

# Amazon Bedrock

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-bedrock</artifactId>
    <version>0.34.0</version>
</dependency>
```

## AWS credentials
In order to use Amazon Bedrock models, you need to configure AWS credentials.
One of the options is to set the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables.
More information can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/security-iam.html).

## AI21 Models
- `BedrockAI21LabsChatModel`

## Anthropic Models
- `BedrockAnthropicMessageChatModel`: supports new Messages API
- `BedrockAnthropicCompletionChatModel`: supports old Text Completions API
- `BedrockAnthropicStreamingChatModel`

Example:
```java
ChatLanguageModel model = BedrockAnthropicMessageChatModel.builder()
.model("anthropic.claude-3-sonnet-20240229-v1:0")
.build();
```

## Cohere Models
- `BedrockCohereChatModel`

## Meta Llama Models
- `BedrockLlamaChatModel`

## Mistral Models
- `BedrockMistralAiChatModel`

## Titan Models
- `BedrockTitanChatModel`
- `BedrockTitanEmbeddingModel`


## Examples

- [BedrockChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/BedrockChatModelExample.java)
- [BedrockStreamingChatModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/bedrock-examples/src/main/java/BedrockStreamingChatModelExample.java)
