---
sidebar_position: 4
---

# OpenAI Official SDK

:::note

This is the documentation for the `OpenAI Official SDK` integration, that uses the [official OpenAI Java SDK](https://github.com/openai/openai-java).

LangChain4j provides 3 different integrations with OpenAI for generating images, and this is #2 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).
- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.

:::

## Use cases for this integration

This integration uses the [OpenAI Java SDK GitHub Repository](https://github.com/openai/openai-java), and will work for all OpenAI models which can be provided by:

- OpenAI
- Azure OpenAI

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
import com.openai.models.images.ImageModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialImageModel;

// ....

ImageModel model = OpenAiOfficialImageModel.builder()
        .baseUrl(System.getenv("OPENAI_BASE_URL"))
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(ImageModel.DALL_E_3)
        .build();
```

### Specific configurations for Azure OpenAI and GitHub Models.

Similar to configuring the [OpenAI Official Chat Model](/integrations/language-models/open-ai-official), you can configure the `OpenAiOfficialImageModel` with
Azure OpenAI and GitHub Models, using the `isAzure()` and `isGitHubModels()` methods.

#### Azure OpenAI

```java
ImageModel model = OpenAiOfficialImageModel.builder()
        .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .modelName(ImageModel.DALL_E_3)
        .isAzure(true) // Not necessary if the base URL ends with `openai.azure.com`
        .build();
```

You can also use "passwordless" authentication, as described in the [OpenAI Official Chat Model](/integrations/language-models/open-ai-official) documentation.

#### GitHub Models

```java
ImageModel model = OpenAiOfficialImageModel.builder()
        .modelName(ImageModel.DALL_E_3)
        .isGitHubModels(true)
        .build();
```

## Using the models

Once the model is configured, you can use it to generate images:

```java
String imageUrl = imageModel
        .generate("A coffee mug in Paris, France")
        .content()
        .url()
        .toString();
```
