---
sidebar_position: 14
---

# Ollama

https://ollama.com/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
    <version>1.18.0</version>
</dependency>
```

## APIs

- `OllamaEmbeddingModel`

## Usage

```java
EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("all-minilm")
    .build();

Response<Embedding> response = embeddingModel.embed("Hello, world!");
Embedding embedding = response.content();
```

## Request/response API and observability

`OllamaEmbeddingModel` supports `embed(EmbeddingRequest)` in addition to the convenience methods. Ollama's
embedding API is text-only, so a request that sets an input type or an image input fails fast with
`UnsupportedFeatureException`. Token usage (`prompt_eval_count`) is reported in the response metadata.

Ollama's optional output `dimensions` is model-dependent (only some models support reducing the output size),
so it is configured on the builder via `.dimensions(...)` rather than as a per-call parameter.

Attach [listeners](/tutorials/observability#embeddingmodel-observability) to observe requests, responses, and
errors:

```java
EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("all-minilm")
    .listeners(List.of(myEmbeddingModelListener))
    .build();
```


## Examples

- [OllamaEmbeddingModelIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-ollama/src/test/java/dev/langchain4j/model/ollama/OllamaEmbeddingModelIT.java)
