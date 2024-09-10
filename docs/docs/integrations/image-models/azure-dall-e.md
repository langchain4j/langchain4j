---
sidebar_position: 1
---

# Azure OpenAI Dall·E

Azure OpenAI provides a few image models (`dall-e-3`, etc.)
that can be used for various image processing tasks.


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


## Creating `AzureOpenAiImageModel`

### Plain Java
```java
ImageModel model = AzureOpenAiImageModel.builder()
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("dall-e-3")
        .endpoint("https://langchain4j.openai.azure.com/")
        ...
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.image-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.image-model.deployment-name=dall-e-3
langchain4j.azure-open-ai.image-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.image-model.log-requests-and-responses=...
langchain4j.azure-open-ai.image-model.max-retries=...
langchain4j.azure-open-ai.image-model.quality=...
langchain4j.azure-open-ai.image-model.response-format=...
langchain4j.azure-open-ai.image-model.size=...
langchain4j.azure-open-ai.image-model.style=...
langchain4j.azure-open-ai.image-model.timeout=...
langchain4j.azure-open-ai.image-model.user=...
```


## Examples

- [AzureOpenAIDallEExample](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/java/AzureOpenAIDallEExample.java)
