---
sidebar_position: 2
---

# OpenAI Dall·E


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


## Creating `OpenAiImageModel`

### Plain Java
```java
ImageModel model = OpenAiImageModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        ...
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.open-ai.image-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.image-model.base-url=...
langchain4j.open-ai.image-model.custom-headers=...
langchain4j.open-ai.image-model.log-requests=...
langchain4j.open-ai.image-model.log-responses=...
langchain4j.open-ai.image-model.max-retries=...
langchain4j.open-ai.image-model.model-name=...
langchain4j.open-ai.image-model.organization-id=...
langchain4j.open-ai.image-model.persist-to=...
langchain4j.open-ai.image-model.proxy.host=...
langchain4j.open-ai.image-model.proxy.port=...
langchain4j.open-ai.image-model.proxy.type=...
langchain4j.open-ai.image-model.quality=...
langchain4j.open-ai.image-model.response-format=...
langchain4j.open-ai.image-model.size=...
langchain4j.open-ai.image-model.style=...
langchain4j.open-ai.image-model.timeout=...
langchain4j.open-ai.image-model.user=...
langchain4j.open-ai.image-model.with-persisting=...
```


## Examples

- [OpenAiImageModelExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/open-ai-examples/src/main/java/OpenAiImageModelExamples.java)
