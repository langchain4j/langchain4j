---
sidebar_position: 22
---

# watsonx.ai

- [watsonx.ai API Reference](https://cloud.ibm.com/apidocs/watsonx-ai#chat-completions)
- [watsonx.ai Java SDK](https://github.com/IBM/watsonx-ai-java-sdk)
- [watsonx.ai Java SDK documentation](https://ibm.github.io/watsonx-ai-java-sdk/)

This integration is built on top of the **IBM watsonx.ai Java SDK**. Every model described below wraps one of its services. When you need details on a behavior that is not specific to LangChain4j - token caching, retries, HTTP client tuning - the [SDK documentation](https://ibm.github.io/watsonx-ai-java-sdk/) is the reference.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-watsonx</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

## Authentication

Watsonx.ai supports authentication via the `Authenticator` interface.

This allows you to use different authentication mechanisms depending on your deployment:

- **IBMCloudAuthenticator** – authenticates with **IBM Cloud** using an API key. This is the simplest approach and is used when you provide the `apiKey(...)` builder method.
- **CP4DAuthenticator** – authenticates with **Cloud Pak for Data** deployments.
- **Custom authenticators** – any implementation of the `Authenticator` interface can be used.

The `WatsonxChatModel`, `WatsonxStreamingChatModel`, and other service builders accept either a shortcut via `.apiKey(...)` or a full `Authenticator` instance via `.authenticator(...)`.

Token caching and renewal are handled transparently. A token is fetched on the first request, cached, and refreshed before it expires, so you never manage its lifecycle. Passing the same `Authenticator` instance to several models lets them share a single cached token. See the [SDK authentication guide](https://ibm.github.io/watsonx-ai-java-sdk/authentication) for the full list of authenticators and their parameters.

### Example
```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import com.ibm.watsonx.ai.core.auth.cp4d.CP4DAuthenticator;
import com.ibm.watsonx.ai.core.auth.cp4d.AuthMode;
import com.ibm.watsonx.ai.CloudRegion;

WatsonxChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key") // Simple IBM Cloud authentication
    .projectId("your-project-id")
    .modelName("ibm/granite-4-h-small")
    .build();

WatsonxChatModel.builder()
    .baseUrl("https://my-instance-url")
    .authenticator( // For Cloud Pak for Data deployments
        CP4DAuthenticator.builder()
            .baseUrl("https://my-instance-url")
            .username("username")
            .apiKey("api-key")
            .authMode(AuthMode.LEGACY)
            .build()
    )
    .projectId("my-project-id")
    .modelName("ibm/granite-4-h-small")
    .build();
```

### Custom HttpClient and SSL Configuration

#### Using a custom HttpClient

All services and authenticators support a custom `HttpClient` instance through the builder pattern. This is particularly useful for Cloud Pak for Data environments where you may need to configure custom TLS/SSL settings, proxy configuration, or other HTTP client properties.

```java
HttpClient httpClient = HttpClient.newBuilder()
    .sslContext(createCustomSSLContext())
    .executor(ExecutorProvider.ioExecutor())
    .build();

EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
    .baseUrl("https://my-instance-url")
    .modelName("ibm/granite-embedding-278m-multilingual")
    .projectId("project-id")
    .httpClient(httpClient) // Custom HttpClient
    .authenticator(
        CP4DAuthenticator.builder()
            .baseUrl("https://my-instance-url")
            .username("username")
            .apiKey("api-key")
            .httpClient(httpClient) // Custom HttpClient
            .build()
    )
    .build();
```

> **Note:** When using a custom `HttpClient` with Cloud Pak for Data, make sure to set it on both the service builder and the authenticator builder to ensure consistent HTTP behavior across all requests.

> 🔗 [SDK guide to the HTTP client](https://ibm.github.io/watsonx-ai-java-sdk/advanced/http-client), including how to build an `SSLContext` from a private truststore.

#### Disabling SSL verification

If you only need to disable SSL certificate verification, you can use the `verifySsl(false)` option instead of providing a custom `HttpClient`:

```java
EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
    .baseUrl("https://my-instance-url")
    .modelName("ibm/granite-embedding-278m-multilingual")
    .projectId("project-id")
    .verifySsl(false) // Disable SSL verification
    .authenticator(
        CP4DAuthenticator.builder()
            .baseUrl("https://my-instance-url")
            .username("username")
            .apiKey("api-key")
            .verifySsl(false) // Disable SSL verification
            .build()
    )
    .build();
```

### How to create an IBM Cloud API Key

You can create an API key at [https://cloud.ibm.com/iam/apikeys](https://cloud.ibm.com/iam/apikeys) by clicking **Create +**.

### How to find your Project ID

1. Visit [https://dataplatform.cloud.ibm.com/projects/?context=wx](https://dataplatform.cloud.ibm.com/projects/?context=wx)  
2. Open your project  
3. Go to the **Manage** tab  
4. Copy the **Project ID** from the **Details** section  

## WatsonxChatModel

The `WatsonxChatModel` class allows you to create an instance of the `ChatModel` interface fully encapsulated within LangChain4j.
To create an instance, you must specify the mandatory parameters:

- `baseUrl(...)` – IBM Cloud endpoint URL (as `String`, `URI`, or `CloudRegion`)
- `apiKey(...)` – IBM Cloud IAM API key
- `projectId(...)` – IBM Cloud Project ID (or use `spaceId(...)`)
- `modelName(...)` – Foundation model ID for inference

> You can authenticate using either `.apiKey(...)` or a full `Authenticator` instance via `.authenticator(...)`.

> To call a model you have deployed on-demand, use [`WatsonxDeploymentChatModel`](#deployed-models-on-demand-deployment) instead.

### Example

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import com.ibm.watsonx.ai.CloudRegion;

ChatModel chatModel = WatsonxChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-4-h-small")
    .temperature(0.7)
    .maxOutputTokens(0)
    .build();

String answer = chatModel.chat("Hello from watsonx.ai");
System.out.println(answer);
```

> 🔗 [View available models](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx#ibm-provided)

## WatsonxStreamingChatModel

The `WatsonxStreamingChatModel` provides streaming support for IBM watsonx.ai within LangChain4j. It's useful when you want to process tokens as they are generated, ideal for real-time applications such as chat UIs or long text generation.

Streaming uses the same configuration structure and parameters as the non-streaming [`WatsonxChatModel`](#watsonxchatmodel). The main difference is that responses are delivered incrementally through a handler interface.

> To call a model you have deployed on-demand, use [`WatsonxDeploymentStreamingChatModel`](#deployed-models-on-demand-deployment) instead.

### Example

```java
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.ChatResponse;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import com.ibm.watsonx.ai.CloudRegion;

StreamingChatModel model = WatsonxStreamingChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-4-h-small")
    .maxOutputTokens(0)
    .build();

model.chat("What is the capital of Italy?", new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.println("Partial: " + partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        System.out.println("Complete: " + completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

> 🔗 [View available models](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx#ibm-provided)

## Deployed models (on-demand deployment)

IBM watsonx.ai allows you to deploy foundation models on-demand on dedicated hardware for exclusive use by your organization. These deployed models are addressed by their `deploymentId` and are served by a different watsonx.ai endpoint than the foundation-model catalog, so LangChain4j exposes them through their own pair of classes, `WatsonxDeploymentChatModel` and `WatsonxDeploymentStreamingChatModel`.

To create an instance, you must specify:

- `baseUrl(...)` – IBM Cloud endpoint URL (as `String`, `URI`, or `CloudRegion`)
- `apiKey(...)` – IBM Cloud IAM API key
- `deploymentId(...)` – Deployment ID of the on-demand deployed model

A deployment already targets a specific model within a project or space, so these builders expose neither `modelName(...)` nor `projectId(...)`/`spaceId(...)`. Every other generation parameter (`temperature`, `maxOutputTokens`, `thinking`, tools, `responseFormat`, …) works exactly as it does on `WatsonxChatModel`.

> **Note:** `deploymentId` is a connection-level setting fixed when the model is built - it selects the deployment endpoint, so it cannot be overridden per request through `WatsonxChatRequestParameters`.

### WatsonxDeploymentChatModel

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.watsonx.WatsonxDeploymentChatModel;
import com.ibm.watsonx.ai.CloudRegion;

ChatModel chatModel = WatsonxDeploymentChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .deploymentId("your-deployment-id")
    .temperature(0.7)
    .maxOutputTokens(0)
    .build();

String answer = chatModel.chat("Hello from watsonx.ai");
System.out.println(answer);
```

### WatsonxDeploymentStreamingChatModel

```java
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.ChatResponse;
import dev.langchain4j.model.watsonx.WatsonxDeploymentStreamingChatModel;
import com.ibm.watsonx.ai.CloudRegion;

StreamingChatModel model = WatsonxDeploymentStreamingChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .deploymentId("your-deployment-id")
    .maxOutputTokens(0)
    .build();

model.chat("What is the capital of Italy?", new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.println("Partial: " + partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        System.out.println("Complete: " + completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

> 🔗 [Learn more about deploying models on-demand](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/deploy-on-demand-overview.html?context=wx&audience=wdp)

## Model Gateway

The IBM watsonx.ai **Model Gateway** exposes an OpenAI-compatible chat endpoint that can route requests to models hosted by multiple providers (for example OpenAI, Anthropic, or third-party providers you register) behind a single watsonx.ai entry point. LangChain4j integrates with it through `WatsonxGatewayChatModel` and `WatsonxGatewayStreamingChatModel`.

> **Note:** the gateway must be configured by an administrator before use - each `modelName` you pass must be an id already registered in the gateway.

### WatsonxGatewayChatModel

To create an instance, specify:

- `baseUrl(...)` – IBM Cloud endpoint URL (as `String`, `URI`, or `CloudRegion`)
- `apiKey(...)` – IBM Cloud IAM API key (or a full `Authenticator` via `.authenticator(...)`)
- `modelName(...)` – OpenAI-style model id registered in the gateway

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatModel;
import com.ibm.watsonx.ai.CloudRegion;

ChatModel chatModel = WatsonxGatewayChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .modelName("gpt-4o")
    .temperature(0.7)
    .build();

String answer = chatModel.chat("Hello from the watsonx.ai Model Gateway");
System.out.println(answer);
```

### WatsonxGatewayStreamingChatModel

`WatsonxGatewayStreamingChatModel` provides streaming support for the gateway. It uses the same configuration
as `WatsonxGatewayChatModel`. Responses are delivered incrementally through a handler.

```java
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.ChatResponse;
import dev.langchain4j.model.watsonx.WatsonxGatewayStreamingChatModel;
import com.ibm.watsonx.ai.CloudRegion;

StreamingChatModel model = WatsonxGatewayStreamingChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .modelName("gpt-4o")
    .build();

model.chat("What is the capital of Italy?", new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.println("Partial: " + partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        System.out.println("Complete: " + completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }
});
```

### Gateway-only parameters

Each watsonx.ai chat service has its own `ChatRequestParameters` implementation, exposing the parameters that
service accepts and nothing else:

| Class | Used by |
|---|---|
| `WatsonxChatRequestParameters` | `WatsonxChatModel`, `WatsonxStreamingChatModel`, `WatsonxDeploymentChatModel`, `WatsonxDeploymentStreamingChatModel` |
| `WatsonxGatewayChatRequestParameters` | `WatsonxGatewayChatModel`, `WatsonxGatewayStreamingChatModel` |

The two classes are independent implementations of `ChatRequestParameters`. Each one declares exactly what its service
supports, so neither knows anything about the other's parameters. Passing the parameters of one service to the other -
either through `defaultRequestParameters(...)` on the builder or through the parameters of a single `ChatRequest` -
therefore contributes only what `DefaultChatRequestParameters` covers (`modelName`, `temperature`, `topP`,
`maxOutputTokens`, …), and every watsonx.ai-specific parameter they carry is ignored. Use the class that matches the
model you are calling.

In addition to the common chat parameters, `WatsonxGatewayChatRequestParameters` exposes gateway-specific parameters:

| Parameter | Description |
|---|---|
| `serviceTier(...)` | Service tier, one of `AUTO`, `DEFAULT`, `FLEX`, or `PRIORITY`. |
| `reasoningEffort(...)` | Reasoning effort for reasoning models, one of `LOW`, `MEDIUM`, or `HIGH`. |
| `router(...)` / `cache(...)` | Router configuration, including a prompt `Cache`. |
| `modalities(...)` | Output modalities (e.g. `["text"]`). |
| `store(...)` | Whether the provider should persist the request/response. |
| `parallelToolCalls(...)` | Enable/disable parallel tool calls. |
| `user(...)` | End-user identifier forwarded to the provider. |
| `metadata(...)` | Free-form metadata map forwarded to the provider. |
| `logitBias(...)`, `logprobs(...)`, `topLogprobs(...)`, `seed(...)` | OpenAI-compatible sampling controls. |

```java
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatRequestParameters;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ReasoningEffort;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ServiceTier;

ChatRequest request = ChatRequest.builder()
    .messages(UserMessage.from("Solve this step by step."))
    .parameters(
        WatsonxGatewayChatRequestParameters.builder()
            .serviceTier(ServiceTier.FLEX)
            .reasoningEffort(ReasoningEffort.HIGH)
            .build()
    ).build();

String answer = chatModel.chat(request).aiMessage().text();
```

### Response metadata

Every watsonx.ai chat response carries a `WatsonxChatResponseMetadata`. Three of its fields are only populated by the
Model Gateway and stay `null` for the other services:

```java
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.watsonx.WatsonxChatResponseMetadata;

ChatResponse response = chatModel.chat(request);
var metadata = (WatsonxChatResponseMetadata) response.metadata();

metadata.getServiceTier();       // service tier that served the request (gateway only)
metadata.getSystemFingerprint(); // provider system fingerprint (gateway only)
metadata.getCached();            // whether the response was served from cache (gateway only)
```

## Tool Integration

All the watsonx.ai chat models - `WatsonxChatModel`, `WatsonxStreamingChatModel`, `WatsonxDeploymentChatModel`, `WatsonxDeploymentStreamingChatModel`, `WatsonxGatewayChatModel` and `WatsonxGatewayStreamingChatModel` - support **LangChain4j Tools**, allowing the model to call Java methods annotated with `@Tool`.

Here’s an example using the synchronous model (`WatsonxChatModel`), but the same approach applies to the streaming and to the deployment/gateway variants.

```java
static class Tools {

    @Tool
    LocalDate currentDate() {
        return LocalDate.now();
    }

    @Tool
    LocalTime currentTime() {
        return LocalTime.now();
    }
}

interface AiService {
    String chat(String userMessage);
}

ChatModel chatModel = WatsonxChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("mistralai/mistral-small-3-1-24b-instruct-2503")
    .maxOutputTokens(0)
    .build();

AiService aiService = AiServices.builder(AiService.class)
        .chatModel(chatModel)
        .tools(new Tools())
        .build();

String answer = aiService.chat("What is the date today?");
System.out.println(answer);
```

> **NOTE:** Ensure your selected model supports tool use.
---

## Structured Outputs

All the watsonx.ai chat models can constrain the response to a JSON Schema. The generic LangChain4j documentation is available [here](/tutorials/structured-outputs), while this section describes the watsonx.ai specific behavior.

The `strictJsonSchema(...)` builder method controls how the schema is sent to the service and defaults to `true`. In strict mode the model is required to adhere to the schema, every property is marked as `required`, `additionalProperties` is set to `false` and the properties left out of the required list are made nullable.

```java
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import com.ibm.watsonx.ai.CloudRegion;

ChatModel chatModel = WatsonxChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-4-h-small")
    .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
    .strictJsonSchema(true) // default value
    .build();
```

> Use `strictJsonSchema(false)` to send the schema as a hint instead of a constraint. The model still tries to produce a response that adheres to the schema, but the request does not fail when the response diverges from it, the required list is sent as declared and `additionalProperties` is left out. This is the mode to use when optional fields must stay optional.

> `supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)` is needed only when the model is used through AI Services, where the JSON Schema is generated from the return type of the AI Service method.

The root element of the JSON Schema must be a `JsonObjectSchema` or a `JsonRawSchema`. Any other root element makes the request fail with an `IllegalArgumentException`.

## Enabling Thinking / Reasoning Output

Some foundation models can include internal *reasoning* (also referred to as *thinking*) steps as part of their responses.  
Depending on the model, this reasoning may be **embedded in the same text as the final response**, or **returned separately** in a dedicated field from `watsonx.ai`.  

To correctly enable and capture this behavior, you must configure the `thinking(...)` builder method according to the model’s output format.  
This ensures that LangChain4j can automatically extract the reasoning and response content from the model output.

There are two main configuration modes:

- **`ExtractionTags`** → for models that return reasoning and response in the same text block (e.g **ibm/granite-3-3-8b-instruct**).  
- **`ThinkingEffort`** → for models that already separate reasoning and response automatically (e.g **openai/gpt-oss-120b**).  

> The `thinking(...)` builder method is available on `WatsonxChatModel`, `WatsonxStreamingChatModel`, `WatsonxDeploymentChatModel` and `WatsonxDeploymentStreamingChatModel`. The Model Gateway does not accept it, so use [`reasoningEffort(...)`](#gateway-only-parameters) on the gateway models instead.

### Models that return reasoning and response together

Use **`ExtractionTags`** when the model outputs reasoning and response in the same text string.  
The tags define XML-like markers used to separate the reasoning from the final response.

**Example tags:**

- **Reasoning tag:** `<think>` - contains the model's internal reasoning.  
- **Response tag:** `<response>` - contains the user-facing answer.  

#### Behavior

- If **both tags** are specified, they are used directly to extract reasoning and response segments.  
- If **only the reasoning tag** is specified, everything outside that tag is considered the response.  

#### Example for **ibm/granite-3-3-8b-instruct**

```java
ChatModel chatModel = WatsonxChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-3-3-8b-instruct")
    .maxOutputTokens(0)
    .thinking(ExtractionTags.of("think", "response"))
    .build();

ChatResponse chatResponse = chatModel.chat(
    UserMessage.userMessage("Why is the sky blue?")
);

AiMessage aiMessage = chatResponse.aiMessage();

System.out.println(aiMessage.thinking());
System.out.println(aiMessage.text());
```

### Models that return reasoning and response separately.

For models that already return reasoning and response as separate fields, use the **`ThinkingEffort`** to control how much reasoning the model applies during generation.
Alternatively, enable it using the boolean flag.

#### Example for **openai/gpt-oss-120b**

```java
ChatModel chatModel = WatsonxChatModel.builder()
    .baseUrl(CloudRegion.DALLAS)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("openai/gpt-oss-120b")
    .thinking(ThinkingEffort.HIGH)
    .build();
```

or

```java
ChatModel chatModel = WatsonxChatModel.builder()
    .baseUrl(CloudRegion.DALLAS)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("openai/gpt-oss-120b")
    .thinking(true)
    .build();
```

### Streaming Example

```java
StreamingChatModel model = WatsonxStreamingChatModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-3-3-8b-instruct")
    .thinking(ExtractionTags.of("think", "response"))
    .build();

List<ChatMessage> messages = List.of(
    UserMessage.userMessage("Why is the sky blue?")
);

ChatRequest chatRequest = ChatRequest.builder()
    .messages(messages)
    .build();

model.chat(chatRequest, new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        ...
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking) {
        ...
    }
});
```

> **Notes:**
> - Ensure that the selected model supports reasoning output.  
> - Use `ExtractionTags` for models that embed reasoning and response in a single text string.  
> - Use `ThinkingEffort` or `thinking(true)` for models that already separate reasoning and response automatically.  

## WatsonxModelCatalog

The `WatsonxModelCatalog` provides a programmatic way to discover and list all available foundation models on IBM watsonx.ai.
It implements the LangChain4j `ModelCatalog` interface, allowing you to retrieve detailed information about each model.

### Example

```java
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.watsonx.WatsonxModelCatalog;
import com.ibm.watsonx.ai.CloudRegion;

ModelCatalog modelCatalog = WatsonxModelCatalog.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .build();

var models = modelCatalog.listModels();
```

> 🔗 [SDK foundation model service](https://ibm.github.io/watsonx-ai-java-sdk/services/foundation-model-service)

## WatsonxGatewayModelCatalog

The `WatsonxGatewayModelCatalog` is the [Model Gateway](#model-gateway) counterpart of `WatsonxModelCatalog`. Instead of listing the foundation models hosted by watsonx.ai, it lists the models configured in the gateway, aggregated across all the providers registered in it. It also implements the LangChain4j `ModelCatalog` interface.

The `name()` of every returned `ModelDescription` is the identifier to pass to `WatsonxGatewayChatModel.modelName(...)` and `WatsonxGatewayStreamingChatModel.modelName(...)`. It is the model **alias** when the gateway administrator defined one, otherwise the provider-side model id.

### Example

```java
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.watsonx.WatsonxGatewayModelCatalog;
import com.ibm.watsonx.ai.CloudRegion;

ModelCatalog modelCatalog = WatsonxGatewayModelCatalog.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .build();

for (ModelDescription model : modelCatalog.listModels()) {
    System.out.println(model.name() + " (" + model.owner() + ")");
}
// → gpt-4o (openai)
// → claude-3-5-sonnet (anthropic)
```

### How the gateway models are mapped

| `ModelDescription` | Gateway field | Notes |
|---|---|---|
| `name()` | `alias`, or `id` when there is no alias | the id to use with the gateway chat models |
| `displayName()` | same as `name()` | the gateway has no separate label |
| `description()` | `description` | user-defined, `null` unless the administrator set it |
| `owner()` | `owned_by` | provider, e.g. `openai` |
| `createdAt()` | `created` | Unix timestamp of the *gateway configuration*, not of the model release |
| `type()` | - | always `ModelType.CHAT` because the gateway does not expose model capabilities |
| `maxInputTokens()` | `metadata.context_window` | `null` when the administrator did not configure the metadata |
| `maxOutputTokens()` | - | always `null`, the gateway does not return it |

## WatsonxTokenCountEstimator

The `WatsonxTokenCountEstimator` implements the LangChain4j `TokenCountEstimator` interface by calling the watsonx.ai
tokenization endpoint, so the count comes from the tokenizer of the model itself rather than from a local approximation.
Because of that, `modelName(...)` is mandatory.

### Example

```java
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.watsonx.WatsonxTokenCountEstimator;
import com.ibm.watsonx.ai.CloudRegion;

TokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .modelName("ibm/granite-4-h-small")
    .build();

int tokenCount = tokenCountEstimator.estimateTokenCountInText("Hello from watsonx.ai");
```

> **Note:** every estimate is a remote call. `estimateTokenCountInMessage(...)` also counts the thinking text and the
> tool execution requests of an `AiMessage`. Image, audio, PDF and video contents are not supported.

> 🔗 [SDK tokenization service](https://ibm.github.io/watsonx-ai-java-sdk/services/tokenization-service)

## WatsonxModerationModel

The `WatsonxModerationModel` provides a LangChain4j implementation of the `ModerationModel` interface using IBM watsonx.ai.  
It allows you to automatically detect and flag sensitive, unsafe, or policy-violating content in text through **detectors**.

One or multiple **detectors** can be used to identify different types of content, such as:

- **Pii** – Detects Personally Identifiable Information (e.g., emails, phone numbers)  
- **Hap** – Detects hate, abuse, or profanity  
- **GraniteGuardian** – Detects risky or harmful language  

### Example

```java
ModerationModel model = WatsonxModerationModel.builder()
    .baseUrl(CloudRegion.FRANKFURT)
    .apiKey("your-api-key")
    .projectId("your-project-id")
    .detectors(Hap.ofDefaults(), GraniteGuardian.ofDefaults())
    .build();

Response<Moderation> response = model.moderate("...");
```

### Metadata

Each moderation response includes a `metadata` map that provides additional context about the detection.  

| Key | Description | 
|-----|--------------|
| `detection` | The detected label or category assigned by the detector
| `detection_type` | The type of detector that triggered the flag 
| `start` | The starting character index of the detected segment 
| `end` | The ending character index of the detected segment 
| `score` | The confidence score of the detection 

These metadata values are available via `Response.metadata()`:

```java
Map<String, Object> metadata = response.metadata();
System.out.println("Detection type: " + metadata.get("detection_type"));
System.out.println("Score: " + metadata.get("score"));
```

> 🔗 [SDK detection service](https://ibm.github.io/watsonx-ai-java-sdk/services/detection-service), for the full list of
> detectors and their options.

## Configuration via Environment Variables

The internal HTTP behavior of the underlying SDK can be customized through environment variables, without any code
change. These settings are optional and sensible defaults are used when variables are not explicitly defined.

### Retry Configuration

HTTP requests are automatically retried in case of transient failures or expired authentication tokens.  
Retry behavior can be customized using the following environment variables:

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `WATSONX_RETRY_TOKEN_EXPIRED_MAX_RETRIES` | Maximum number of retries when an authentication token has expired (HTTP 401 / 403) | `1` |
| `WATSONX_RETRY_STATUS_CODES_MAX_RETRIES` | Maximum number of retries for transient HTTP status codes (`429`, `503`, `504`, `520`) | `10` |
| `WATSONX_RETRY_STATUS_CODES_BACKOFF_ENABLED` | Enables exponential backoff for transient retries | `true` |
| `WATSONX_RETRY_STATUS_CODES_INITIAL_INTERVAL_MS` | Initial retry interval in milliseconds (used as base for exponential backoff) | `20` |

### HTTP IO Executor Configuration

Streaming responses and HTTP response processing are handled by an internal IO executor.  
By default, virtual threads are used on Java 21+ and a cached thread pool on Java 17–20.

This behavior can be customized using the following environment variable:

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `WATSONX_IO_EXECUTOR_THREADS` | Caps the IO executor to a fixed-size pool of this many threads | _unset_ |

> 🔗 [Full reference of the SDK environment variables](https://ibm.github.io/watsonx-ai-java-sdk/advanced/environment-variables)

## Error Handling

The watsonx.ai errors raised by the SDK are translated into the standard LangChain4j exceptions, so you can handle them
the same way as with any other provider. The mapping is driven by the error code returned by watsonx.ai:

| watsonx.ai error code | LangChain4j exception |
|---|---|
| `authentication_token_expired`, `authorization_rejected` | `AuthenticationException` |
| `invalid_input_argument`, `invalid_request_entity`, `json_type_error`, `json_validation_error` | `InvalidRequestException` |
| `model_not_supported` | `ModelNotFoundException` |
| `token_quota_reached` | `RateLimitException` |
| any other error code | `LangChain4jException` |

When the response carries no error body, the exception is chosen from the HTTP status code instead. A request that
exceeds its `timeout(...)` is reported as a `TimeoutException`.

```java
try {
    String answer = chatModel.chat("Hello from watsonx.ai");
} catch (RateLimitException e) {
    // token quota reached
} catch (InvalidRequestException e) {
    // a request parameter was rejected by watsonx.ai
}
```

> 🔗 [SDK exception hierarchy](https://ibm.github.io/watsonx-ai-java-sdk/advanced/error-handling), for the underlying
> `WatsonxException` and its `statusCode()`, `errorCode()` and `traceId()`, available through `getCause()`.

## Quarkus

See more details [here](https://docs.quarkiverse.io/quarkus-langchain4j/dev/watsonx-chat-model.html).

## Examples

- [WatsonxChatModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxChatModelTest.java)
- [WatsonxChatModelReasoningTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxChatModelReasoningTest.java)
- [WatsonxStreamingChatModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxStreamingChatModelTest.java)
- [WatsonxStreamingChatModelReasoningTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxStreamingChatModelReasoningTest.java)
- [WatsonxToolsTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxToolsTest.java)
- [WatsonxTokenCounterEstimatorTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxTokenCounterEstimatorTest.java)
- [WatsonxModerationModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxModerationModelTest.java)
