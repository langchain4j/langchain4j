---
sidebar_position: 3
---

# Azure OpenAI

:::note

This is the documentation for the `Azure OpenAI` integration, that uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.

LangChain4j provides 4 different integrations with OpenAI for using chat models, and this is #3 :

- [OpenAI](/integrations/language-models/open-ai) uses a custom Java implementation of the OpenAI REST API, that works best with Quarkus (as it uses the Quarkus REST client) and Spring (as it uses Spring's RestClient).
- [OpenAI Official SDK](/integrations/language-models/open-ai-official) uses the official OpenAI Java SDK.
- [Azure OpenAI](/integrations/language-models/azure-open-ai) uses the Azure SDK from Microsoft, and works best if you are using the Microsoft Java stack, including advanced Azure authentication mechanisms.
- [GitHub Models](/integrations/language-models/github-models) uses the Azure AI Inference API to access GitHub Models.

:::

Azure OpenAI provides language models from OpenAI (`gpt-4`, `gpt-4o`, etc.) hosted on Azure, using the [Azure OpenAI Java SDK](https://learn.microsoft.com/en-us/java/api/overview/azure/ai-openai-readme).

## Azure OpenAI Documentation

- [Azure OpenAI Documentation](https://learn.microsoft.com/en-us/azure/ai-services/openai/)

## Maven Dependencies

### Plain Java

The `langchain4j-azure-open-ai` library is availlable on Maven Central.

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

### Spring Boot

A Spring Boot starter is available to configure the `langchain4j-azure-open-ai` library more easily.

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-open-ai-spring-boot-starter</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

:::note
Before using any of the Azure OpenAI models, you need to [deploy](https://learn.microsoft.com/en-us/azure/ai-services/openai/how-to/create-resource?pivots=web-portal) them.
:::

## Creating `AzureOpenAiChatModel` with an API Key

### Plain Java

```java
ChatModel model = AzureOpenAiChatModel.builder()
        .endpoint(System.getenv("AZURE_OPENAI_URL"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("gpt-4o")
        ...
        .build();
```

This will create an instance of `AzureOpenAiChatModel` with the specified endpoint, API key and depoyment name.
Other parameters can be customized by providing values in the builder.

### Spring Boot

Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.chat-model.endpoint=${AZURE_OPENAI_URL}
langchain4j.azure-open-ai.chat-model.service-version=...
langchain4j.azure-open-ai.chat-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.chat-model.non-azure-api-key=${OPENAI_API_KEY}
langchain4j.azure-open-ai.chat-model.deployment-name=gpt-4o
langchain4j.azure-open-ai.chat-model.max-tokens=...
langchain4j.azure-open-ai.chat-model.temperature=...
langchain4j.azure-open-ai.chat-model.top-p=
langchain4j.azure-open-ai.chat-model.logit-bias=...
langchain4j.azure-open-ai.chat-model.user=
langchain4j.azure-open-ai.chat-model.stop=...
langchain4j.azure-open-ai.chat-model.presence-penalty=...
langchain4j.azure-open-ai.chat-model.frequency-penalty=...
langchain4j.azure-open-ai.chat-model.seed=...
langchain4j.azure-open-ai.chat-model.strict-json-schema=...
langchain4j.azure-open-ai.chat-model.timeout=...
langchain4j.azure-open-ai.chat-model.max-retries=...
langchain4j.azure-open-ai.chat-model.log-requests-and-responses=...
langchain4j.azure-open-ai.chat-model.user-agent-suffix=
langchain4j.azure-open-ai.chat-model.custom-headers=...
```
See the description of some of the parameters above [here](https://learn.microsoft.com/en-us/azure/ai-services/openai/reference#completions).

This configuration will create an `AzureOpenAiChatModel` bean (with default model parameters),
which can be either used by an [AI Service](/tutorials/spring-boot-integration/#langchain4j-spring-boot-starter)
or autowired where needed, for example:

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

## Creating `AzureOpenAiChatModel` with Azure Credentials

API key can have a few security issues (can be committed, can be passed around, etc.).
If you want to improve security, it is recommended to use Azure Credentials instead.
For that, it is necessary to add the `azure-identity` dependency to the project.

```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <scope>compile</scope>
</dependency>
```

Then, you can create an `AzureOpenAiChatModel` using the [DefaultAzureCredentialBuilder](https://learn.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredentialbuilder?view=azure-java-stable) API:  

```java
ChatModel model = AzureOpenAiChatModel.builder()
        .deploymentName("gpt-4o")
        .endpoint(System.getenv("AZURE_OPENAI_URL"))
        .tokenCredential(new DefaultAzureCredentialBuilder().build())
        .build();
```

:::note
Notice that you need to deploy your model using Managed Identities. Check the [Azure CLI deployment script](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/script/deploy-azure-openai-security.sh) for more information.
:::

## Tools

Tools, also known as "Function Calling", is supported an allows the model to call methods within your Java code, including parallel tools calling.
"Function Calling" is described in the OpenAI documentation [here](https://platform.openai.com/docs/guides/function-calling).

:::note
There is a complete tutorial on how to use "Function Calling" in LangChain4j [here](/tutorials/tools/).
:::

Functions can be specified using the `ToolSpecification` class, or more easily using the `@Tool` annotation, like in the following example:

```java
class StockPriceService {

    private Logger log = Logger.getLogger(StockPriceService.class.getName());

    @Tool("Get the stock price of a company by its ticker")
    public double getStockPrice(@P("Company ticker") String ticker) {
        log.info("Getting stock price for " + ticker);
        if (Objects.equals(ticker, "MSFT")) {
            return 400.0;
        } else {
            return 0.0;
        }
    }
}
```

Then, you can use the `StockPriceService` in an AI `Assistant` like this:

```java

interface Assistant {
    String chat(String userMessage);
}

public class Demo {
    String functionCalling(Model model) {
        String question = "Is the current Microsoft stock higher than $450?";
        StockPriceService stockPriceService = new StockPriceService();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(stockPriceService)
                .build();

        String answer = assistant.chat(question);

        model.addAttribute("answer", answer);
        return "demo";
    }
}
```

## Structured Outputs

Structured Outputs ensure that a model's responses adhere to a JSON schema.

:::note
The documentation for using Structured Outputs in LangChain4j is available [here](/tutorials/structured-outputs), and in the section below you will find Azure OpenAI-specific information.
:::

The model needs to be configured with the `strictJsonSchema` parameter set to `true` in order to force the adherence to a JSON Schema:

```java
ChatModel model = AzureOpenAiChatModel.builder()
        .endpoint(System.getenv("AZURE_OPENAI_URL"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("gpt-4o")
        .strictJsonSchema(true)
        .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
        .build();
```

:::note
If `strictJsonSchema` is set to `false` and you provide a JSON Schema, the model will still try to generate a response that adheres to the schema, but it will not fail if the response does not adhere to the schema. One reason to do this is for better performance.
:::

You can then use this model either with the high level `Assistant` API or the low level `ChatModel` API, as detailed below.
When using it with the high level `Assistant` API, configure `supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))` to enable structured outputs with a JSON schema.

### Using the high level `Assistant` API

Like for Tools in the previous section, Structured Output can be automatically used with an AI `Assistant`:

```java

interface PersonAssistant {
    Person extractPerson(String message);
}

class Person {
    private final String name;
    private final List<String> favouriteColors;

    public Person(String name, List<String> favouriteColors) {
        this.name = name;
        this.favouriteColors = favouriteColors;
    }

    public String getName() {
        return name;
    }

    public List<String> getFavouriteColors() {
        return favouriteColors;
    }
}
```

This `Assistant` will make sure that the response adheres to a JSON schema corresponding in the `Person` class, like in the following example:

```java
String question = "Julien likes the colors blue, white and red";

PersonAssistant assistant = AiServices.builder(PersonAssistant.class)
                .chatModel(chatModel)
                .build();

Person person = assistant.extractPerson(question);
```

### Using the low level `ChatModel` API

This is a similar process to the high level API, but this time the JSON schema needs to be configured manually, as well as mapping the JSON response to a Java object.

Once the model is configured, the JSON Schema has to be specified in the `ChatRequest` object for each request.
The model will then generate a response that adheres to the schema, like in this example:

```java
ChatRequest chatRequest = ChatRequest.builder()
    .messages(UserMessage.from("Julien likes the colors blue, white and red"))
    .responseFormat(ResponseFormat.builder()
        .type(JSON)
        .jsonSchema(JsonSchema.builder()
            .name("Person")
            .rootElement(JsonObjectSchema.builder()
                .addStringProperty("name")
                .addProperty("favouriteColors", JsonArraySchema.builder()
                    .items(new JsonStringSchema())
                    .build())
                .required("name", "favouriteColors")
                .build())
            .build())
        .build())
    .build();

String answer = chatModel.chat(chatRequest).aiMessage().text();
```

In this example, the `answer` will be:
```json
{
  "name": "Julien",
  "favouriteColors": ["blue", "white", "red"]
}
```

This JSON response will then typically be deserialized into a Java object, using a library like Jackson.

## Creating `AzureOpenAiStreamingChatModel` to stream results

This implementation is similar to the `AzureOpenAiChatModel` above, but it streams the response token by token.

### Plain Java
```java
StreamingChatModel model = AzureOpenAiStreamingChatModel.builder()
        .endpoint(System.getenv("AZURE_OPENAI_URL"))
        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
        .deploymentName("gpt-4o")
        ...
        .build();
```

### Spring Boot
Add to the `application.properties`:
```properties
langchain4j.azure-open-ai.streaming-chat-model.endpoint=${AZURE_OPENAI_URL}
langchain4j.azure-open-ai.streaming-chat-model.service-version=...
langchain4j.azure-open-ai.streaming-chat-model.api-key=${AZURE_OPENAI_KEY}
langchain4j.azure-open-ai.streaming-chat-model.deployment-name=gpt-4o
langchain4j.azure-open-ai.streaming-chat-model.max-tokens=...
langchain4j.azure-open-ai.streaming-chat-model.temperature=...
langchain4j.azure-open-ai.streaming-chat-model.top-p=...
langchain4j.azure-open-ai.streaming-chat-model.logit-bias=...
langchain4j.azure-open-ai.streaming-chat-model.user=...
langchain4j.azure-open-ai.streaming-chat-model.stop=...
langchain4j.azure-open-ai.streaming-chat-model.presence-penalty=...
langchain4j.azure-open-ai.streaming-chat-model.frequency-penalty=...
langchain4j.azure-open-ai.streaming-chat-model.seed=...
langchain4j.azure-open-ai.streaming-chat-model.timeout=...
langchain4j.azure-open-ai.streaming-chat-model.max-retries=...
langchain4j.azure-open-ai.streaming-chat-model.log-requests-and-responses=...
langchain4j.azure-open-ai.streaming-chat-model.user-agent-suffix=...
langchain4j.azure-open-ai.streaming-chat-model.customHeaders=...
```

## Examples

- [Azure OpenAI Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/azure-open-ai-examples/src/main/java)
- [AzureOpenAiSecurityExamples](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/java/AzureOpenAiSecurityExamples.java) with its [Azure CLI deployment script](https://github.com/langchain4j/langchain4j-examples/blob/main/azure-open-ai-examples/src/main/script/deploy-azure-openai-security.sh)
