# Google Gen AI Embeddings (Experimental)

https://github.com/googleapis/java-genai

This integration uses the official Google Gen AI SDK for Java (`com.google.genai:google-genai`). It is marked
**Experimental**: the API and implementation may change in future releases.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-genai</artifactId>
    <version>1.17.2-beta27</version>
</dependency>
```

## API Key

Get an API key for free here: https://ai.google.dev/gemini-api/docs/api-key .

## Models available

See the [available embedding models](https://ai.google.dev/gemini-api/docs/embeddings#model-versions), for
example:

* `gemini-embedding-001` — text-only; supports task types and output dimensionality (128–3072).
* `gemini-embedding-2` — natively multimodal; does not use the task type parameter (see
  [Google AI Gemini Embeddings](/integrations/embedding-models/google-ai-gemini) for how task instructions work
  with Gemini Embedding 2).

## GoogleGenAiEmbeddingModel

### Basic Usage

```java
EmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-embedding-001")
    .build();

Response<Embedding> response = embeddingModel.embed("Hello, world!");
Embedding embedding = response.content();
```

### Configuring the Embedding Model

```java
EmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-embedding-001")
    .taskType(GoogleGenAiEmbeddingModel.TaskTypeEnum.RETRIEVAL_DOCUMENT) // default task type
    .outputDimensionality(768)      // reduce the embedding size (for models that support it)
    .titleMetadataKey("title")      // metadata key used as the document title for RETRIEVAL_DOCUMENT
    .maxRetries(3)
    .timeout(Duration.ofSeconds(30))
    .build();
```

## Request/response API and capabilities

Besides the convenience methods and the builder-level `taskType(...)`, `GoogleGenAiEmbeddingModel` supports the
request/response API with per-call parameters:

- **Input type**: `EmbeddingInputType.QUERY` / `DOCUMENT` is mapped to the SDK's `RETRIEVAL_QUERY` /
  `RETRIEVAL_DOCUMENT` task type, so you can embed queries and documents differently without configuring two
  model instances. (This applies to models that support task types, such as `gemini-embedding-001`.)
- **Dimensions**: a per-call `dimensions(...)` overrides the builder's `outputDimensionality`, for models that
  support reducing the output size.
- **Multimodal** (`gemini-embedding-2`): natively embeds interleaved text + image into a single embedding.
  Earlier models (e.g. `gemini-embedding-001`) are text-only. Images must be provided as base64 (`ImageContent`).
- **Listeners**: configure via `GoogleGenAiEmbeddingModel.builder().listeners(...)` to observe requests,
  responses, and errors.

```java
EmbeddingResponse response = embeddingModel.embed(EmbeddingRequest.builder()
    .input("What is the capital of France?")
    .inputType(EmbeddingInputType.QUERY) // embed as a query
    .dimensions(256)                     // reduce output dimensionality
    .build());

List<Embedding> embeddings = response.embeddings();
```

Multimodal example (Gemini Embedding 2 — text and image fused into one embedding):

```java
EmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
    .modelName("gemini-embedding-2")
    .build();

EmbeddingResponse response = embeddingModel.embed(EmbeddingRequest.builder()
    .input(TextContent.from("a photo of a cat"), ImageContent.from(base64Image, "image/png"))
    .build());

Embedding embedding = response.embeddings().get(0);
```

See [Embedding Model](/tutorials/rag#embedding-model) for the request/response API, and
[Observability](/tutorials/observability) for listeners.

## Learn more

For more details on the Gemini embedding models, see the
[documentation](https://ai.google.dev/gemini-api/docs/embeddings).
