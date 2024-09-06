---
sidebar_position: 13
---

# OpenAI

- https://platform.openai.com/docs/guides/embeddings
- https://platform.openai.com/docs/api-reference/embeddings


## Maven Dependency

### Plain Java
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.34.0</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>0.34.0</version>
</dependency>
```


## Creating `OpenAiEmbeddingModel`

### Plain Java
```java
EmbeddingModel model = OpenAiEmbeddingModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        ...
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.open-ai.embedding-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.embedding-model.base-url=...
langchain4j.open-ai.embedding-model.custom-headers=...
langchain4j.open-ai.embedding-model.dimensions=...
langchain4j.open-ai.embedding-model.log-requests=...
langchain4j.open-ai.embedding-model.log-responses=...
langchain4j.open-ai.embedding-model.max-retries=...
langchain4j.open-ai.embedding-model.model-name=...
langchain4j.open-ai.embedding-model.organization-id=...
langchain4j.open-ai.embedding-model.proxy.host=...
langchain4j.open-ai.embedding-model.proxy.port=...
langchain4j.open-ai.embedding-model.proxy.type=...
langchain4j.open-ai.embedding-model.timeout=...
langchain4j.open-ai.embedding-model.user=...
```


## Examples

- [OpenAiEmbeddingModelExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/open-ai-examples/src/main/java/OpenAiEmbeddingModelExamples.java)
