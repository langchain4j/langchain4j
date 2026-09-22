---
sidebar_position: 19
---

# Voyage AI

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-voyage-ai</artifactId>
    <version>1.20.0-beta30</version>
</dependency>
```

## APIs

- `VoyageAiEmbeddingModel`

## Capabilities

- **Multimodal** (`voyage-multimodal-3`, `voyage-multimodal-3.5` — auto-detected from the model name): embeds
  text and images into a shared vector space; interleaved text + image is fused into a single embedding.
  Provide image inputs as `ImageContent` (URL or base64) in an `EmbeddingRequest`.
- **Per-call parameters**: `input_type` (`EmbeddingInputType.QUERY` / `DOCUMENT`), and Voyage AI's own
  `truncation` and `encoding_format` via `VoyageAiEmbeddingRequestParameters`. A value set on the builder is
  the default for every call, and a value set on the request overrides it for that call. `truncation(false)`
  makes a call fail instead of shortening an input that exceeds the model's context. `encoding_format`
  (`"base64"`) only asks for a smaller response; the embeddings you get back are the same, and only the text
  models accept it, so a multimodal model rejects it.
- **Listeners**: configure via `VoyageAiEmbeddingModel.builder().listeners(...)`.

See [Embedding Model](/tutorials/rag#embedding-model) for the request/response API and multimodal usage.

## Examples

- [VoyageAiEmbeddingModelIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-voyage-ai/src/test/java/dev/langchain4j/model/voyageai/VoyageAiEmbeddingModelIT.java)
- [VoyageAiEmbeddingModelExample](https://github.com/langchain4j/langchain4j-examples/blob/main/voyage-ai-examples/src/main/java/VoyageAiEmbeddingModelExample.java)
