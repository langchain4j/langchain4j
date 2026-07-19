---
sidebar_position: 4
---

# Cohere

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-cohere</artifactId>
    <version>1.18.0-beta28</version>
</dependency>
```

## APIs

- `CohereEmbeddingModel`

## Capabilities

- **Multimodal** (`embed-v4.0`): embeds text and images into a shared vector space; interleaved text + image is
  fused into a single embedding. Provide image inputs as `ImageContent` (URL or base64) in an `EmbeddingRequest`.
- **Per-call parameters**: `input_type` — embed queries and documents differently
  (`EmbeddingInputType.QUERY` / `DOCUMENT`, mapped to Cohere's `search_query` / `search_document`).
- **Listeners**: configure via `CohereEmbeddingModel.builder().listeners(...)`.

See [Embedding Model](/tutorials/rag#embedding-model) for the request/response API and multimodal usage.

## Examples

- [CohereEmbeddingModelIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-cohere/src/test/java/dev/langchain4j/model/cohere/CohereEmbeddingModelIT.java)
