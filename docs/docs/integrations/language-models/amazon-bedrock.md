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

## Cohere Embedding Models
Support is provided for Bedrock Cohere embedding models, enabling the use of the following versions:

- **`cohere.embed-english-v3`**
- **`cohere.embed-multilingual-v3`**

These models are ideal for generating high-quality text embeddings for English and multilingual text processing tasks.

### Implementation Example

Below is an example of how to configure and use a Bedrock embedding model:

```
BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel
        .builder()
        .region(Region.US_EAST_1)
        .maxRetries(1)
        .model("cohere.embed-multilingual-v3")
        .inputType(BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY)
        .truncation(BedrockCohereEmbeddingModel.Truncate.NONE)
        .build();
```
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
