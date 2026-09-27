---
sidebar_position: 21
---

# Qdrant

https://qdrant.tech/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-qdrant</artifactId>
    <version>1.20.1-beta30</version>
</dependency>
```


## APIs

- `QdrantEmbeddingStore`


## Hybrid Search

With `SearchMode.HYBRID`, `QdrantEmbeddingStore` combines dense and sparse vectors and fuses the results
with Reciprocal Rank Fusion (RRF). The collection must be created with a named dense vector and a named
sparse vector (`dense` and `sparse` by default, configurable via `denseVectorName` and `sparseVectorName`).
Search requests must carry the query text (`EmbeddingSearchRequest.query(...)`).

Sparse vectors can be produced in two ways:

- **Client-side**, with your own `SparseEncoder` implementation:

```java
QdrantEmbeddingStore store = QdrantEmbeddingStore.builder()
        .host("localhost")
        .port(6334)
        .collectionName("my-collection")
        .searchMode(SearchMode.HYBRID)
        .sparseEncoder(mySparseEncoder)
        .build();
```

- **Server-side**, with a model executed by Qdrant, such as the built-in BM25 model:

```java
QdrantEmbeddingStore store = QdrantEmbeddingStore.builder()
        .host("localhost")
        .port(6334)
        .collectionName("my-collection")
        .searchMode(SearchMode.HYBRID)
        .sparseModel(QdrantSparseModel.bm25Builder()
                .language("english")
                .avgLen(128)
                .build())
        .build();
```

For BM25, the sparse vector must be created with the IDF modifier, otherwise the IDF part of the score is missing:

```java
SparseVectorParams.newBuilder().setModifier(Modifier.Idf).build()
```

Other models supported by your Qdrant deployment (e.g. on Qdrant Cloud) can be used with
`QdrantSparseModel.of(modelName, options)`.

In `HYBRID` mode, match scores are RRF scores (small, rank-based values), not cosine similarities,
so `minScore` must be set accordingly.


## Examples

- [QdrantEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/qdrant-example/src/main/java/QdrantEmbeddingStoreExample.java)
