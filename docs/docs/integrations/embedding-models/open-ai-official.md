---
sidebar_position: 16
---

# OpenAI Official SDK

:::note

This is the documentation for the `OpenAI Official SDK` integration, that uses the [official OpenAI Java SDK](https://github.com/openai/openai-java).

LangChain4j provides 4 different integrations with OpenAI for using embedding models, and this is #2 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).
- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.
- [GitHub Models](/integrations/language-models/github-models) uses the Azure AI Inference API to access GitHub Models.

:::

## Use cases for this integration

This integration uses the [OpenAI Java SDK GitHub Repository](https://github.com/openai/openai-java), and will work for all OpenAI models which can be provided by:

- OpenAI
- Azure OpenAI
- GitHub Models

It will also work with models supporting the OpenAI API.

## OpenAI Documentation

- [OpenAI Java SDK GitHub Repository](https://github.com/openai/openai-java)
- [OpenAI API Documentation](https://platform.openai.com/docs/introduction)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-official</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

## Configuring the models

To use OpenAI models, you usually need an endpoint URL, an API key, and a model name. This depends on where the model is hosted, and this integration tries
to make it easier with some auto-configuration:

### Generic configuration

```java
import com.openai.models.embeddings.EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialEmbeddingModel;

// ....

EmbeddingModel model = OpenAiOfficialEmbeddingModel.builder()
        .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .modelName(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
        .build();
```

### Specific configurations for Azure OpenAI and GitHub Models.

Similar to configuring the [OpenAI Official Chat Model](/integrations/language-models/open-ai-official), you can configure the `OpenAiOfficialEmbeddingModel` with
Azure OpenAI and GitHub Models, using the `isAzure()` and `isGitHubModels()` methods.

#### Azure OpenAI

```java
EmbeddingModel model = OpenAiOfficialEmbeddingModel.builder()
        .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .modelName(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
        .isAzure(true) // Not necessary if the base URL ends with `openai.azure.com`
        .build();
```

You can also use "passwordless" authentication, as described in the [OpenAI Official Chat Model](/integrations/language-models/open-ai-official) documentation.

#### GitHub Models

```java
EmbeddingModel model = OpenAiOfficialEmbeddingModel.builder()
        .modelName(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
        .isGitHubModels(true)
        .build();
```

## Using the models

Once the model is configured, you can use it to create embeddings:

```java
Response<Embedding> response = model.embed("Please embed this sentence.");
```
