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
- [Request & Response Logging](#request--response-logging)
- [Batch API](#batch-api)
- [Tools](#tools)
- [JSON Schema / Structured Outputs](#json-schema--structured-outputs)
- [Grounding Metadata](#grounding-metadata)
- [Custom Labels](#custom-labels)
- [File API](#file-api)
- [Cached Content Support](#cached-content-support)
- [Thinking Models (Gemini 3.0+)](#thinking-models-gemini-30)
- [Multimodality (Audio, Video, PDF)](#multimodality-audio-video-pdf)
- [Live API (Experimental)](#live-api-experimental)
- [Token Count Estimator](#token-count-estimator)
- [Model Catalog](#model-catalog)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-genai</artifactId>
    <version>1.18.0-beta28</version>
</dependency>
```

## Authentication

You can authenticate with the Gemini models using either an API key or Google Cloud Vertex AI credentials.

### Gemini Developer API (API Key)

Get an API key for free here: https://ai.google.dev/gemini-api/docs/api-key.
You can provide it to the builder using `.apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))`.

### Google Cloud Vertex AI

If you are using Vertex AI, you can authenticate using Google Credentials along with your project ID and location. The integration will automatically use Application Default Credentials (ADC) if available, or you can explicitly provide them:

```java
ChatModel gemini = GoogleGenAiChatModel.builder()
    // .googleCredentials(...) // Optional: explicitly provide credentials
    .projectId("your-google-cloud-project-id")
    .location("us-central1")
    .modelName("gemini-2.5-flash")
    .build();
```

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
    .thinkingLevel("LOW")
    .listeners(...)
    .build();
```

### Advanced: customizing the `GenerateContentConfig`

The builder methods cover the most common options. To set an option of the underlying Google Gen AI Java SDK
that is not (yet) exposed by a builder method, register a `generateContentConfigCustomizer`. It receives the
`GenerateContentConfig.Builder` after this integration has populated it (generation parameters, tools, system
instruction, etc.) and just before the config is built, so it can set additional options or override existing
ones while the per-request tools and system instruction are preserved.

```java
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .generateContentConfigCustomizer(config -> config.responseLogprobs(true).logprobs(5))
    .build();
```

This works the same way on `GoogleGenAiStreamingChatModel`.

## Request & Response Logging

You can enable request and response logging for debugging, troubleshooting, and audit purposes on `GoogleGenAiChatModel`, `GoogleGenAiStreamingChatModel`, `GoogleGenAiEmbeddingModel`, and `GoogleGenAiImageModel`.

To capture these logs, configure `.logRequests(true)`, `.logResponses(true)` (or both using `.logRequestsAndResponses(true)`) in your model builders.

```java
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .logRequests(true)
    .logResponses(true)
    // Or: .logRequestsAndResponses(true)
    .build();
```

### Logging Configuration Setup

All logging in the Google Gen AI integration module is routed through the standard **SLF4J** facade. To actually view the output, you must ensure that:
1. An SLF4J binding (implementation) is present in your dependencies.
2. The logging framework is configured to output logs under the `INFO` level for the package `dev.langchain4j.model.google.genai`.

Below are common setup patterns for popular logging environments:

#### 1. Setup using Logback

Add the Logback classic implementation to your project:

##### Maven
```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.8</version> <!-- or your preferred version -->
</dependency>
```

##### Gradle
```groovy
implementation 'ch.qos.logback:logback-classic:1.5.8'
```

Next, configure the logging level in your `src/main/resources/logback.xml` file. For example:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Configure the package specifically for Google Gen AI logging -->
    <logger name="dev.langchain4j.model.google.genai" level="INFO" />

    <root level="WARN">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

#### 2. Setup in Spring Boot Applications

Spring Boot automatically provides an SLF4J provider. Simply configure the logging level in your `application.properties` (or `application.yml` equivalent):

```properties
# Enable logging for Google Gen AI models
logging.level.dev.langchain4j.model.google.genai=INFO
```

#### 3. Setup with SLF4J Simple

If you are writing a script or a simple command-line application, you can use the lightweight `slf4j-simple` backend:

##### Maven
```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.13</version>
</dependency>
```

Configure SLF4J Simple via a system property when starting your application:

```bash
java -Dorg.slf4j.simpleLogger.log.dev.langchain4j.model.google.genai=INFO -jar app.jar
```

Alternatively, create a `simplelogger.properties` file in `src/main/resources/` containing:

```properties
org.slf4j.simpleLogger.log.dev.langchain4j.model.google.genai=info
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

## Cached Content Support

When working with very large context windows (like massive system prompts, large documents, or extensive codebases) that are reused across multiple requests, you can significantly reduce costs and latency by caching the content. 

Once you have created the cached content using the official Google Gen AI SDK or API, you can easily pass the unique cache identifier to the LangChain4j chat model builders:

```java
// Pass your cached content URI here
String cachedContentUri = "projects/123456/locations/us-central1/cachedContents/my-cached-content-789";

ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-pro")
    .cachedContent(cachedContentUri)
    .build();

// The model will automatically use the cached context!
String response = gemini.chat("Summarize the cached document in 3 bullet points.");
```

This feature is available on `GoogleGenAiChatModel`, `GoogleGenAiStreamingChatModel`, and `GoogleGenAiBatchChatModel`.

### Creating and managing caches

Instead of creating the cache out-of-band, you can create and manage it directly from LangChain4j with `GoogleGenAiCaches`, which wraps the SDK's cache lifecycle (create / get / list / update TTL / delete). The messages are cached using the same `GoogleGenAiContentMapper` the chat models use, so you stay in the LangChain4j `ChatMessage` domain.

```java
GoogleGenAiCaches caches = GoogleGenAiCaches.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .build();

// Cache a large, reusable context (a system instruction plus a long document)
CachedContent cache = caches.createCache(
    "gemini-2.5-flash",
    List.of(
        SystemMessage.from("You are a precise assistant answering questions about the attached document."),
        UserMessage.from(longDocumentText)),
    Duration.ofHours(1));

// Reuse it across many requests via cachedContent
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .cachedContent(cache.name().orElseThrow())
    .build();

String answer = gemini.chat("Summarize the cached document in 3 bullet points.");

// Manage the cache lifecycle
caches.updateCacheTtl(cache.name().orElseThrow(), Duration.ofHours(2));
caches.listCaches();
caches.deleteCache(cache.name().orElseThrow());
```

> Note: explicit context caching requires a paid tier; it is not available on the free tier.

## Thinking Models (Gemini 3.0+)

Gemini 3.0 models (like `gemini-3.0-pro` and `gemini-3.0-flash`) support advanced reasoning (thinking) capabilities. 
You can enable this by specifying a `thinkingLevel` during model configuration. The supported values are `"MINIMAL"`, `"LOW"`, `"MEDIUM"`, and `"HIGH"`:

```java
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-3.0-pro")
    .thinkingLevel("MEDIUM")
    .build();
```

> [!NOTE]
> Previously, thinking was configured using a token-based `thinkingBudget`. The `thinkingBudget` parameter is now considered legacy, though still supported. You cannot specify both `thinkingLevel` and `thinkingBudget` at the same time.

> [!TIP]
> The LangChain4j `google-genai` integration seamlessly manages the complex state required for multi-turn tool execution with thinking models. It automatically persists and injects the necessary hidden `thought_signature` tokens across conversation turns, ensuring robust and uninterrupted agentic workflows!

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

### Batching & Retries

When embedding multiple text segments (via `embedAll`), `GoogleGenAiEmbeddingModel` automatically manages batching and API request retries.

```java
EmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-embedding-2")
    .maxSegmentsPerBatch(100) // Default: 100. Sets maximum segments per batch request.
    .maxRetries(3)             // Default: 3. Automatically retries failed requests.
    .build();
```

#### Title-based Grouping Strategy
The official Google Gen AI Java SDK's `embedContent` API only supports a single common `title` per batch request. To handle this restriction cleanly and preserve document-level associations, `GoogleGenAiEmbeddingModel` implements a **group-by-title** batching strategy:

1. When `taskType` is set to `RETRIEVAL_DOCUMENT`, the model groups text segments by their document title (extracted from the segment's metadata using the key defined by `.titleMetadataKey(...)`, which defaults to `"title"`).
2. Segments sharing the same title are batched and sent together in a single API call.
3. Segments with different titles (or no title) are processed in separate, optimized batches.
4. The resulting embeddings are seamlessly reassembled and returned in their original order.

This maximizes API throughput without losing document metadata context or individual segment titles.


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

BatchResponse<ChatResponse> batchResponse = batchChatModel.submit(
    "My Batch Job",
    List.of(
        ChatRequest.builder().messages(UserMessage.from("What is 2+2?")).build(),
        ChatRequest.builder().messages(UserMessage.from("What is the capital of France?")).build()
    )
);

System.out.println("Batch Job ID: " + batchResponse.batchId());
```

You can then retrieve the status and results of the job using `batchChatModel.retrieve(batchResponse.batchId())`.

## Grounding Metadata

If you enable Google Search grounding or use a Vertex AI Search datastore, the Google Gen AI chat model exposes the native `GroundingMetadata` directly in the `ChatResponse`. You can retrieve it through the response metadata via the underlying raw `GenerateContentResponse`.

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

if (metadata.rawResponse() != null 
        && metadata.rawResponse().candidates() != null 
        && !metadata.rawResponse().candidates().isEmpty()) {
    var groundingMetadata = metadata.rawResponse().candidates().get(0).groundingMetadata();
    if (groundingMetadata != null && groundingMetadata.webSearchQueries() != null) {
        System.out.println("Search Queries: " + groundingMetadata.webSearchQueries());
    }
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

## File API

The Google Gen AI integration provides the `GoogleGenAiFiles` utility to upload and manage files on Google's servers. This is particularly useful for passing large multimodal inputs (like long videos, audio files, or extensive PDFs) that might exceed standard request limits.

```java
GoogleGenAiFiles fileApi = GoogleGenAiFiles.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .build();

String uploadedFileUri = fileApi.uploadFile(
    Paths.get("path/to/my-video.mp4"), 
    "video/mp4", 
    "My Video Demo"
);

// You can now use this URI in your chat requests
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

ChatResponse response = gemini.chat(ChatRequest.builder()
    .messages(UserMessage.from(
        VideoContent.from(uploadedFileUri, "video/mp4"),
        TextContent.from("What happens in this video?")
    ))
    .build());
```

## Multimodality (Audio, Video, PDF)

The integration fully supports LangChain4j's multimodal content types. The underlying `GoogleGenAiContentMapper` automatically converts them into the appropriate Gemini `Part` objects.

```java
ChatModel gemini = GoogleGenAiChatModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

ChatResponse response = gemini.chat(ChatRequest.builder()
    .messages(UserMessage.from(
        AudioContent.from("https://example.com/audio.mp3"),
        PdfFileContent.from("https://example.com/document.pdf"),
        TextContent.from("Summarize the document and the audio recording.")
    ))
    .build());
```

## Live API (Experimental)

The [Gemini Live API](https://ai.google.dev/gemini-api/docs/live) is a stateful, bidirectional, real-time interface: you stream text and audio to the model over a persistent connection, and it streams text or audio back with low latency. It is designed for natural, interruptible voice conversations.

Because a live session is not a request/response call, it does not fit the `ChatModel` contract. It is exposed as its own `GoogleGenAiLiveModel`, which opens a `GoogleGenAiLiveSession`.

> [!NOTE]
> The Live API is in preview and marked `@Experimental`; its surface may change in future releases.

### How it works

- **Open a session.** Configure a `GoogleGenAiLiveModel` with the builder, then call `connect(handler)` to open a `GoogleGenAiLiveSession`. The session is `AutoCloseable` — use it in a try-with-resources block so it always closes.
- **Send input.** Push input through the session: `sendText(...)`, `sendAudio(...)`, or `sendClientContent(...)` to seed conversation history.
- **Receive events.** Output is delivered to your `GoogleGenAiLiveResponseHandler`. Every method has a no-op default, so you override only the events you care about. The handler runs on the SDK's callback thread — don't block it; hand long-running work to your own executor.
- **One response modality per session** — either `TEXT` or `AUDIO`, set with `responseModalities(...)`. To get a text version of an audio response, enable `outputAudioTranscription`.
- **Audio format.** Input audio (`sendAudio`) must be raw 16 kHz, 16-bit little-endian PCM, sent in short chunks (about 20–40 ms). Output audio (`onAudio`) is 24 kHz, 16-bit little-endian PCM.

> [!NOTE]
> The current Gemini Developer API Live models are native-audio and require `responseModalities("AUDIO")`. Text-only output (`responseModalities("TEXT")`) is available with half-cascade Live models. The examples below use the native-audio path.

### Quickstart: talk to the model and get audio back

Send a text prompt and receive spoken audio plus its transcript. `onAudio` receives the raw PCM to play or buffer; `onOutputTranscription` receives the same response as text; `onTurnComplete` signals the model is done.

```java
GoogleGenAiLiveModel model = GoogleGenAiLiveModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash-native-audio-latest")
    .responseModalities("AUDIO")
    .voiceName("Puck")                 // pick a prebuilt voice
    .outputAudioTranscription(true)    // also receive the spoken response as text
    .build();

CountDownLatch turnComplete = new CountDownLatch(1);

try (GoogleGenAiLiveSession session = model.connect(new GoogleGenAiLiveResponseHandler() {
    @Override
    public void onAudio(byte[] pcm) {
        // play or buffer the 24 kHz PCM audio
    }

    @Override
    public void onOutputTranscription(String text) {
        System.out.print(text);
    }

    @Override
    public void onTurnComplete() {
        turnComplete.countDown();
    }
})) {
    session.sendText("Say hello in one short sentence.");
    turnComplete.await(30, TimeUnit.SECONDS);
}
```

### Streaming audio input

To hold a voice conversation, stream microphone audio to the model with `sendAudio`. Send small chunks (about 20–40 ms) as they are captured. When automatic voice activity detection is enabled (the default), call `sendAudioStreamEnd()` when the input pauses (for example, the user muted the microphone) to flush any buffered audio; you can resume by sending audio again.

```java
try (GoogleGenAiLiveSession session = model.connect(handler)) {
    for (byte[] chunk : microphoneChunks) {       // ~20–40 ms of 16 kHz PCM each
        session.sendAudio(chunk, "audio/pcm;rate=16000");
    }
    session.sendAudioStreamEnd();                  // input paused — flush buffered audio
}
```

Enable `inputAudioTranscription(true)` on the builder to also receive a transcript of the user's audio via `onInputTranscription`.

### Response events

Implement only the callbacks you need on `GoogleGenAiLiveResponseHandler`:

| Callback | Fires when |
| --- | --- |
| `onPartialText(String)` | A chunk of streamed text arrives (`TEXT` modality). |
| `onCompleteText(String)` | A turn's full text, concatenated from its chunks. |
| `onAudio(byte[])` | A chunk of output audio arrives (24 kHz PCM). |
| `onInputTranscription(String)` | A transcript of the user's input audio (when enabled). |
| `onOutputTranscription(String)` | A transcript of the model's output audio (when enabled). |
| `onGenerationComplete()` | The model finished generating the response (fires before `onTurnComplete`). |
| `onTurnComplete()` | The model finished its turn. |
| `onInterrupted()` | The user interrupted the model (barge-in) — stop and discard queued audio. |
| `onUsageMetadata(TokenUsage)` | Cumulative token usage for the session. |
| `onGoAway(Duration)` | The server will soon close the connection. |
| `onSessionResumptionUpdate(String)` | A handle you can use to resume the session later. |
| `onError(Throwable)` | An error occurred while sending or receiving. |
| `onClose()` | The session was closed. |

### Advanced: passthrough configuration

The builder surfaces the common Live options as first-class methods. Any other `LiveConnectConfig` option — for example `sessionResumption`, `contextWindowCompression`, `mediaResolution`, `safetySettings` or `translationConfig` — can be supplied through the `liveConnectConfig(...)` escape hatch. The passed config is used as the base, and any first-class builder option takes precedence over the same field in it.

```java
LiveConnectConfig baseConfig = LiveConnectConfig.builder()
    .mediaResolution("MEDIA_RESOLUTION_LOW")
    .build();

GoogleGenAiLiveModel model = GoogleGenAiLiveModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash-native-audio-latest")
    .responseModalities("AUDIO")
    .liveConnectConfig(baseConfig)     // advanced options not surfaced as builder methods
    .build();
```

> [!NOTE]
> Session length is limited by the model (audio-only sessions to about 15 minutes, audio + video to about 2 minutes). For longer conversations, enable context window compression via the passthrough config. See the [Live API guide](https://ai.google.dev/gemini-api/docs/live) for details.

## Token Count Estimator

You can accurately estimate the number of tokens in your prompts and messages using the `GoogleGenAiTokenCountEstimator`, which uses the official SDK's counting endpoints.

```java
TokenCountEstimator estimator = GoogleGenAiTokenCountEstimator.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-2.5-flash")
    .build();

int tokenCount = estimator.estimateTokenCount("How many tokens is this sentence?");
System.out.println("Tokens: " + tokenCount);
```

## Model Catalog

You can query the list of available Gemini models programmatically using the `GoogleGenAiModelCatalog`. This is helpful for discovering model capabilities, context windows, and supported methods dynamically.

```java
GoogleGenAiModelCatalog catalog = GoogleGenAiModelCatalog.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .build();

List<Model> availableModels = catalog.listModels();
availableModels.forEach(model -> {
    System.out.println("Model Name: " + model.name());
    System.out.println("Supported Generation Methods: " + model.supportedGenerationMethods());
});
```
