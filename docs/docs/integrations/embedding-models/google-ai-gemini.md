# Google AI Gemini Embeddings

https://ai.google.dev/gemini-api/docs/embeddings

## Table of Contents

- [Maven Dependency](#maven-dependency)
- [API Key](#api-key)
- [Models Available](#models-available)
- [GoogleAiEmbeddingModel](#googleaiembeddingmodel)
    - [Basic Usage](#basic-usage)
    - [Embedding Multiple Texts](#embedding-multiple-texts)
    - [Configuring the Embedding Model](#configuring-the-embedding-model)
    - [Task Types](#task-types)
    - [Using Metadata for Document Titles](#using-metadata-for-document-titles)
    - [Output Dimensionality](#output-dimensionality)
    - [Batch Processing](#batch-processing)
- [Batch Embedding Processing](#batch-embedding-processing)
    - [GoogleAiGeminiBatchEmbeddingModel](#googleaigeminibatchembeddingmodel)
    - [Creating Batch Embedding Jobs](#creating-batch-embedding-jobs)
    - [Handling Batch Responses](#handling-batch-responses)
    - [Polling for Results](#polling-for-results)
    - [Managing Batch Jobs](#managing-batch-jobs)
    - [File-Based Batch Processing](#file-based-batch-processing)

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-ai-gemini</artifactId>
    <version>1.11.0</version>
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

**Note:** This is not the discounted batch API, instead this is a convenience method for processing multiple embeddings.

## Batch Embedding Processing

The `GoogleAiGeminiBatchEmbeddingModel` provides an interface for processing large volumes of embedding requests asynchronously at a reduced cost (50% of standard pricing). It is ideal for non-urgent, large-scale embedding tasks with a 24-hour turnaround SLO.

### Creating Batch Embedding Jobs

**Inline batch creation:**

```java
GoogleAiGeminiBatchEmbeddingModel batchModel = GoogleAiGeminiBatchEmbeddingModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-embedding-001")
    .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
    .outputDimensionality(768)
    .build();

// Create batch of text segments
List<TextSegment> segments = List.of(
    TextSegment.from("First document to embed"),
    TextSegment.from("Second document to embed"),
    TextSegment.from("Third document to embed")
);

// Submit the batch
BatchResponse<Embedding> response = batchModel.createBatchInline(
    "Document Embeddings Batch",  // display name
    0L,                            // priority (optional, defaults to 0)
    segments
);
```

**File-based batch creation:**

For larger batches, you can create a batch from an uploaded file:

```java
// First, upload a file with batch requests
GeminiFiles filesApi = GeminiFiles.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .build();

GeminiFile uploadedFile = filesApi.uploadFile(
    Paths.get("batch_embeddings.jsonl"),
    "Batch Embedding Requests"
);

// Wait for file to be active
while (uploadedFile.isProcessing()) {
    Thread.sleep(1000);
    uploadedFile = filesApi.getMetadata(uploadedFile.name());
}

// Create batch from file
BatchResponse<Embedding> response = batchModel.createBatchFromFile(
    "My Embedding Batch Job",
    uploadedFile
);
```

### Handling Batch Responses

The `BatchResponse` is a sealed interface with three possible states:

```java
BatchResponse<Embedding> response = batchModel.createBatchInline("My Batch", null, segments);

switch (response) {
    case BatchIncomplete incomplete -> {
        System.out.println("Batch is " + incomplete.state());
        System.out.println("Batch name: " + incomplete.batchName().value());
    }
    case BatchSuccess success -> {
        System.out.println("Batch completed successfully!");
        for (Embedding embedding : success.responses()) {
            System.out.println("Embedding dimensions: " + embedding.dimension());
        }
    }
    case BatchError error -> {
        System.err.println("Batch failed: " + error.message());
        System.err.println("Error code: " + error.code());
        System.err.println("State: " + error.state());
    }
}
```

### Polling for Results

Since batch processing is asynchronous, you need to poll for results:

```java
BatchResponse<Embedding> initialResponse = batchModel.createBatchInline(
    "My Batch",
    null,
    segments
);

// Extract the batch name for polling
BatchName batchName = switch (initialResponse) {
    case BatchIncomplete incomplete -> incomplete.batchName();
    case BatchSuccess success -> success.batchName();
    case BatchError error -> throw new RuntimeException("Batch creation failed");
};

// Poll until completion
BatchResponse<Embedding> result;
do {
    Thread.sleep(5000); // Wait 5 seconds between polls
    result = batchModel.retrieveBatchResults(batchName);
} while (result instanceof BatchIncomplete);

// Process final result
if (result instanceof BatchSuccess success) {
    List<Embedding> embeddings = success.responses();
    System.out.println("Generated " + embeddings.size() + " embeddings");
} else if (result instanceof BatchError error) {
    System.err.println("Batch failed: " + error.message());
}
```

### Managing Batch Jobs

**Cancel a batch job:**

```java
BatchName batchName = // ... obtained from createBatchInline or createBatchFromFile

try {
    batchModel.cancelBatchJob(batchName);
    System.out.println("Batch cancelled successfully");
} catch (HttpException e) {
    System.err.println("Failed to cancel batch: " + e.getMessage());
}
```

**Delete a batch job:**

```java
batchModel.deleteBatchJob(batchName);
System.out.println("Batch deleted successfully");
```

**List batch jobs:**

```java
// List first page of batch jobs
BatchList<Embedding> batchList = batchModel.listBatchJobs(10, null);

for (BatchResponse<Embedding> batch : batchList.batches()) {
    System.out.println("Batch: " + batch);
}

// Get next page if available
if (batchList.nextPageToken() != null) {
    BatchList<Embedding> nextPage = batchModel.listBatchJobs(10, batchList.nextPageToken());
}
```

### File-Based Batch Processing

For advanced use cases, you can write batch requests to a JSONL file and upload it:

```java
// Create a JSONL file with batch requests
Path batchFile = Files.createTempFile("batch", ".jsonl");

try (JsonLinesWriter writer = new StreamingJsonLinesWriter(batchFile)) {
    List<BatchFileRequest<TextSegment>> fileRequests = List.of(
        new BatchFileRequest<>("segment-1", TextSegment.from("First document")),
        new BatchFileRequest<>("segment-2", TextSegment.from("Second document")),
        new BatchFileRequest<>("segment-3", TextSegment.from("Third document"))
    );
    
    batchModel.writeBatchToFile(writer, fileRequests);
}

// Upload the file
GeminiFiles filesApi = GeminiFiles.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .build();

GeminiFile uploadedFile = filesApi.uploadFile(batchFile, "Batch Embedding Requests");

// Create batch from file
BatchResponse<Embedding> response = batchModel.createBatchFromFile(
    "File-Based Embedding Batch",
    uploadedFile
);
```

### Using Metadata with Batch Embeddings

When using `TaskType.RETRIEVAL_DOCUMENT`, you can include document titles via metadata:

```java
GoogleAiGeminiBatchEmbeddingModel batchModel = GoogleAiGeminiBatchEmbeddingModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-embedding-001")
    .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
    .titleMetadataKey("title")
    .build();

List<TextSegment> segments = List.of(
    TextSegment.from(
        "Content of first document",
        Metadata.from("title", "First Document Title")
    ),
    TextSegment.from(
        "Content of second document",
        Metadata.from("title", "Second Document Title")
    )
);

BatchResponse<Embedding> response = batchModel.createBatchInline(
    "Documents with Titles",
    null,
    segments
);
```

### Configuration

The `GoogleAiGeminiBatchEmbeddingModel` supports the same configuration options as `GoogleAiEmbeddingModel`:

```java
GoogleAiGeminiBatchEmbeddingModel batchModel = GoogleAiGeminiBatchEmbeddingModel.builder()
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

### Important Constraints

- **Size Limit**: The inline API supports a total request size of 20 MB or under
- **Batch Size**: Maximum of 100 segments per batch for optimal performance
- **Cost**: Batch processing offers 50% cost reduction compared to real-time requests
- **Turnaround**: 24-hour SLO, though completion is often much quicker
- **Use Cases**: Best for large-scale embedding generation for document indexing or semantic search

### Example: Complete Workflow

```java
GoogleAiGeminiBatchEmbeddingModel batchModel = GoogleAiGeminiBatchEmbeddingModel.builder()
    .apiKey(System.getenv("GEMINI_AI_KEY"))
    .modelName("gemini-embedding-001")
    .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
    .outputDimensionality(768)
    .build();

// Prepare batch of text segments
List<TextSegment> segments = new ArrayList<>();
for (int i = 0; i < 500; i++) {
    segments.add(TextSegment.from(
        "Document content #" + i,
        Metadata.from("title", "Document " + i)
    ));
}

// Submit batch
BatchResponse<Embedding> response = batchModel.createBatchInline(
    "Large Document Collection",
    0L,
    segments
);

// Get batch name
BatchName batchName = switch (response) {
    case BatchIncomplete incomplete -> incomplete.batchName();
    case BatchSuccess success -> success.batchName();
    case BatchError error -> throw new RuntimeException("Failed: " + error.message());
};

// Poll for completion
BatchResponse<Embedding> finalResult;
int attempts = 0;
int maxAttempts = 720; // 1 hour with 5-second intervals

do {
    if (attempts++ >= maxAttempts) {
        throw new RuntimeException("Batch processing timeout");
    }
    Thread.sleep(5000);
    finalResult = batchModel.retrieveBatchResults(batchName);
    
    if (finalResult instanceof BatchIncomplete incomplete) {
        System.out.println("Status: " + incomplete.state());
    }
} while (finalResult instanceof BatchIncomplete);

// Process results
if (finalResult instanceof BatchSuccess success) {
    List<Embedding> embeddings = success.responses();
    System.out.println("Generated " + embeddings.size() + " embeddings");
    
    // Store embeddings in your vector database
    for (int i = 0; i < embeddings.size(); i++) {
        Embedding embedding = embeddings.get(i);
        System.out.println("Embedding " + i + " has " + embedding.dimension() + " dimensions");
        // vectorStore.add(embedding, segments.get(i));
    }
} else if (finalResult instanceof BatchError error) {
    System.err.println("Batch failed: " + error.message());
}
```

## Learn more

If you're interested in learning more about the Google AI Gemini embedding models, please have a look at the
[documentation](https://ai.google.dev/gemini-api/docs/embeddings).
