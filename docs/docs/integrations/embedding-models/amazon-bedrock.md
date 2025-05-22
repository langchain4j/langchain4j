---
sidebar_position: 2
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
In order to use Amazon Bedrock embeddings, you need to configure AWS credentials.
One of the options is to set the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables.
More information can be found [here](https://docs.aws.amazon.com/bedrock/latest/userguide/security-iam.html).

## Cohere Models
- `BedrockCohereEmbeddingModel`

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
        .model("cohere.embed-multilingual-v3")
        .inputType(BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY)
        .truncation(BedrockCohereEmbeddingModel.Truncate.NONE)
        .build();
```

## APIs

- `BedrockTitanEmbeddingModel`
- `BedrockCohereEmbeddingModel`

## Examples

- [BedrockEmbeddingIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-bedrock/src/test/java/dev/langchain4j/model/bedrock/BedrockEmbeddingIT.java)
