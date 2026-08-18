---
sidebar_position: 7
---

# Chroma

https://www.trychroma.com/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-chroma</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

## APIs

- `ChromaEmbeddingStore`


## Examples

- [ChromaEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/chroma-example/src/main/java/ChromaEmbeddingStoreExample.java)

## Supported API Versions
Chroma has multiple REST API versions:
- until version 0.5.16: only API V1 is supported
- versions 0.5.16 to 0.6.3: API V1 and V2 are supported (there are some bugs in V1 API introduced around 0.6.2)
- versions after 0.7.0: only API V2 is supported, so you need to select the proper version
when configuring the `ChromaEmbeddingStore`:
```java
ChromaEmbeddingStore.builder()
    .apiVersion(ChromaApiVersion.V2)
    .baseUrl(...)
    .tenantName(...)
    .databaseName(...)
    .collectionName(...)
    .build();
```

## Distance Metric

A Chroma collection is created with a distance metric, which determines how the distance between two embeddings
is measured. Chroma supports `cosine`, `l2` (squared euclidean) and `ip` (inner product), and uses `l2` unless
another metric is requested.

When `ChromaEmbeddingStore` creates a collection, it creates it with the `cosine` distance metric.
When the collection already exists, it is used as is, whichever distance metric it was created with:

```java
ChromaEmbeddingStore store = ChromaEmbeddingStore.builder()
    .baseUrl("http://localhost:8000")
    .collectionName("my-collection")
    .build();
```

The relevance score returned by `EmbeddingMatch.score()` is always in the `[0, 1]` range, where 1 means the most
relevant. It is derived from the distance metric of the collection, so scores obtained from collections with
different distance metrics are not comparable with each other. The same applies to the `minScore` of an
`EmbeddingSearchRequest`, which is compared against the score of the collection's own metric.

For `ip`, the score is accurate only if the embeddings are normalized, which is the case for most embedding models.

## Current Limitations

- Chroma cannot filter by greater and less than of alphanumeric metadata, only int and float are supported
- Chroma filters by *not* as following: if you filter by "key" not equals "a",
  then in fact all items with "key" != "a" value are returned, but no items without "key" metadata!
