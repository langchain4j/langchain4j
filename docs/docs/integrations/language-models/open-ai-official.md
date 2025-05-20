---
sidebar_position: 16
---

# OpenAI Official SDK

:::note

This is the documentation for the `OpenAI Official SDK` integration, that uses the [official OpenAI Java SDK](https://github.com/openai/openai-java).

LangChain4j provides 4 different integrations with OpenAI for using chat models, and this is #2 :

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

It will also work with models supporting the OpenAI API, such as DeepSeek.

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

:::note
This configuration, as well as the next section about its usage, is for non-streaming mode (also known as "blocking" or "synchronous" mode).
Streaming mode is detailed 2 sections below: it allows for real-time chat with the model, but is more complex to use.
:::

To use OpenAI models, you usually need an endpoint URL, an API key, and a model name. This depends on where the model is hosted, and this integration tries
to make it easier with some auto-configuration:

### Generic configuration

```java
import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;

// ....

ChatModel model = OpenAiOfficialChatModel.builder()
        .baseUrl(System.getenv("OPENAI_BASE_URL"))
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(ChatModel.GPT_4O_MINI)
        .build();
```

### OpenAI configuration

The OpenAI `baseUrl` (`https://api.openai.com/v1`) is the default, so you can omit it:

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(ChatModel.GPT_4O_MINI)
        .build();
```

### Azure OpenAI configuration

#### Generic configuration

For Azure OpenAI, setting a `baseUrl` is mandatory, and Azure OpenAI will be automatically detected if that URL ends with `openai.azure.com`:

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .modelName(ChatModel.GPT_4O_MINI)
        .build();
```

If you want to force the usage of Azure OpenAI, you can also use the `isAzure()` method:

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .isAzure(true)
        .modelName(ChatModel.GPT_4O_MINI)
        .build();
```

#### Passwordless authentication

You can authenticate to Azure OpenAI using "passwordless" authentication, which is more secure as you won't manage the API key.

To do so, you must first configure your Azure OpenAI instance to support managed identity, and then give access to this application, for example:

```bash
# Enable system managed identity on the Azure OpenAI instance
az cognitiveservices account identity assign \
    --name <your-openai-instance-name> \
    --resource-group <your-resource-group>

# Get your logged-in identity
az ad signed-in-user show \
    --query id -o tsv
    
# Give access to the Azure OpenAI instance
az role assignment create \
    --role "Cognitive Services OpenAI User" \
    --assignee <your-logged-identity-from-the-previous-command> \
    --scope "/subscriptions/<your-subscription-id>/resourceGroups/<your-resource-group>"
```

Then, you need to add the `azure-identity` dependency to your Maven `pom.xml`:

```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
</dependency>
```

When no API key is configured, LangChain4j will then automatically use passwordless authentication with Azure OpenAI.

### GitHub Models configuration

For GitHub Models, you can use the default `baseUrl` (`https://models.inference.ai.azure.com`):

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .baseUrl("https://models.inference.ai.azure.com")
        .apiKey(System.getenv("GITHUB_TOKEN"))
        .modelName(ChatModel.GPT_4O_MINI)
        .build();
```

Or you can use the `isGitHubModels()` method to force the usage of GitHub Models, which will automatically set the `baseUrl`:

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .apiKey(System.getenv("GITHUB_TOKEN"))
        .modelName(ChatModel.GPT_4O_MINI)
        .isGitHubModels(true)
        .build();
```

As GitHub Models are usually configured using the `GITHUB_TOKEN` environment variable, which is automatically filled up when using GitHub Actions or GitHub Codespaces, it will be automatically detected:

```java
ChatModel model = OpenAiOfficialChatModel.builder()
        .modelName(ChatModel.GPT_4O_MINI)
        .isGitHubModels(true)
        .build();
```

This last configuration is easier to use, and more secure as the `GITHUB_TOKEN` environment variable is not exposed in the code or in the GitHub logs.

## Using the models

In the previous section, an `OpenAiOfficialChatModel` object was created, which implements the `ChatModel` interface.

It can be either used by an [AI Service](https://docs.langchain4j.dev/tutorials/spring-boot-integration/#langchain4j-spring-boot-starter) or used directly in a Java application.

In this example, it is autowired as a Spring Bean:

```java
@RestController
class ChatModelController {

    ChatModel chatModel;

    ChatModelController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping("/model")
    public String model(@RequestParam(value = "message", defaultValue = "Hello") String message) {
        return chatModel.chat(message);
    }
}
```

## Structured Outputs
The [Structured Outputs](https://openai.com/index/introducing-structured-outputs-in-the-api/) feature is supported
for both [tools](/tutorials/tools) and [response format](/tutorials/ai-services#json-mode).

See more info on Structured Outputs [here](/tutorials/structured-outputs).

### Structured Outputs for Tools
To enable Structured Outputs feature for tools, set `.strictTools(true)` when building the model:

```java
OpenAiOfficialChatModel.builder()
        // ...
        .strictTools(true)
        .build();
```

Please note that this will automatically make all tool parameters mandatory (`required` in json schema)
and set `additionalProperties=false` for each `object` in json schema. This is due to the current OpenAI limitations.

### Structured Outputs for Response Format
To enable the Structured Outputs feature for response formatting when using AI Services,
set `supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))` and `.strictJsonSchema(true)` when building the model:

```java
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

// ...

OpenAiChatModel.builder()
        // ...
        .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
        .strictJsonSchema(true)
        .build();
```

In this case AI Service will automatically generate a JSON schema from the given POJO and pass it to the LLM.

## Configuring the models for streaming

:::note
In the two sections above, we detailed how to configure the models for non-streaming mode (also known as "blocking" or "synchronous" mode).
This section is for streaming mode, which allows for real-time chat with the model, but is more complex to use.
:::

This is similar to the non-streaming mode, but you need to use the `OpenAiOfficialStreamingChatModel` class instead of `OpenAiOfficialChatModel`:

```java
StreamingChatModel model = OpenAiOfficialStreamingChatModel.builder()
        .baseUrl(System.getenv("OPENAI_BASE_URL"))
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName(ChatModel.GPT_4O_MINI)
        .build();
```

You can also use the specific `isAzure()` and `isGitHubModels()` methods to force the usage of Azure OpenAI or GitHub Models, as detailed in the non-streaming configuration section.

