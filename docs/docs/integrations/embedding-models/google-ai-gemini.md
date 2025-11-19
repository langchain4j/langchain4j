# Google AI Gemini

https://ai.google.dev/gemini-api/docs/embeddings

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-ai-gemini</artifactId>
    <version>1.8.0</version>
</dependency>
```

## API Key

Get an API key for free here: https://ai.google.dev/gemini-api/docs/api-key .

## Models available

Check the list of [available models](https://ai.google.dev/gemini-api/docs/embeddings#model-versions) in the documentation.

* `gemini-embedding-001`
  * Input token limit: 2,048
  * Output dimension size: Flexible, supports: 128 - 3072, Recommended: 768, 1536, 3072

## GoogleAiEmbeddingModel

The `GoogleAiEmbeddingModel` allows you to generate embeddings from text using Google AI Gemini's embedding models.

### Basic Usage

```java
EmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-embedding-001")
    .build();

Response<Embedding> response = embeddingModel.embed("Hello, world!");
Embedding embedding = response.content();
```

### Embedding Multiple Texts

```java
List<TextSegment> segments = List.of(
    TextSegment.from("First document"),
    TextSegment.from("Second document"),
    TextSegment.from("Third document")
);

Response<List<Embedding>> response = embeddingModel.embedAll(segments);
List<Embedding> embeddings = response.content();
```

### Configuring the Embedding Model

```java
EmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-embedding-001")
    .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
    .outputDimensionality(768)
    .titleMetadataKey("title")
    .maxRetries(3)
    .timeout(Duration.ofSeconds(30))
    .logRequestsAndResponses(true)
    .build();
```

### Task Types

The `taskType` parameter optimizes the embedding for specific use cases:

- `RETRIEVAL_QUERY`: For search queries
- `RETRIEVAL_DOCUMENT`: For documents to be retrieved (default for document indexing)
- `SEMANTIC_SIMILARITY`: For measuring text similarity
- `CLASSIFICATION`: For text classification tasks
- `CLUSTERING`: For grouping similar texts
- `QUESTION_ANSWERING`: For Q&A systems
- `FACT_VERIFICATION`: For fact-checking applications

### Using Metadata for Document Titles

When using `TaskType.RETRIEVAL_DOCUMENT`, you can provide document titles via metadata:

```java
EmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-embedding-001")
    .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
    .titleMetadataKey("title") // defaults to "title"
    .build();

TextSegment segment = TextSegment.from(
    "This is the document content",
    Metadata.from("title", "My Document Title")
);

Response<Embedding> response = embeddingModel.embed(segment);
```

### Output Dimensionality

You can specify the output dimensionality to reduce the embedding size:

```java
EmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-embedding-001")
    .outputDimensionality(256) // Reduce from default 768 dimensions
    .build();
```

### Batch Processing

The model automatically batches requests when embedding multiple segments, with a maximum of 100 segments per batch for optimal performance.
**Note:** This is not the discounted batch API, instead this is a convenience method for processing multiple Embeddings.
