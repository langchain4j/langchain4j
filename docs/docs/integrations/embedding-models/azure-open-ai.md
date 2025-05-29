---
sidebar_position: 3
---

# Azure OpenAI

:::note

This is the documentation for the `Azure OpenAI` integration, that uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.

LangChain4j provides 4 different integrations with OpenAI for using embedding models, and this is #3 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).
- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.
- [GitHub Models](/integrations/language-models/github-models) uses the Azure AI Inference API to access GitHub Models.

:::

Azure OpenAI provides a few embedding models (`text-embedding-3-small`, `text-embedding-ada-002`, etc.)
that can be used to transforms text or images into a dimensional vector space.

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
langchain4j.azure-open-ai.embedding-model.endpoint=https://langchain4j.openai.azure.com/
langchain4j.azure-open-ai.embedding-model.service-version=...
langchain4j.azure-open-ai.embedding-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.embedding-model.deployment-name=text-embedding-3-small
langchain4j.azure-open-ai.embedding-model.timeout=...
langchain4j.azure-open-ai.embedding-model.max-retries=...
langchain4j.azure-open-ai.embedding-model.log-requests-and-responses=...
langchain4j.azure-open-ai.embedding-model.user-agent-suffix=...
langchain4j.azure-open-ai.embedding-model.dimensions=...
langchain4j.azure-open-ai.embedding-model.customHeaders=...
```


## Examples

- [AzureOpenAiEmbeddingModelExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/java/AzureOpenAiEmbeddingModelExamples.java)
