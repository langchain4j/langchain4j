---
sidebar_position: 3
---

# Azure OpenAI

Azure OpenAI provides a few embedding models (`text-embedding-3-small`, `text-embedding-ada-002`, etc.)
that can be used to transforms text or images into a dimensional vector space.

## Maven Dependency

### Plain Java
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai</artifactId>
    <version>0.34.0</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai-spring-boot-starter</artifactId>
    <version>0.34.0</version>
</dependency>
```


## Creating `AzureOpenAiEmbeddingModel`

### Plain Java
```java
EmbeddingModel model = AzureOpenAiEmbeddingModel.builder()
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("text-embedding-3-small")
        .endpoint("https://langchain4j.openai.azure.com/")
        ...
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.embedding-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.embedding-model.deployment-name=text-embedding-3-small
langchain4j.azure-open-ai.embedding-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.embedding-model.dimensions=...
langchain4j.azure-open-ai.embedding-model.log-requests-and-responses=...
langchain4j.azure-open-ai.embedding-model.max-retries=...
langchain4j.azure-open-ai.embedding-model.timeout=...
```


## Examples

- [AzureOpenAiEmbeddingModelExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/java/AzureOpenAiEmbeddingModelExamples.java)
