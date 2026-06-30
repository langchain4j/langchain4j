---
sidebar_position: 2
---

# OpenAI Image Models

:::note

This is the documentation for the `OpenAI` integration, that uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).


LangChain4j provides 3 different integrations with OpenAI for generating images, and this is #1 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).

- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.

:::

## Maven Dependency

### Plain Java
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.17.0</version>
</dependency>
```

### Spring Boot
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.17.0-beta27</version>
</dependency>
```


:::note

OpenAI has deprecated the DALL·E 2 and DALL·E 3 models in favor of the new GPT image models
(`gpt-image-1`, `gpt-image-1-mini`, `gpt-image-1.5`, `gpt-image-2`, `chatgpt-image-latest`).
The DALL·E-specific parameters `style` and `response-format` are no longer supported by the API and
have been removed. New parameters `background`, `output-format`, `output-compression`, and `moderation`
are available instead.

:::

## Creating `OpenAiImageModel`

### Plain Java
```java
ImageModel model = OpenAiImageModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-image-1")
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
# Mandatory properties:
langchain4j.open-ai.image-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.image-model.model-name=gpt-image-1

# Optional properties:
langchain4j.open-ai.image-model.background=...
langchain4j.open-ai.image-model.base-url=...
langchain4j.open-ai.image-model.custom-headers=...
langchain4j.open-ai.image-model.log-requests=...
langchain4j.open-ai.image-model.log-responses=...
langchain4j.open-ai.image-model.max-retries=...
langchain4j.open-ai.image-model.moderation=...
langchain4j.open-ai.image-model.organization-id=...
langchain4j.open-ai.image-model.output-compression=...
langchain4j.open-ai.image-model.output-format=...
langchain4j.open-ai.image-model.project-id=...
langchain4j.open-ai.image-model.quality=...
langchain4j.open-ai.image-model.size=...
langchain4j.open-ai.image-model.timeout=...
langchain4j.open-ai.image-model.user=...
```

## Examples

- [OpenAiImageModelExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/open-ai-examples/src/main/java/OpenAiImageModelExamples.java)
