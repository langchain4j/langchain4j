---
sidebar_position: 14
---

# Milvus

[Milvus](https://milvus.io/) is an open-source vector database. LangChain4j can use it as an
`EmbeddingStore` to persist embeddings and run similarity search over them.

## Two Milvus modules

There are currently **two** Milvus integration modules. They are independent and can be used side by side.

| Module | Milvus Java SDK | Capabilities | Status |
|---|---|---|---|
| `langchain4j-milvus` | v1 (`MilvusServiceClient`) | Dense vector search | Legacy. Built on the deprecated v1 SDK. |
| `langchain4j-milvus-v2` | v2 (`MilvusClientV2`) | Dense **+ sparse + hybrid** search (incl. built-in BM25) | Current. Recommended for new projects. |

:::note Transitional naming — this will change in LangChain4j 2.0
The `-v2` suffix refers to the **Milvus SDK version**, not to a version of this module. It is temporary.

In **LangChain4j 2.0** the legacy `langchain4j-milvus` module will be **removed**, and `langchain4j-milvus-v2`
will be **renamed to `langchain4j-milvus`** (the Maven artifact, the `...milvus.v2` package, and the `MilvusV2*`
class names will all drop the `v2`).

If you adopt `langchain4j-milvus-v2` now, be aware that upgrading to 2.0 will require updating your Maven
coordinates, imports, and class names. A migration guide will be provided with the 2.0 release.

**New projects should use `langchain4j-milvus-v2`.**
:::

---

## `langchain4j-milvus-v2` (recommended)

Built on the current Milvus Java SDK v2. Supports dense vector search, sparse vector search, and hybrid
(dense + sparse) search — including Milvus' built-in BM25 full-text sparse vectors.

### Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-milvus-v2</artifactId>
    <version>1.19.0-beta29</version>
</dependency>
```

### API

- `MilvusV2EmbeddingStore`

### Basic usage — dense vector search

This is the standard `EmbeddingStore` usage. The search mode defaults to `VECTOR` (dense only).

```java
MilvusV2EmbeddingStore store = MilvusV2EmbeddingStore.builder()
        .uri("http://localhost:19530")        // or .host("localhost").port(19530)
        .collectionName("my_collection")
        .dimension(384)                        // required when a new collection is created
        .build();

store.add(embedding, textSegment);

EmbeddingSearchResult<TextSegment> result = store.search(
        EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .build());
```

### Hybrid search (dense + sparse)

Hybrid search combines a dense vector search with a sparse vector search and merges the results with a
re-ranker (Reciprocal Rank Fusion by default). Enable it with `searchMode(HYBRID)`.

There are two ways to produce the sparse vectors, selected via `sparseMode`:

- **`BM25` (default)** — Milvus computes the sparse vector from your text automatically. You provide the text.
- **`CUSTOM`** — you provide the sparse vectors yourself (e.g. from a BGE-M3 model).

#### Option A — built-in BM25 (text → sparse, computed by Milvus)

```java
MilvusV2EmbeddingStore store = MilvusV2EmbeddingStore.builder()
        .uri("http://localhost:19530")
        .collectionName("bm25_collection")
        .dimension(384)
        .searchMode(MilvusV2EmbeddingStore.SearchMode.HYBRID)
        .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.BM25)   // default
        .build();

// Insert: only dense embeddings are needed; Milvus builds the BM25 sparse index from the text.
store.addAll(ids, denseEmbeddings, textSegments);

// Search: provide the dense query embedding and the query text (used for BM25).
MilvusV2EmbeddingSearchRequest request = MilvusV2EmbeddingSearchRequest.milvusBuilder()
        .queryEmbedding(queryDenseEmbedding)
        .query("full-text keywords here")
        .maxResults(10)
        .build();

EmbeddingSearchResult<TextSegment> result = store.search(request);
```

#### Option B — custom sparse vectors (e.g. BGE-M3)

```java
MilvusV2EmbeddingStore store = MilvusV2EmbeddingStore.builder()
        .uri("http://localhost:19530")
        .collectionName("hybrid_collection")
        .dimension(384)
        .searchMode(MilvusV2EmbeddingStore.SearchMode.HYBRID)
        .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM) // you provide sparse vectors
        .build();

// Insert both dense and sparse embeddings.
List<SparseEmbedding> sparseEmbeddings = List.of(
        new SparseEmbedding(new long[]{1L, 42L, 300L}, new float[]{0.8f, 0.5f, 0.3f}),
        new SparseEmbedding(new long[]{7L, 99L},        new float[]{0.6f, 0.4f}));
store.addAllHybrid(ids, denseEmbeddings, sparseEmbeddings, textSegments);

// Search with a dense query embedding and a sparse query embedding.
MilvusV2EmbeddingSearchRequest request = MilvusV2EmbeddingSearchRequest.milvusBuilder()
        .queryEmbedding(queryDenseEmbedding)
        .sparseEmbedding(querySparseEmbedding)
        .maxResults(10)
        .build();

EmbeddingSearchResult<TextSegment> result = store.search(request);
```

:::note
The search mode is a property of the **collection schema**, set once when the store (and collection) is
created. A `HYBRID` collection has both a dense and a sparse vector field; a `VECTOR` collection has only a
dense field. The two are not interchangeable — switching modes requires a new collection.
:::

### Connecting to Zilliz Cloud

```java
MilvusV2EmbeddingStore store = MilvusV2EmbeddingStore.builder()
        .uri("https://xxx.api.gcp-us-west1.zillizcloud.com")
        .token("your-api-key")
        .collectionName("my_collection")
        .dimension(384)
        .build();
```

You can also pass your own `MilvusClientV2` instance via `.milvusClient(client)`.

### Compatibility

- Milvus Server **2.5.x or later** is recommended (BM25 full-text search requires 2.5+; hybrid search requires 2.4+).
- Java 17+

---

## `langchain4j-milvus` (legacy)

Built on the Milvus Java SDK v1. Supports dense vector search only. It will be removed in LangChain4j 2.0
(see the note above); new projects should prefer `langchain4j-milvus-v2`.

### Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-milvus</artifactId>
    <version>1.18.0-beta28</version>
</dependency>
```

### API

- `MilvusEmbeddingStore`

### Creation

There are 2 ways to create a `MilvusEmbeddingStore`:

1. Let the store create a `MilvusServiceClient` internally from the host, port, and authentication details:

```java
MilvusEmbeddingStore store = MilvusEmbeddingStore.builder()
    .host("localhost")                         // Host for Milvus instance
    .port(19530)                               // Port for Milvus instance
    .collectionName("example_collection")      // Name of the collection
    .dimension(128)                            // Dimension of vectors
    .indexType(IndexType.FLAT)                 // Index type
    .metricType(MetricType.COSINE)             // Metric type
    .username("username")                      // Username for Milvus
    .password("password")                      // Password for Milvus
    .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)  // Consistency level
    .autoFlushOnInsert(true)                   // Auto flush after insert
    .idFieldName("id")                         // ID field name
    .textFieldName("text")                     // Text field name
    .metadataFieldName("metadata")             // Metadata field name
    .vectorFieldName("vector")                 // Vector field name
    .build();
```

2. Pass an existing `MilvusServiceClient`:

```java
// Set up a custom MilvusServiceClient
MilvusServiceClient customMilvusClient = new MilvusServiceClient(
    ConnectParam.newBuilder()
        .withHost("localhost")
        .withPort(19530)
        .build()
);

// Use the custom client in the builder
MilvusEmbeddingStore store = MilvusEmbeddingStore.builder()
    .milvusClient(customMilvusClient)          // Use an existing Milvus client
    .collectionName("example_collection")      // Name of the collection
    .dimension(128)                            // Dimension of vectors
    .indexType(IndexType.FLAT)                 // Index type
    .metricType(MetricType.COSINE)             // Metric type
    .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)  // Consistency level
    .autoFlushOnInsert(true)                   // Auto flush after insert
    .idFieldName("id")                         // ID field name
    .textFieldName("text")                     // Text field name
    .metadataFieldName("metadata")             // Metadata field name
    .vectorFieldName("vector")                 // Vector field name
    .build();
```

---

## Examples

- [MilvusEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/milvus-example/src/main/java/MilvusEmbeddingStoreExample.java)
