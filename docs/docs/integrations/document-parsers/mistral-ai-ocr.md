---
sidebar_position: 8
---

# Mistral AI OCR

[Mistral Document AI](https://docs.mistral.ai/api/endpoint/ocr) (also marketed as Mistral OCR) turns PDFs
and images into markdown, keeping the layout, the tables and the reading order of the original. It makes
scanned documents, which no local text extractor can handle, usable in an ingestion pipeline.

Unlike the local parsers, this one sends the content to Mistral and is billed per page.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-mistral-ai</artifactId>
    <version>1.19.0</version>
</dependency>
```

## Usage as a DocumentParser

```java
DocumentParser parser = MistralAiOcrDocumentParser.builder()
        .ocrModel(MistralAiOcrModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName("mistral-ocr-latest")
                .build())
        .build();

Document document = FileSystemDocumentLoader.loadDocument("/home/me/scan.pdf", parser);
```

`DocumentParser` receives a bare `InputStream` with no indication of its type, so the mime type has to be
stated up front. It defaults to `application/pdf`; use one parser instance per type when ingesting both
PDFs and images:

```java
DocumentParser imageParser = MistralAiOcrDocumentParser.builder()
        .ocrModel(ocrModel)
        .mimeType("image/png")
        .build();
```

## Usage as a model

`MistralAiOcrModel` can be used directly, which additionally allows the page structure to be kept. One
`Document` per page means a retrieved segment can be traced back to a page of the source document:

```java
MistralAiOcrModel model = MistralAiOcrModel.builder()
        .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
        .modelName("mistral-ocr-latest")
        .tableFormat(MistralAiOcrTableFormat.HTML)
        .extractHeader(true)
        .build();

List<Document> pages = model.parsePages(Files.readAllBytes(path), "application/pdf");
```

The model can also let the service fetch the document itself, which avoids sending the bytes:

```java
Document document = model.parseDocumentUrl("https://example.com/invoice.pdf");
Document receipt = model.parseImageUrl("https://example.com/receipt.png");
```

A document from which nothing could be recognized raises `BlankDocumentException`, matching the behaviour
of the other parsers.

### Metadata

| Key                   | Set by                        | Meaning                                     |
|-----------------------|-------------------------------|---------------------------------------------|
| `ocr_model`           | all methods                   | the model version that produced the result  |
| `pages_processed`     | all methods                   | how many pages the service billed           |
| `page_count`          | all methods                   | how many pages the result consists of       |
| `page_index`          | `parsePages`                  | zero-based index of the page in the source  |
| `page_header`         | `parsePages`                  | header of the page, if extraction is on     |
| `page_footer`         | `parsePages`                  | footer of the page, if extraction is on     |
| `document_size_bytes` | `MistralAiOcrDocumentParser`  | size of the parsed input                    |

### Headers and footers

By default the header and footer of a page are part of the recognized content. `extractHeader` and
`extractFooter` make the service report them separately instead, which `parsePages` then exposes as
`page_header` and `page_footer` metadata:

```java
MistralAiOcrModel model = MistralAiOcrModel.builder()
        .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
        .modelName("mistral-ocr-latest")
        .extractHeader(true)
        .extractFooter(true)
        .build();
```

The text of the returned documents is the same either way — the header and footer stay part of it. The
point of the options is that a header repeated on every page becomes identifiable, so it can be stripped
from the body before embedding without having to guess which line it was.

## Azure hosted deployments

Mistral Document AI is also offered through Azure AI Foundry. Such a deployment lives on its own host,
accepts the key in an `api-key` header and can take an `api-version` query parameter. All three are
ordinary builder settings, so no separate model is needed:

```java
MistralAiOcrModel model = MistralAiOcrModel.builder()
        .baseUrl("https://my-resource.services.ai.azure.com/providers/mistral/azure")
        .apiKey(key)
        .customHeaders(Map.of("api-key", key))
        .customQueryParams(Map.of("api-version", "2024-05-01-preview"))
        .modelName("mistral-document-ai-2512")
        .build();
```

Note that on Azure `modelName` is the name of the *deployment*, not the catalog name. For a standalone
serverless deployment the base URL ends in `/v1` instead:

```java
        .baseUrl("https://my-deployment.westus3.models.ai.azure.com/v1")
```

`customHeaders` and `customQueryParams` are available on the other Mistral models as well, so the same
approach works for chat and embeddings served from Azure.
