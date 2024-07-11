---
sidebar_position: 1
---

# Azure OpenAI Dall·E

Azure OpenAI has a few image models (`dall-e-3`, etc.) that can be used for various image processing tasks.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai</artifactId>
    <version>0.32.0</version>
</dependency>
```

## Creating AzureOpenAiImageModel

### Plain Java
```java
ImageModel model = AzureOpenAiImageModel.builder()
        .endpoint("https://langchain4j.openai.azure.com/")
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("dall-e-3")
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.image-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.image-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.image-model.deployment-name=dall-e-3
```

## APIs

- `AzureOpenAiImageModel`

## Examples

- [AzureOpenAIDallEExample](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/java/AzureOpenAIDallEExample.java)
