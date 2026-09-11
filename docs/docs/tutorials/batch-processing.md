---
sidebar_position: 15
---

# Batch Processing

Batch processing lets you submit many requests to a model at once and have them executed
asynchronously in the background, rather than making one request at a time and waiting for each
result synchronously.

Instead of sending, for example, 1000 individual chat requests one by one, you group them into a
single **batch**. The provider queues the batch, processes it behind the scenes, and you poll for
the results later, often within a defined Service Level Objective (SLO) such as 24 hours.

## How Batching Works

While the exact mechanics differ from provider to provider, the typical flow looks the same:

1. **Build a batch model** - construct one of the `*Batch*Model` implementations (e.g.
   `GoogleAiGeminiBatchChatModel`).
2. **Submit a batch** - hand the model a list of requests (chat messages, text segments, or image
   prompts). The provider returns a `BatchResponse` with a `batchId` and an initial `state`.
3. **Poll for completion** - the batch starts out in a non-terminal state (`PENDING` or `RUNNING`).
   You repeatedly call `retrieve(batchId)` until `state().isTerminal()` is true.
4. **Process the results** - once the batch `SUCCEEDED`, iterate the responses. Each request maps to
   a result in order, and you can also see which individual requests failed.
5. **Manage (optional)** - cancel, delete, or list batch jobs as needed.

Typically, all requests within a single batch must use the **same model**.

## Why Use Batch Processing?

Batching is ideal for **large-scale, non-urgent** workloads where a few minutes to a day of latency
is acceptable. It is the wrong tool when you need an answer immediately for a single prompt.

### Pros and Cons

| Pros                                                                                                           | Cons                                                                                                                              |
|----------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| **Lower cost** - providers typically discount batch requests (Most providers offer ~50% off standard pricing). | **Latency** - results are not immediate; you must poll and wait (up to a 24-hour SLO).                                            |
| **Higher throughput** - process thousands of requests without hitting per-request rate limits.                 | **Asynchronous complexity** - you need to handle polling, terminal states, and job lifecycle management.                          |
| **Retries handled by the provider** - transient errors are retried for you.                                    | **Partial failures** - a "succeeded" batch can still contain some failed individual requests you must inspect.                    |
| **Simpler orchestration for bulk jobs** - reason about one job instead of thousands of calls.                  | **Not for interactive use** - batch results are unusable for real-time, user-facing responses.                                    |
| **Off-peak scheduling** - many providers process batches when demand is lower, smoothing load.                 | **Stricter size limits per batch** (e.g. 20 MB inline for Gemini) require chunking very large workloads or using file upload APIs |

### Good Use Cases vs. Bad Use Cases

- **Good**: backfilling embeddings for a knowledge base, evaluating a model against thousands of
  test cases, mass-translating documents, generating many summary or classification outputs,
  creating multiple images for a catalog.
- **Bad**: a live chat assistant, any request where the user is waiting on a response, or small
  one-off calls where you only need a single answer.

## Provider Support

Batch processing is supported across several LangChain4j providers. Each exposes the generic
`BatchChatModel`, `BatchEmbeddingModel`, and `BatchImageModel` interfaces, so the code you write is
largely portable:

- **OpenAI** and **OpenAI Official** - support OpenAI's Batch API.
- **Anthropic** - supports the Message Batches API via `AnthropicBatchChatModel`.
- **Mistral** - supports the Mistral Batch API via `MistralAiBatchChatModel`.
- **Google AI Gemini** - the `GoogleAi*Batch*` models (Google AI Studio / AI Gemini key).
- **Google Gen AI** - the `GoogleGenAi*Batch*` models (the newer Google Gen AI SDK).

## Examples

The following examples walk through the full batch lifecycle for each request type:

- [Chat Batching (Text)](#example-chat-batching-text) - submitting and polling a simple chat batch.
- [Embedding Batching](#example-embedding-batching) - bulk embedding of many text segments.
- [Image Batching](#example-image-batching) - generating many images from a batch of prompts.
- [Larger Batches and File-Based Input](#larger-batches-and-file-based-input) - writing requests to
  a JSONL file for very large workloads.
- [Listing Batches (Pagination)](#example-listing-batches-pagination) - paging through all batch jobs
  with `BatchPage` and `BatchPagination`.
- [Cleaning Up Batches](#cleaning-up-batches) - deleting finished batches to free up limits.

## Example: Chat Batching (Text)

Here we build an example model and batch a few simple chat questions. The pattern is identical for
OpenAI, OpenAI Official, and Anthropic - only the builder class (and model name) changes.

```java
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel;

// Build the batch chat model - Swap the class and model name for other providers.
GoogleAiGeminiBatchChatModel batchChatModel = GoogleAiGeminiBatchChatModel.builder()
        .apiKey(System.getenv("GEMINI_AI_KEY"))
        .modelName("gemini-2.5-flash")
        .build();

// 1. Build a list of chat requests
List<ChatRequest> requests = List.of(
        ChatRequest.builder().messages(UserMessage.from("What is the capital of France?")).build(),
        ChatRequest.builder().messages(UserMessage.from("What is the capital of Germany?")).build(),
        ChatRequest.builder().messages(UserMessage.from("What is the capital of Italy?")).build());

// 2. Submit the batch
BatchResponse<ChatResponse> response = batchChatModel.submit(new BatchRequest<>(requests));
String batchId = response.batchId();
System.out.println("Batch ID: " + batchId);

// 3. Poll until the batch reaches a terminal state
while (!response.state().isTerminal()) {
    Thread.sleep(5000); // wait 5 seconds between polls
    response = batchChatModel.retrieve(batchId);
}

// 4. Process the results
if (response.state() == BatchState.SUCCEEDED) {
    for (ChatResponse chatResponse : response.responses()) {
        System.out.println(chatResponse.aiMessage().text());
    }
    if (!response.errors().isEmpty()) {
        System.err.println("Some requests failed: " + response.errors());
    }
} else {
    System.err.println("Batch did not succeed: " + response.state());
}

// 5. Clean up the batch once you are done with it
batchChatModel.deleteBatchJob(batchId);
```

For provider-specific options (such as a Gemini display name or priority), pass a provider-specific request wrapper
instead of the generic `BatchRequest`. 

## Example: Embedding Batching

For bulk embedding workloads (e.g. backfilling a vector store), submit a batch of `TextSegment` objects.
Each successful result is a `Response<Embedding>` wrapping the computed vector.

```java
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchEmbeddingModel;
import dev.langchain4j.model.output.Response;

// Build the batch embedding model
GoogleAiGeminiBatchEmbeddingModel batchEmbeddingModel = GoogleAiGeminiBatchEmbeddingModel.builder()
        .apiKey(System.getenv("GEMINI_AI_KEY"))
        .modelName("gemini-embedding-001")
        .build();

// 1. Prepare the text segments you want to embed
List<TextSegment> segments = List.of(
        TextSegment.from("LangChain4j simplifies working with LLMs in Java."),
        TextSegment.from("Batch processing cuts costs for bulk embedding jobs."),
        TextSegment.from("Embeddings capture the meaning of a piece of text."));

// 2. Submit the batch
BatchResponse<Response<Embedding>> response =
        batchEmbeddingModel.submit(new BatchRequest<>(segments));
String batchId = response.batchId();

// 3. Poll for completion
while (!response.state().isTerminal()) {
    Thread.sleep(5000);
    response = batchEmbeddingModel.retrieve(batchId);
}

// 4. Collect the embeddings. results() preserves the order of the submitted segments,
// so the i-th result corresponds to the i-th TextSegment.
for (Response<Embedding> embeddingResponse : response.responses()) {
    Embedding embedding = embeddingResponse.content();
    System.out.println("Embedding dimension: " + embedding.dimension());
}
```

## Example: Image Batching

Batch generation is useful when you need a large number of images at once, such as generating
thumbnails or asset variations. Prompts are submitted as plain `String` objects via
`BatchImageModel`, and each successful result is a `Response<Image>`.

```java
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchImageModel;
import dev.langchain4j.model.output.Response;

// Build the batch image generation model
GoogleAiGeminiBatchImageModel batchImageModel = GoogleAiGeminiBatchImageModel.builder()
        .apiKey(System.getenv("GEMINI_AI_KEY"))
        .modelName("gemini-2.5-flash-image")
        .aspectRatio("16:9")
        .build();

// 1. Provide the prompts for the images to generate
List<String> prompts = List.of(
        "A serene mountain landscape at sunset",
        "A futuristic cityscape at night",
        "A minimalist logo for a coffee brand");

// 2. Submit the batch
BatchResponse<Response<Image>> response = batchImageModel.submit(new BatchRequest<>(prompts));
String batchId = response.batchId();

// 3. Poll for completion
while (!response.state().isTerminal()) {
    Thread.sleep(5000);
    response = batchImageModel.retrieve(batchId);
}

// 4. Save each generated image
if (response.state() == BatchState.SUCCEEDED) {
    response.responses().stream()
            .filter(res -> res.content() != null && res.content().url() != null)
            .forEach(res -> System.out.println("Image URL: " + res.content().url()));
}
```

## Larger Batches and File-Based Input

The inline API used above is convenient but limited in size (For example Gemini only allows up to ~20 MB inline).
For very large workloads you can write requests to a JSONL file, upload it, and create a batch from
that file:

```java
import dev.langchain4j.model.batch.BatchFileRequest;
import dev.langchain4j.model.googleai.GeminiFiles;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriter;
import dev.langchain4j.model.googleai.jsonl.StreamingJsonLinesWriter;
import java.nio.file.Files;
import java.nio.file.Path;

// 1. Write the requests to a JSONL file
Path batchFile = Files.createTempFile("batch", ".jsonl");
try (JsonLinesWriter writer = new StreamingJsonLinesWriter(batchFile)) {
    batchChatModel.writeBatchToFile(writer, List.of(
            new BatchFileRequest<>("r1", ChatRequest.builder()
                    .messages(UserMessage.from("Question 1")).build()),
            new BatchFileRequest<>("r2", ChatRequest.builder()
                    .messages(UserMessage.from("Question 2")).build())));
}

// 2. Upload the file
GeminiFiles filesApi = GeminiFiles.builder()
        .apiKey(System.getenv("GEMINI_AI_KEY"))
        .build();
GeminiFiles.GeminiFile uploadedFile = filesApi.uploadFile(batchFile, "Batch Chat Requests");

// 3. Create a batch from the uploaded file
batchChatModel.submit("File-Based Chat Batch", uploadedFile);
```

## Example: Listing Batches (Pagination)

When you have submitted many batches, use `list(...)` to page through them instead of tracking every
`batchId` yourself. Each page is a `BatchPage<ChatResponse>` holding the `batches()` on that page and
a `nextPageToken()` if there are more pages to fetch.

```java
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.chat.response.ChatResponse;

// Fetch the first page (up to 10 batches per page)
BatchPage<ChatResponse> page = batchChatModel.list(new BatchPagination(10, null));

for (BatchResponse<ChatResponse> batch : page.batches()) {
    System.out.println("Batch: " + batch.batchId() + " - " + batch.state());
}

// If there are more pages, fetch them until nextPageToken() is null
String nextPageToken = page.nextPageToken();
while (nextPageToken != null) {
    page = batchChatModel.list(new BatchPagination(10, nextPageToken));
    for (BatchResponse<ChatResponse> batch : page.batches()) {
        System.out.println("Batch: " + batch.batchId() + " - " + batch.state());
    }
    nextPageToken = page.nextPageToken();
}
```

## Cleaning Up Batches

Once a batch has reached a terminal state, **delete it** so you do not hold onto it longer than
necessary. Providers differ in how many active batches you can have at once, and a batch that you
do not consume or delete simply sits around until the provider's expiry time passes, after which it
is reported as `BatchState.EXPIRED`. Deleting the job immediately after you have read its results:

- frees up your allowance of active/batchable jobs,
- avoids consumers counting stale batches toward provider limits,
- removes results you no longer need and stops the record lingering in `list(...)` output.

Deleting a job does **not** cancel it if it is still running: cancel first with `cancel(batchId)`, then delete. The 
exact delete method name varies slightly by provider, e.g. `deleteBatchJob(batchId)` on the Gemini batch models:

```java
// After the batch has SUCCEEDED and you have processed the results
batchChatModel.deleteBatchJob(batchId);
```

## Key API Concepts

- **`BatchRequest<T>`** wraps a list of requests to process together.
- **`BatchResponse<T>`** holds the `batchId()`, the current `state()`, and the per-request
  `results()`. Use `responses()` and `errors()` as convenience views.
- **`BatchState`** describes the job lifecycle: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`,
  `CANCELLED`, `EXPIRED`, `UNSPECIFIED`. Use `state().isTerminal()` to know when to stop polling.
- **`results()`** preserve submission order, so the i-th result corresponds to the i-th request -
  important for correlating outcomes back to their inputs.
- **`BatchPage<T>`** is a page of batch jobs returned by `list(...)`, holding the `batches()` on the
  current page and a `nextPageToken()` to fetch the next page (null when there are no more).
- **`BatchPagination`** configures a `list(...)` call with a `pageSize` and an optional
  `pageToken` for fetching a specific page.
