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
    <version>1.17.2-beta27</version>
</dependency>
```

## APIs

- `JinaEmbeddingModel`

## Capabilities

- **Multimodal** (`jina-clip-v2`, `jina-embeddings-v4` — auto-detected from the model name): embeds text and
  images. Jina embeds one modality per input item (it does **not** fuse interleaved text + image); pass a single
  `TextContent` or a single `ImageContent` (URL or base64) per input in an `EmbeddingRequest`.
- **Listeners**: configure via `JinaEmbeddingModel.builder().listeners(...)`.

See [Embedding Model](/tutorials/rag#embedding-model) for the request/response API and multimodal usage.

## Examples

- [JinaEmbeddingModelIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-jina/src/test/java/dev/langchain4j/model/jina/JinaEmbeddingModelIT.java)
