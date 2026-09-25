---
sidebar_position: 15
---

# Batch Processing

:::note
Experimental. The batch API is annotated `@Experimental` and may change in future releases.
:::

Batch processing lets you submit many requests to a model at once and have them executed
asynchronously in the background, rather than making one request at a time and waiting for each
result synchronously.

Instead of sending, for example, 1000 individual chat requests one by one, you group them into a
single **batch**. The provider queues the batch, processes it behind the scenes, and you poll for
the results later, often within a defined Service Level Objective (SLO) such as 24 hours.

## How Batching Works

While the exact mechanics differ from provider to provider, the typical flow looks the same:

1. **Build a batch model** - construct the batch model of a provider (e.g.
   `GoogleAiGeminiBatchChatModel` or `AnthropicBatchChatModel`).
2. **Submit a batch** - hand the model a `BatchRequest` with a list of requests (chat requests, text
   segments, or image prompts). The provider returns a `BatchResponse` with a `batchId` and an initial `state`.
3. **Poll for completion** - the batch starts out in a non-terminal state (`PENDING` or `RUNNING`).
   You repeatedly call `retrieve(batchId)` until `state().isTerminal()` is true.
4. **Process the results** - once the batch has `SUCCEEDED`, read its `results()`: one entry per request,
   in the order the requests were submitted, each either a success or a failure.
5. **Manage (optional)** - cancel a batch that is still in progress, or list your batches.

Most providers run all requests of a batch against a single model; Anthropic lets each request choose its own model.

## Why Use Batch Processing?

Batching is ideal for **large-scale, non-urgent** workloads where a few minutes to a day of latency
is acceptable. It is the wrong tool when you need an answer immediately for a single prompt.

### Pros and Cons

Pros:

- **Lower cost** - providers typically charge 50% of the standard price for batch requests.
- **Higher throughput** - most providers apply separate, higher rate limits to batch jobs than to regular requests.
- **Simpler orchestration for bulk jobs** - you track one batch instead of thousands of individual calls.

Cons:

- **Latency** - results are not immediate; you must poll and wait (typically up to 24 hours).
- **Asynchronous complexity** - you need to handle polling, terminal states, and job lifecycle management.
- **Partial failures** - a "succeeded" batch can still contain individual requests that failed, which you must inspect.
- **Not for interactive use** - batch results are unusable for real-time, user-facing responses.
- **Size limits** - providers limit the size of a batch (e.g. 20 MB of inline requests for Gemini), so very large
  workloads have to be split into several batches or submitted as a file where the provider supports it.

### Good Use Cases vs. Bad Use Cases

- **Good**: backfilling embeddings for a knowledge base, evaluating a model against thousands of
  test cases, mass-translating documents, generating many summary or classification outputs,
  creating multiple images for a catalog.
- **Bad**: a live chat assistant, any request where the user is waiting on a response, or small
  one-off calls where you only need a single answer.

## Provider Support

Batch models implement one of three provider-independent interfaces: `BatchChatModel`, `BatchEmbeddingModel`,
or `BatchImageModel`. Each of them offers the same four operations: `submit`, `retrieve`, `cancel`, and `list`.
Code written against these interfaces works with any provider that supports the same request type;
switching providers only means building a different model.

| Provider                                                                               | `BatchChatModel`               | `BatchEmbeddingModel`                                                                                             | `BatchImageModel`                                                                           |
|----------------------------------------------------------------------------------------|--------------------------------|-------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| [Anthropic](/integrations/language-models/anthropic#batch-api)                         | `AnthropicBatchChatModel`      |                                                                                                                   |                                                                                             |
| [Google AI Gemini](/integrations/language-models/google-ai-gemini#batch-processing)    | `GoogleAiGeminiBatchChatModel` | [`GoogleAiGeminiBatchEmbeddingModel`](/integrations/embedding-models/google-ai-gemini#batch-embedding-processing) | [`GoogleAiGeminiBatchImageModel`](/integrations/image-models/gemini#batch-image-generation) |
| [Google Gen AI](/integrations/language-models/google-genai#batch-api)                  | `GoogleGenAiBatchChatModel`    | `GoogleGenAiBatchEmbeddingModel`                                                                                  | `GoogleGenAiBatchImageModel`                                                                |
| [Mistral AI](/integrations/language-models/mistral-ai#batch-processing)                | `MistralAiBatchChatModel`      |                                                                                                                   |                                                                                             |
| [OpenAI Official SDK](/integrations/language-models/open-ai-official#batch-processing) | `OpenAiOfficialBatchChatModel` |                                                                                                                   |                                                                                             |

## Examples

The following examples walk through the full batch lifecycle for each request type. They use Google AI Gemini,
because it supports all three, but only the builder is provider-specific:

- [Chat Batching (Text)](#example-chat-batching-text) - submitting and polling a simple chat batch.
- [Embedding Batching](#example-embedding-batching) - bulk embedding of many text segments.
- [Image Batching](#example-image-batching) - generating many images from a batch of prompts.
- [Listing Batches (Pagination)](#example-listing-batches-pagination) - paging through all batch jobs
  with `BatchPage` and `BatchPagination`.
- [Cancelling a Batch](#cancelling-a-batch) - stopping a batch that is still in progress.

## Example: Chat Batching (Text)

Here we build a batch chat model and batch a few simple chat questions.

```java
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.BatchChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel;
import java.util.List;

// Build the batch chat model - swap the builder for another provider, the rest of the code stays the same
BatchChatModel batchChatModel = GoogleAiGeminiBatchChatModel.builder()
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
    Thread.sleep(30_000); // wait 30 seconds between polls
    response = batchChatModel.retrieve(batchId);
}

// 4. Process the results: the i-th result belongs to the i-th request
if (response.state() == BatchState.SUCCEEDED) {
    List<BatchItemResult<ChatResponse>> results = response.results();
    for (int i = 0; i < results.size(); i++) {
        BatchItemResult<ChatResponse> result = results.get(i);
        if (result.isSuccess()) {
            System.out.println("Request #" + i + ": " + result.response().aiMessage().text());
        } else {
            System.err.println("Request #" + i + " failed: " + result.error().message());
        }
    }
} else {
    System.err.println("Batch did not succeed: " + response.state());
}
```

If you only need the successful responses, `response.responses()` returns them without the failures,
and `response.errors()` returns only the failures. Neither tells you which request an entry belongs to.

## Example: Embedding Batching

For bulk embedding workloads (e.g. backfilling an embedding store), submit a batch of `TextSegment` objects.
Each successful result is a `Response<Embedding>` wrapping the computed vector.

```java
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.embedding.BatchEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;

// Build the batch embedding model
BatchEmbeddingModel batchEmbeddingModel = GoogleAiGeminiBatchEmbeddingModel.builder()
        .apiKey(System.getenv("GEMINI_AI_KEY"))
        .modelName("gemini-embedding-001")
        .build();

// 1. Prepare the text segments you want to embed
List<TextSegment> segments = List.of(
        TextSegment.from("LangChain4j simplifies working with LLMs in Java."),
        TextSegment.from("Batch processing cuts costs for bulk embedding jobs."),
        TextSegment.from("Embeddings capture the meaning of a piece of text."));

// 2. Submit the batch
BatchResponse<Response<Embedding>> response = batchEmbeddingModel.submit(new BatchRequest<>(segments));
String batchId = response.batchId();

// 3. Poll for completion
while (!response.state().isTerminal()) {
    Thread.sleep(30_000);
    response = batchEmbeddingModel.retrieve(batchId);
}

// 4. Match each embedding to its segment: the i-th result belongs to the i-th segment
if (response.state() == BatchState.SUCCEEDED) {
    List<BatchItemResult<Response<Embedding>>> results = response.results();
    for (int i = 0; i < results.size(); i++) {
        TextSegment segment = segments.get(i);
        BatchItemResult<Response<Embedding>> result = results.get(i);
        if (result.isSuccess()) {
            Embedding embedding = result.response().content();
            System.out.println(segment.text() + " -> dimension " + embedding.dimension());
        } else {
            System.err.println("Failed to embed '" + segment.text() + "': " + result.error().message());
        }
    }
}
```

## Example: Image Batching

Batch generation is useful when you need a large number of images at once, such as generating
thumbnails or asset variations. Prompts are submitted as plain `String` objects via
`BatchImageModel`, and each successful result is a `Response<Image>`.

```java
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchImageModel;
import dev.langchain4j.model.image.BatchImageModel;
import dev.langchain4j.model.output.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

// Build the batch image generation model
BatchImageModel batchImageModel = GoogleAiGeminiBatchImageModel.builder()
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
    Thread.sleep(30_000);
    response = batchImageModel.retrieve(batchId);
}

// 4. Save each generated image
if (response.state() == BatchState.SUCCEEDED) {
    List<BatchItemResult<Response<Image>>> results = response.results();
    for (int i = 0; i < results.size(); i++) {
        BatchItemResult<Response<Image>> result = results.get(i);
        if (result.isSuccess()) {
            Image image = result.response().content();
            Files.write(Path.of("image-" + i + ".png"), Base64.getDecoder().decode(image.base64Data()));
        } else {
            System.err.println("Prompt '" + prompts.get(i) + "' failed: " + result.error().message());
        }
    }
}
```

Depending on the provider, a generated `Image` contains either `base64Data()` or a `url()`.
Both Google AI Gemini and Google Gen AI return `base64Data()`.

## Example: Listing Batches (Pagination)

When you have submitted many batches, use `list(...)` to page through them instead of tracking every
`batchId` yourself. Each page is a `BatchPage` holding the `batches()` on that page and
a `nextPageToken()`, which is `null` on the last page.

```java
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.chat.response.ChatResponse;

String pageToken = null;
do {
    // Fetch up to 10 batches per page
    BatchPage<ChatResponse> page = batchChatModel.list(new BatchPagination(10, pageToken));
    for (BatchResponse<ChatResponse> batch : page.batches()) {
        System.out.println("Batch: " + batch.batchId() + " - " + batch.state());
    }
    pageToken = page.nextPageToken();
} while (pageToken != null);
```

## Cancelling a Batch

A batch that is still `PENDING` or `RUNNING` can be cancelled. It then ends in the `CANCELLED` state:

```java
batchChatModel.cancel(batchId);
```

Depending on the provider, a cancelled batch can still contain results for the requests that
completed before the cancellation took effect.

## Provider-Specific Features

Some features are not part of the provider-independent interfaces. To use them, work with the concrete
batch model class of the provider. They are described on the provider pages linked in [Provider Support](#provider-support):

- **File-based input** - Google AI Gemini and Google Gen AI can create a batch from an uploaded file,
  for workloads that are too large to submit inline.
- **Deleting batch jobs** - the Google AI Gemini and Google Gen AI batch models provide `deleteBatchJob(batchId)`
  to remove a finished batch job. Cancel a batch that is still running before deleting it.
- **Batch options** - for example a display name and priority for Google AI Gemini, or a completion window
  and batch metadata for the OpenAI Official SDK.

## Key API Concepts

- **`BatchChatModel`**, **`BatchEmbeddingModel`**, **`BatchImageModel`** are the provider-independent
  interfaces with the `submit`, `retrieve`, `cancel`, and `list` operations.
- **`BatchRequest<T>`** wraps a list of requests to process together.
- **`BatchResponse<T>`** holds the `batchId()`, the current `state()`, and the per-request
  `results()`. `responses()` and `errors()` are convenience views that contain only the successful
  responses or only the errors.
- **`BatchItemResult<T>`** is the outcome of a single request: either a success with a `response()`, or a failure
  with an `error()`. Check `isSuccess()` to tell them apart.
- **`BatchError`** describes why a request (or the whole batch) failed, with a `code()` and a `message()`.
- **`BatchState`** describes the job lifecycle: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`,
  `CANCELLED`, `EXPIRED`, `UNSPECIFIED`. Use `state().isTerminal()` to know when to stop polling.
- **`results()`** preserve submission order, so the i-th result corresponds to the i-th request -
  important for correlating outcomes back to their inputs.
- **`BatchPage<T>`** is a page of batch jobs returned by `list(...)`, holding the `batches()` on the
  current page and a `nextPageToken()` to fetch the next page (null when there are no more).
- **`BatchPagination`** configures a `list(...)` call with a `pageSize` and an optional
  `pageToken` for fetching a specific page.
