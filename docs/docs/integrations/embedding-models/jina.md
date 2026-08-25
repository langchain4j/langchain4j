---
sidebar_position: 9
---

# Jina

https://jina.ai/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-jina</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

## APIs

- `JinaEmbeddingModel`

## Capabilities

- **Multimodal** (`jina-clip-v2`, `jina-embeddings-v4` — auto-detected from the model name): embeds text and
  images. Jina embeds one modality per input item (it does **not** fuse interleaved text + image); pass a single
  `TextContent` or a single `ImageContent` (URL or base64) per input in an `EmbeddingRequest`.
- **Input type** (`jina-embeddings-v3`, `jina-embeddings-v4`, `jina-embeddings-v5` — auto-detected from the
  model name): embeds a search query differently from the documents it is matched against, which usually
  improves retrieval quality. Set `EmbeddingInputType.QUERY` or `DOCUMENT` per call on an `EmbeddingRequest`,
  or via `EmbeddingStoreContentRetriever.embeddingInputType(...)`. Models that do not offer this (for example
  `jina-clip-v2`) reject the parameter instead of silently ignoring it.
- **Listeners**: configure via `JinaEmbeddingModel.builder().listeners(...)`.

See [Embedding Model](/tutorials/rag#embedding-model) for the request/response API and multimodal usage.

## Examples

- [JinaEmbeddingModelIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-jina/src/test/java/dev/langchain4j/model/jina/JinaEmbeddingModelIT.java)
