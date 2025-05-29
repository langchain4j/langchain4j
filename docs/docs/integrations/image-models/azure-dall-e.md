---
sidebar_position: 1
---

# Azure OpenAI Dall·E

:::note

This is the documentation for the `Azure OpenAI` integration, that uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.

LangChain4j provides 3 different integrations with OpenAI for generating images, and this is #3 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).

- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.

:::

Azure OpenAI provides a few image models (`dall-e-3`, etc.)
that can be used for various image processing tasks.

## Maven Dependency

### Plain Java
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai-spring-boot-starter</artifactId>
    <version>1.0.1-beta6</version>
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
langchain4j.azure-open-ai.image-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.image-model.service-version=...
langchain4j.azure-open-ai.image-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.image-model.deployment-name=dall-e-3
langchain4j.azure-open-ai.image-model.quality=...
langchain4j.azure-open-ai.image-model.size=...
langchain4j.azure-open-ai.image-model.user=...
langchain4j.azure-open-ai.image-model.style=...
langchain4j.azure-open-ai.image-model.response-format=...
langchain4j.azure-open-ai.image-model.timeout=...
langchain4j.azure-open-ai.image-model.max-retries=...
langchain4j.azure-open-ai.image-model.log-requests-and-responses=...
langchain4j.azure-open-ai.image-model.user-agent-suffix=...
langchain4j.azure-open-ai.image-model.customHeaders=...
```


## Examples

- [AzureOpenAIDallEExample](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/java/AzureOpenAIDallEExample.java)
