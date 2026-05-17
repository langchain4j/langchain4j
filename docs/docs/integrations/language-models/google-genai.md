---
sidebar_position: 8
---

# Google Gen AI (Experimental)

https://github.com/googleapis/java-genai

> [!WARNING]  
> This integration is currently marked as **Experimental**. The API and implementation are subject to change in future releases.
> It uses the new official Google Gen AI SDK for Java (`com.google.genai:google-genai`).

## Table of Contents

- [Maven Dependency](#maven-dependency)
- [API Key](#api-key)
- [Models Available](#models-available)
- [GoogleGenAiChatModel](#googlegenAichatmodel)
    - [Configuring](#configuring)
- [GoogleGenAiStreamingChatModel](#googlegenaistreamingchatmodel)
    - [Executor](#executor)
- [GoogleGenAiEmbeddingModel](#googlegenaiembeddingmodel)
- [GoogleGenAiImageModel](#googlegenaiimagemodel)
- [Batch API](#batch-api)
- [Tools](#tools)
- [JSON Schema / Structured Outputs](#json-schema--structured-outputs)
- [Grounding Metadata](#grounding-metadata)
- [Custom Labels](#custom-labels)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-genai</artifactId>
    <version>1.15.0-beta25</version>
</dependency>
```

## API Key

Get an API key for free here: https://ai.google.dev/gemini-api/docs/api-key.

## Models available

Check the list of [available models](https://ai.google.dev/gemini-api/docs/models/gemini) in the documentation.

* `gemini-3.1-pro-preview`
* `gemini-3.1-flash-lite`
* `gemini-3-pro-preview`
* `gemini-3-flash-preview`
* `gemini-2.5-pro`
* `gemini-2.5-flash`
* `gemini-2.5-flash-lite`

(See the [official documentation](https://ai.google.dev/gemini-api/docs/models) for the full list of specialized preview models like `-image`, `-tts`, and `-live`).

## GoogleGenAiChatModel

The usual `chat(...)` methods are available:

```java
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

String response = gemini.chat("Hello Gemini!");
```

As well as the `ChatResponse chat(ChatRequest req)` method:

```java
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

ChatResponse chatResponse = gemini.chat(ChatRequest.builder()
    .messages(UserMessage.from(
        "How many R's are there in the word 'strawberry'?"))
    .build());

String response = chatResponse.aiMessage().text();
```

### Configuring

```java
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    // or .googleCredentials(...)
    .projectId(...)
    .location(...)
    .modelName("gemini-2.5-flash")
    .temperature(1.0)
    .topP(0.95)
    .topK(64)
    .seed(42)
    .maxOutputTokens(8192)
    .timeout(Duration.ofSeconds(60))
    .maxRetries(2)
    .stopSequences(List.of(...))
    .safetySettings(List.of(...))
    .responseFormat(ResponseFormat.JSON)
    .enableGoogleSearch(true)
    .enableGoogleMaps(true)
    .enableUrlContext(true)
    .allowedFunctionNames(List.of("getWeather"))
    .thinkingBudget(250)
    .listeners(...)
    .build();
```

## GoogleGenAiStreamingChatModel

The `GoogleGenAiStreamingChatModel` allows streaming the text of a response token by token.
The response must be handled by a `StreamingChatResponseHandler`.

```java
StreamingChatModel gemini = GoogleGenAiStreamingChatModel.builder()
        .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
        .modelName("gemini-2.5-flash")
        .build();

CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

gemini.chat("Tell me a joke about Java", new StreamingChatResponseHandler() {

    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.print(partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        futureResponse.complete(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        futureResponse.completeExceptionally(error);
    }
});

futureResponse.join();
```

### Executor

The Google Gen AI SDK exposes streaming as a **blocking** `ResponseStream` iterator: each chunk is delivered by a blocking `next()` call. `GoogleGenAiStreamingChatModel` therefore needs an `ExecutorService` to drive that iteration off the caller's thread.

If you don't pass one, a shared default from `DefaultExecutorProvider` is used (lazily initialized, uses virtual threads when available). This works out of the box but is **not recommended for production**: the default executor is unbounded, JVM-wide, and not tied to your application lifecycle — so it offers no back-pressure, no graceful shutdown, and no visibility in your metrics.

You should almost always supply your own executor — for example, your framework's managed task executor (Spring `TaskExecutor`, Quarkus `ManagedExecutor`, ...), a virtual-thread executor you own, or a bounded pool tuned to your concurrency budget:

```java
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor(); // or your framework's executor

StreamingChatModel gemini = GoogleGenAiStreamingChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .executor(executor)
    .build();
```

## Tools

Tools (aka Function Calling) are supported. You can define them using LangChain4j's `AiServices`:

```java
class WeatherForecastService {
    @Tool("Get the weather forecast for a location")
    String getForecast(@P("Location to get the forecast for") String location) {
        return "The weather in " + location + " is sunny and 25°C.";
    }
}

interface WeatherAssistant {
    String chat(String userMessage);
}

WeatherForecastService weatherForecastService = new WeatherForecastService();

ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .temperature(0.0)
    .build();

WeatherAssistant weatherAssistant = AiServices.builder(WeatherAssistant.class)
    .chatModel(gemini)
    .tools(weatherForecastService)
    .build();

String response = weatherAssistant.chat("What is the weather forecast for Tokyo?");
```

## JSON Schema / Structured Outputs

The `langchain4j-google-genai` integration maps LangChain4j JSON schemas (`ResponseFormat.jsonSchema()`) directly into the `ResponseSchema` of the official Google Gen AI SDK. This allows natively extracting strongly-typed Java records!

```java
record WeatherForecast(
    @Description("minimum temperature") Integer minTemperature,
    @Description("maximum temperature") Integer maxTemperature,
    @Description("chances of rain") boolean rain
) { }

interface WeatherForecastAssistant {
    WeatherForecast extract(String forecast);
}

ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

WeatherForecastAssistant forecastAssistant = AiServices.builder(WeatherForecastAssistant.class)
    .chatModel(gemini)
    .build();

WeatherForecast forecast = forecastAssistant.extract("""
    Morning: The day dawns bright and clear in Osaka...
    Temperatures climb to a comfortable 22°C (72°F) and 
    will drop to 15°C (59°F).
    """);
```

> [!NOTE]  
> The Google Gen AI API has some restrictions on advanced JSON schema features (such as `anyOf` / polymorphic typing). Simple POJOs, lists, and nested objects are fully supported.

## GoogleGenAiEmbeddingModel

The `GoogleGenAiEmbeddingModel` allows you to generate embeddings for text segments using models like `gemini-embedding-2`.

```java
EmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-embedding-2")
    .outputDimensionality(768)
    .taskType(GoogleGenAiEmbeddingModel.TaskTypeEnum.RETRIEVAL_DOCUMENT)
    .build();

Response<Embedding> response = embeddingModel.embed("Hello world!");
```

## GoogleGenAiImageModel

The `GoogleGenAiImageModel` allows you to generate images from text prompts. It supports custom configuration like aspect ratios, image sizes, and person generation policies.

```java
ImageModel imageModel = GoogleGenAiImageModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-3.1-flash-image-preview")
    .aspectRatio("16:9")
    .build();

Response<Image> response = imageModel.generate("A futuristic city at sunset");
```

## Batch API

The Google Gen AI integration provides support for the Batch API, allowing you to run operations asynchronously in the background. The following batch models are supported:
- `GoogleGenAiBatchChatModel`
- `GoogleGenAiBatchEmbeddingModel`
- `GoogleGenAiBatchImageModel`

You can create batch jobs inline or from an uploaded file on Google Cloud. 

```java
GoogleGenAiBatchChatModel batchChatModel = GoogleGenAiBatchChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

BatchResponse<ChatResponse> batchResponse = batchChatModel.createBatchInline(
    "My Batch Job",
    null,
    List.of(
        ChatRequest.builder().messages(UserMessage.from("What is 2+2?")).build(),
        ChatRequest.builder().messages(UserMessage.from("What is the capital of France?")).build()
    )
);

System.out.println("Batch Job ID: " + batchResponse.name().value());
```

You can then retrieve the status and results of the job using `batchChatModel.retrieveBatchResults(batchResponse.name())`.

## Grounding Metadata

If you enable Google Search grounding or use a Vertex AI Search datastore, the Google Gen AI chat model exposes the native `GroundingMetadata` directly in the `ChatResponse`. You can retrieve it through the response metadata.

```java
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .enableGoogleSearch(true)
    .build();

ChatResponse response = gemini.chat(ChatRequest.builder()
    .messages(UserMessage.from("Who won the super bowl in 2024?"))
    .build());

GoogleGenAiChatResponseMetadata metadata = 
    (GoogleGenAiChatResponseMetadata) response.metadata();

if (metadata.groundingMetadata() != null) {
    System.out.println("Search Queries: " + 
        metadata.groundingMetadata().webSearchQueries());
}
```

## Custom Labels

You can apply custom key-value labels to your Google Gen AI requests, which can be useful for billing, metrics, and tracking purposes. Custom labels are supported by:
- `GoogleGenAiChatModel`
- `GoogleGenAiStreamingChatModel`
- `GoogleGenAiBatchChatModel`
- `GoogleGenAiImageModel`
- `GoogleGenAiBatchImageModel`

```java
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .labels(Map.of("environment", "production", "team", "backend"))
    .build();
```
