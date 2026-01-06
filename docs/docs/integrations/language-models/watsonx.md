---
sidebar_position: 22
---

# watsonx.ai

- [watsonx.ai API Reference](https://cloud.ibm.com/apidocs/watsonx-ai#chat-completions)
- [watsonx.ai Java SDK](https://github.com/IBM/watsonx-ai-java-sdk)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-watsonx</artifactId>
    <version>1.10.0-beta18</version>
</dependency>
```

## Authentication

Watsonx.ai supports authentication via the `Authenticator` interface.

This allows to use different authentication mechanisms depending on your deployment:

- **IBMCloudAuthenticator** – authenticates with **IBM Cloud** using an API key. This is the simplest approach and is used when you provide the `apiKey(...)` builder method.
- **CP4DAuthenticator** – authenticates with **Cloud Pak for Data** deployments.
- **Custom authenticators** – any implementation of the `Authenticator` interface can be used.

The `WatsonxChatModel`, `WatsonxStreamingChatModel`, and other service builders accept either a shortcut via `.apiKey(...)` or a full `Authenticator` instance via `.authenticator(...)`.

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

- `baseUrl(...)` – IBM Cloud endpoint URL (as `String`, `URI`, or `CloudRegion`);
- `apiKey(...)` – IBM Cloud IAM API key;
- `projectId(...)` – IBM Cloud Project ID (or use `spaceId(...)`);
- `modelName(...)` – Foundation model ID for inference;

> You can authenticate using either `.apiKey(...)` or a full `Authenticator` instance via `.authenticator(...)`.

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

The `WatsonxStreamingChatModel` provides streaming support for IBM watsonx.ai within LangChain4j. It’s useful when you want to process tokens as they are generated, ideal for real-time applications such as chat UIs or long text generation.

Streaming uses the same configuration structure and parameters as the non-streaming [`WatsonxChatModel`](#watsonxchatmodel). The main difference is that responses are delivered incrementally through a handler interface.

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

## Tool Integration

Both `WatsonxChatModel` and `WatsonxStreamingChatModel` support **LangChain4j Tools**, allowing the model to call Java methods annotated with `@Tool`.

Here’s an example using the synchronous model (`WatsonxChatModel`), but the same approach applies to the streaming variant.

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
        .chatModel(model)
        .tools(new Tools())
        .build();

String answer = aiService.chat("What is the date today?");
System.out.println(answer);
```

> **NOTE:** Ensure your selected model supports tool use.
---

## Enabling Thinking / Reasoning Output

Some foundation models can include internal *reasoning* (also referred to as *thinking*) steps as part of their responses.  
Depending on the model, this reasoning may be **embedded in the same text as the final response**, or **returned separately** in a dedicated field from `watsonx.ai`.  

To correctly enable and capture this behavior, you must configure the `thinking(...)` builder method according to the model’s output format.  
This ensures that LangChain4j can automatically extract the reasoning and response content from the model output.

There are two main configuration modes:

- **`ExtractionTags`** → for models that return reasoning and response in the same text block (e.g **ibm/granite-3-3-8b-instruct**).  
- **`ThinkingEffort`** → for models that already separate reasoning and response automatically (e.g **openai/gpt-oss-120b**).  

### Models that return reasoning and response together

Use **`ExtractionTags`** when the model outputs reasoning and response in the same text string.  
The tags define XML-like markers used to separate the reasoning from the final response.

**Example tags:**

- **Reasoning tag:** `<think>` — contains the model's internal reasoning.  
- **Response tag:** `<response>` — contains the user-facing answer.  

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

## WatsonxModerationModel

The `WatsonxModerationModel` provides a LangChain4j implementation of the `ModerationModel` interface using IBM watsonx.ai.  
It allows to automatically detect and flag sensitive, unsafe, or policy-violating content in text through **detectors**.

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
## Configuration via Environment Variables

The LangChain4j watsonx integration allows customization of internal HTTP behavior through environment variables.  
These settings are optional and sensible defaults are used when variables are not explicitly defined.

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
By default, a single-threaded executor is used to ensure sequential processing of streaming events.

This behavior can be customized using the following environment variable:

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `WATSONX_IO_EXECUTOR_THREADS` | Number of threads used for HTTP IO and SSE stream parsing | `1` |

## Quarkus

See more details [here](https://docs.quarkiverse.io/quarkus-langchain4j/dev/watsonx-chat-model.html).

## Examples

- [WatsonxChatModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxChatModelTest.java)
- [WatsonxChatModelReasoningTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxChatModelReasoningTest.java)
- [WatsonxStreamingChatModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxStreamingChatModelTest.java)
- [WatsonxStreamingChatModelReasoningTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxStreamingChatModelTest.java)
- [WatsonxToolsTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxToolsTest.java)
- [WatsonxTokenCounterEstimatorTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxTokenCounterEstimatorTest.java)
- [WatsonxModerationModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxModerationModelTest.java)