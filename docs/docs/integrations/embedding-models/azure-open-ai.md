---
sidebar_position: 3
---

# Azure OpenAI

Azure OpenAI has a few embedding models (`text-embedding-3-small`, `text-embedding-ada-002`, etc.) that can be used to transforms text or images into a dimensional vector space.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai</artifactId>
    <version>0.32.0</version>
</dependency>
```

## Creating AzureOpenAiEmbeddingModel

### Plain Java
```java
EmbeddingModel model = AzureOpenAiEmbeddingModel.builder()
        .endpoint("https://langchain4j.openai.azure.com/")
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("text-embedding-3-small")
        .build();
```

### Spring Boot

Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.embedding-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.embedding-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.embedding-model.deployment-name=text-embedding-3-small
```

## APIs

- `AzureOpenAiEmbeddingModel`

## Examples

- [AzureOpenAiEmbeddingModelExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/java/AzureOpenAiEmbeddingModelExamples.java)
