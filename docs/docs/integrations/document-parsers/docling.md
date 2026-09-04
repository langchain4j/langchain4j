---
sidebar_position: 7
---

# Docling

[Docling](https://docling.ai) is an IBM Research document processing engine that extracts text and structure from various document formats including PDF, DOCX, PPTX, and more. It provides advanced capabilities such as OCR, table extraction, and layout analysis.

This integration communicates with a running [docling-serve](https://github.com/docling-project/docling-serve) instance via REST API and is built using the [official Docling Java library](https://docling-project.github.io/docling-java/current/).


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-docling</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

This module depends on `docling-serve-api` (the interface) and includes `docling-serve-client` (the reference HTTP client) as an optional runtime dependency.

**If you are not using Spring Boot or Quarkus** (which may provide their own `DoclingServeApi` implementation), you must also add the reference client explicitly:

```xml
<dependency>
    <groupId>ai.docling</groupId>
    <artifactId>docling-serve-client</artifactId>
    <version>0.6.4</version>
</dependency>
```

Frameworks such as [Quarkus](https://quarkus.io) or [Spring Boot](https://spring.io/projects/spring-boot) provide their own integrations with Docling. See the [Docling Java Documentation](https://docling-project.github.io/docling-java/dev/docling-serve/serve-client/#when-to-use-this-module) for how to wire in those specific implementations.


## Usage

Start a `docling-serve` instance (see [docling-serve docs](https://github.com/docling-project/docling-serve)), then build a `DoclingServeApi` client and pass it to the parser:

```java
DoclingServeApi api = DoclingServeApi.builder()
        .baseUrl("http://localhost:5001")
        .build();

DoclingDocumentParser parser = DoclingDocumentParser.builder()
        .doclingClient(api)
        .build();

Document document = parser.parse(inputStream);
String text = document.text();
```

To load documents from files, directories, URLs, or the classpath — and have
standard metadata such as `file_name`, `absolute_directory_path`, and `url`
populated automatically — use the loaders from the `langchain4j` module together
with the parser:

```java
Document fromFile = FileSystemDocumentLoader.loadDocument(Path.of("/tmp/report.pdf"), parser);
Document fromUrl = UrlDocumentLoader.load("https://example.com/report.pdf", parser);
```

### Choosing the Docling Operation

The operation performed by Docling — conversion, hierarchical chunking, or hybrid
chunking — is controlled by the [`DocumentRequest`](https://docling-project.github.io/docling-java/dev/docling-serve/serve-api/#requests-convertdocumentrequest)
template supplied to the builder. The template carries everything except the
document source: the parser injects the source of the document being parsed into a
fresh copy of the template for each call and routes it to the matching Docling
endpoint. If no template is supplied, a plain `ConvertDocumentRequest` is used.

To customize conversion, supply a `ConvertDocumentRequest` carrying your
`ConvertDocumentOptions`:

```java
ConvertDocumentOptions options = ConvertDocumentOptions.builder()
        // configure options here
        .build();

DoclingDocumentParser parser = DoclingDocumentParser.builder()
        .doclingClient(api)
        .documentRequest(ConvertDocumentRequest.builder()
                .options(options)
                .build())
        .build();
```

> The deprecated `Builder.options(ConvertDocumentOptions)` shortcut is still
> available and simply delegates to `documentRequest(...)`, but prefer passing a
> `ConvertDocumentRequest` so you can also choose the Docling operation.

To chunk the document instead of converting it, supply a
`HierarchicalChunkDocumentRequest` or a `HybridChunkDocumentRequest`:

```java
DoclingDocumentParser parser = DoclingDocumentParser.builder()
        .doclingClient(api)
        .documentRequest(HybridChunkDocumentRequest.builder().build())
        .build();
```

Any document source carried by the template is ignored — the parser injects the
source of the document being parsed. The template must describe an in-body
operation: a `BatchConvertDocumentRequest`, or a request carrying a non-in-body
target (for example a `ZipTarget` or `PresignedUrlTarget`), causes `build()` to
fail.

### Custom Text Extraction

By default, the parser extracts markdown content from a conversion response and
joins chunk text (separated by newlines) from a chunk response. You can customize
how text is extracted with two builder methods, each receiving the concrete
response type — no casts required:

- `documentTextExtractor(Function<InBodyConvertDocumentResponse, String>)` — used
  for conversion requests. Gives access to the converted document in various
  formats (markdown, HTML, text, doctags, JSON), conversion errors, processing
  time, and status information.
- `chunkTextExtractor(Function<ChunkDocumentResponse, String>)` — used for chunk
  requests. Gives access to the chunks and their metadata.

If you need control over the whole resulting `Document` — for example to attach
extra `Metadata` such as provenance derived from the structured response — use the
`Document`-returning variants instead:

- `documentExtractor(Function<InBodyConvertDocumentResponse, Document>)`
- `chunkExtractor(Function<ChunkDocumentResponse, Document>)`

For each response kind you set **either** the text extractor **or** the
`Document` extractor — the two variants of a pair are mutually exclusive, and
configuring both (`documentTextExtractor` + `documentExtractor`, or
`chunkTextExtractor` + `chunkExtractor`) makes `build()` throw an
`IllegalArgumentException`.

```java
DoclingDocumentParser parser = DoclingDocumentParser.builder()
        .doclingClient(api)
        .documentRequest(ConvertDocumentRequest.builder()
                .options(ConvertDocumentOptions.builder().toFormat(OutputFormat.JSON).build())
                .build())
        .documentExtractor(response -> {
            DoclingDocument doc = response.getDocument().getJsonContent();
            String fullText = buildFullText(doc);
            return Document.from(fullText, buildProvenanceMetadata(doc, fullText));
        })
        .build();
```

The parser always adds the `document_size_bytes` metadata entry on top of whatever
your extractor returns.

For example, to extract HTML content instead of markdown:

```java
DoclingDocumentParser parser = DoclingDocumentParser.builder()
        .doclingClient(api)
        .documentTextExtractor(response -> response.getDocument().getHtmlContent())
        .build();
```

Or to extract plain text:

```java
DoclingDocumentParser parser = DoclingDocumentParser.builder()
        .doclingClient(api)
        .documentTextExtractor(response -> response.getDocument().getTextContent())
        .build();
```

And to customize how chunks are joined:

```java
DoclingDocumentParser parser = DoclingDocumentParser.builder()
        .doclingClient(api)
        .documentRequest(HybridChunkDocumentRequest.builder().build())
        .chunkTextExtractor(response -> response.getChunks().stream()
                .map(Chunk::getText)
                .collect(Collectors.joining("\n\n")))
        .build();
```

### Asynchronous / Reactive Parsing

`DoclingDocumentParser.parse(InputStream)` satisfies the synchronous
`DocumentParser` contract by blocking on the underlying asynchronous call. For
reactive or non-blocking pipelines, use `parseAsync(InputStream)`, which returns a
`CompletionStage<Document>`:

```java
CompletionStage<Document> stage = parser.parseAsync(inputStream);
```

`parseAsync` offloads reading the input stream and preparing the request onto a
background thread (via `CompletableFuture.supplyAsync`, i.e. the common
`ForkJoinPool`), so it returns without blocking the calling thread. The parsed
`Document` — or any failure (a `null`/empty stream, a request-build error, or an
error from the Docling call) — is delivered through the returned stage: it
completes exceptionally rather than throwing, so reactive callers don't need a
surrounding `try/catch`.

This drops straight into a reactive type, for example Mutiny:

```java
Uni.createFrom().completionStage(() -> parser.parseAsync(inputStream));
```

> **Threading note.** `parseAsync` controls only where the stream is read; the
> Docling network call itself runs on threads owned by the Docling client. The
> reference `docling-serve-client` performs its HTTP and task polling on the common
> `ForkJoinPool` and exposes no executor to change that. If you need to control
> where the blocking network work runs (for example a dedicated pool or virtual
> threads), supply a custom
> [`DoclingRequestExecutor`](#customizing-how-docling-is-called) that invokes the
> client on your own executor.

### Customizing How Docling Is Called

By default the parser calls Docling's asynchronous convert/chunk endpoints
matching the request type. Those endpoints already submit a task, poll for
completion, and fetch the result — all on the common `ForkJoinPool`, so there is
no need for a custom executor just to get that task/queue behaviour. Supply a
`DoclingRequestExecutor` when you want to:

- add **retry or backoff** around the Docling call;
- run the blocking call on **your own executor** (a dedicated pool or virtual
  threads) instead of the common `ForkJoinPool`; or
- perform **fully custom orchestration** (for example driving the task/queue
  endpoints yourself with a custom poll cadence).

To retry the conversion once on failure:

```java
DoclingDocumentParser parser = DoclingDocumentParser.builder()
        .doclingClient(api)
        .requestExecutor((client, request) -> {
            ConvertDocumentRequest convertRequest = (ConvertDocumentRequest) request;
            return client.convertSourceAsync(convertRequest)
                    .exceptionallyCompose(error -> client.convertSourceAsync(convertRequest));
        })
        .build();
```

To run the blocking work on your own executor (here, virtual threads) — this uses
the *synchronous* `convertSource` so the network call runs on the supplied pool
rather than the common `ForkJoinPool`:

```java
Executor executor = Executors.newVirtualThreadPerTaskExecutor();

DoclingDocumentParser parser = DoclingDocumentParser.builder()
        .doclingClient(api)
        .requestExecutor((client, request) -> CompletableFuture.supplyAsync(
                () -> client.convertSource((ConvertDocumentRequest) request), executor))
        .build();
```

When a custom executor is supplied, the parser no longer restricts the request
type or target (the executor owns those semantics), so operations such as
`BatchConvertDocumentRequest` or non-in-body targets become available.

## APIs

- `DoclingDocumentParser`
- `DoclingRequestExecutor`


## Examples

- [DoclingDocumentParserTest](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-docling/src/test/java/dev/langchain4j/data/document/parser/docling/DoclingDocumentParserTest.java)
- [DoclingDocumentParserIT](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-docling/src/test/java/dev/langchain4j/data/document/parser/docling/DoclingDocumentParserIT.java)
