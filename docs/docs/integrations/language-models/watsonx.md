---
sidebar_position: 22
---

# watsonx.ai

- [watsonx.ai API Reference](https://cloud.ibm.com/apidocs/watsonx-ai)
- [watsonx.ai Java SDK](https://github.com/IBM/watsonx-ai-java-sdk)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-watsonx</artifactId>
    <version>1.3.0</version>
</dependency>
```

## WatsonxChatModel

To use the `WatsonxChatModel` in LangChain4j, you need to configure an instance of `ChatService` provided by the [watsonx.ai Java SDK](https://github.com/IBM/watsonx-ai-java-sdk). This service acts as a bridge between LangChain4j and IBM watsonx.ai APIs.

### Authentication

Authentication is handled via the `IAMAuthenticator`, which implements the `AuthenticationProvider` interface. You will need a valid IBM Cloud IAM API key to authenticate.

You can create an API key by visiting [https://cloud.ibm.com/iam/apikeys](https://cloud.ibm.com/iam/apikeys) and clicking **Create +**.

```java
AuthenticationProvider authProvider = IAMAuthenticator.builder()
        .apiKey("<api-key>")
        .build();
```

Token acquisition and renewal are managed automatically by the SDK. You typically don’t need to manually retrieve or manage tokens.

> 🔗 [Learn more about IAM authentication](https://cloud.ibm.com/docs/account?topic=account-iamtoken_from_apikey)

### Configuring the ChatService

Once the `AuthenticationProvider` is created, you can build a `ChatService` to configure the connection to the watsonx.ai chat API.

```java
ChatService chatService = ChatService.builder()
        .url(CloudRegion.FRANKFURT) // Or specify a custom URL
        .authenticationProvider(authProvider)
        .projectId("<project_id>") // OR use .spaceId("<space_id>")
        .modelId("llama-4-maverick-17b-128e-instruct-fp8")
        .build();
```

> **NOTE:** You must provide either a `projectId` **or** a `spaceId`.

#### How to find your Project ID

1. Visit [https://dataplatform.cloud.ibm.com/projects/?context=wx](https://dataplatform.cloud.ibm.com/projects/?context=wx)
2. Open your project.
3. Navigate to the **Manage** tab.
4. Copy the **Project ID** from the **Details** section.

#### Available builder options

The `ChatService.Builder` supports several optional configuration methods:

| Method                  | Description                                                                                      | Default Value                         |
|------------------------|--------------------------------------------------------------------------------------------------|---------------------------------------|
| `url(...)`             | Endpoint URL (as `String`, `URI`, or `CloudRegion`)                                              | _Required_                            |
| `version(...)`         | API version date (`YYYY-MM-DD`)                                                                  | Latest supported version              |
| `projectId(...)`       | IBM Cloud Project ID                                                                             | One of `projectId` or `spaceId` is required |
| `spaceId(...)`         | Space ID used for organizing resources                                                            | See above                             |
| `modelId(...)`         | Foundation model ID to be used for inference                                                     | _Required_                            |
| `timeout(...)`         | Request timeout (`java.time.Duration`)                                                           | 10 seconds                            |
| `logRequests(...)`     | Whether to log the request payload                                                               | false                                 |
| `logResponses(...)`    | Whether to log the response payload                                                              | false                                 |
| `httpClient(...)`      | Custom HTTP client instance                                                                      | Default Java `HttpClient`             |
| `authenticationProvider(...)` | Auth mechanism to use (e.g. `IAMAuthenticator`)                                           | _Required_                            |
| `foundationModelService(...)` | Optional custom instance to query foundation model specs                                 | Auto-created if not specified         |

> 🔗 [View available model IDs](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx#ibm-provided)

You can also retrieve model details programmatically using the `FoundationModelService`:

```java
FoundationModelService service = FoundationModelService.builder()
        .url(CloudRegion.FRANKFURT)
        .build();

service.getModels().resources();
```

Alternatively, if you already have a `ChatService` instance, you can retrieve the model metadata directly using:

```java
FoundationModel modelInfo = chatService.getModelDetails();
```

> 🔗 [FoundationModelService API reference](https://cloud.ibm.com/apidocs/watsonx-ai#list-foundation-model-specs)

### Creating the LangChain4j ChatModel

Once the `ChatService` is configured, you can integrate it into LangChain4j by using the `WatsonxChatModel`:

```java
ChatModel model = WatsonxChatModel.builder()
        .service(chatService)
        .build();

String answer = model.chat("Say 'Hello World'");
System.out.println(answer);
```

## WatsonxStreamingChatModel

The `WatsonxStreamingChatModel` offers streaming support when using IBM watsonx.ai within LangChain4j. It is ideal for applications where response tokens should be processed as they are generated.

Streaming is supported using the same configuration structure and parameters as the non-streaming `WatsonxChatModel`.

### Configuration

`WatsonxStreamingChatModel` uses the same `ChatService` configuration described in the [WatsonxChatModel](#watsonxchatmodel) section. You must build the `ChatService` with a model that supports streaming.

```java
AuthenticationProvider authProvider = IAMAuthenticator.builder()
        .apiKey("<your-api-key>")
        .build();

ChatService chatService = ChatService.builder()
        .url(CloudRegion.FRANKFURT)
        .authenticationProvider(authProvider)
        .projectId("<your-project-id>")
        .modelId("mistralai/mistral-small-3-1-24b-instruct-2503")
        .build();
```

> All builder options (e.g. `timeout`, `logRequests`, `foundationModelService`) are available and behave identically to `WatsonxChatModel`.

### Usage Example

You can call the streaming model using a `StreamingChatResponseHandler`, which exposes three callbacks: `onPartialResponse`, `onCompleteResponse`, and `onError`.

```java
StreamingChatModel model = WatsonxStreamingChatModel.builder()
        .service(chatService)
        .build();

model.chat("What is the capital of Italy?", new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        ...
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        ...
    }

    @Override
    public void onError(Throwable error) {
        ...
    }
});
```
---

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

ChatModel model = WatsonxChatModel.builder()
        .service(chatService)
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

## WatsonxEmbeddingModel

The `WatsonxEmbeddingModel` enables you to generate embeddings using IBM watsonx.ai and integrate them with LangChain4j's vector-based operations such as search, retrieval-augmented generation (RAG), and similarity comparison.

It implements the LangChain4j `EmbeddingModel` interface.

---

### Example: LangChain4j Integration

```java
public class WatsonxEmbeddingModelTest {

    static AuthenticationProvider authProvider = IAMAuthenticator.builder()
        .apiKey(System.getenv("WATSONX_API_KEY"))
        .build();

    static EmbeddingService embeddingService = EmbeddingService.builder()
        .url(CloudRegion.FRANKFURT)
        .authenticationProvider(authProvider)
        .projectId(System.getenv("WATSONX_PROJECT_ID"))
        .modelId("ibm/granite-embedding-278m-multilingual")
        .build();

    public static void main(String... args) {

        EmbeddingModel model = WatsonxEmbeddingModel.builder()
            .service(embeddingService)
            .build();

        System.out.println(model.embed("Hello from watsonx.ai"));
    }
}
```

---

### EmbeddingService Configuration

| Parameter                 | Required | Description                                                                 |
|---------------------------|----------|-----------------------------------------------------------------------------|
| `url`                     | ✅       | Endpoint URL or constant from `CloudRegion`.                               |
| `authenticationProvider` | ✅       | IAM-based authentication provider.                                         |
| `projectId` or `spaceId` | ✅       | Only one is required.                                                      |
| `modelId`                 | ✅       | ID of the embedding model to use.                                          |
| `timeout`                 | ❌       | Defaults to 10 seconds.                                                    |
| `logRequests`/`logResponses` | ❌   | Enable debug logging.                                                      |

> **NOTE:** You must provide either a `projectId` **or** a `spaceId`.

> 🔗 [View available model IDs](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models-embed.html?context=wx&audience=wdp#ibm-provided)

## WatsonxScoringModel

The `WatsonxScoringModel` provides a LangChain4j-compatible implementation of a `ScoringModel` using IBM watsonx.ai Rerank (cross-encoder) models.

It is particularly useful for ranking a list of documents (or text segments) based on their relevance to a user query.

---

### Example: LangChain4j Integration

```java
public class WatsonxScoringModelTest {

    static AuthenticationProvider authProvider = IAMAuthenticator.builder()
        .apiKey(System.getenv("WATSONX_API_KEY"))
        .build();

    static RerankService rerankService = RerankService.builder()
        .url(CloudRegion.FRANKFURT)
        .authenticationProvider(authProvider)
        .projectId(System.getenv("WATSONX_PROJECT_ID"))
        .modelId("cross-encoder/ms-marco-minilm-l-12-v2")
        .build();

    public static void main(String... args) {

        ScoringModel model = WatsonxScoringModel.builder()
            .service(rerankService)
            .build();

        var scores = model.scoreAll(
            List.of(
                TextSegment.from("Example_1"),
                TextSegment.from("Example_2")
            ),
            "Hello from watsonx.ai"
        );

        System.out.println(scores);
    }
}
```

---

### RerankService Configuration

| Parameter                 | Required | Description                                                      |
|---------------------------|----------|------------------------------------------------------------------|
| `url`                     | ✅       | Endpoint URL or constant from `CloudRegion`.                     |
| `authenticationProvider` | ✅       | IAM-based authentication provider.                               |
| `projectId` or `spaceId` | ✅       | Only one is required.                                            |
| `modelId`                 | ✅       | ID of the rerank model to use.                                   |
| `timeout`                 | ❌       | Defaults to 10 seconds.                                          |
| `logRequests`/`logResponses` | ❌   | Enable debug logging.                                            |

> **NOTE:** You must provide either a `projectId` **or** a `spaceId`.

> 🔗 [View available model IDs](https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models-embed.html?context=wx&audience=wdp#rerank)

---

## Quarkus

See more details [here](https://docs.quarkiverse.io/quarkus-langchain4j/dev/watsonx-chat-model.html).

## Examples

- [WatsonxChatModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxChatModelTest.java)
- [WatsonxStreamingChatModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxStreamingChatModelTest.java)
- [WatsonxToolsTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxToolsTest.java)
- [WatsonxEmbeddingModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxEmbeddingModelTest.java)
- [WatsonxScoringModelTest](https://github.com/langchain4j/langchain4j-examples/blob/main/watsonx-ai-examples/src/main/java/WatsonxScoringModelTest.java)
