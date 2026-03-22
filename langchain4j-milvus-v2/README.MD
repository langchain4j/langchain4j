# langchain4j-milvus-v2

LangChain4j integration for [Milvus](https://milvus.io/), built on top of the **Milvus Java SDK v2** (`MilvusClientV2`).

---

## Why v2?

The original `langchain4j-milvus` module uses the legacy **Milvus Java SDK v1** (`MilvusServiceClient`), which is deprecated and does not support newer Milvus features such as Hybrid Search and BM25 full-text sparse vectors.

`langchain4j-milvus-v2` is a full rewrite based on the **Milvus Java SDK v2**, unlocking:

| Feature | `langchain4j-milvus` (v1 SDK) | `langchain4j-milvus-v2` (v2 SDK) |
|---|---|---|
| Dense vector search | ✅ | ✅ |
| Sparse vector search | ❌ | ✅ |
| Hybrid search (dense + sparse) | ❌ | ✅ |
| BM25 built-in sparse (text → sparse) | ❌ | ✅ |
| Custom sparse vectors (e.g. BGE-M3) | ❌ | ✅ |
| Milvus SDK | `MilvusServiceClient` (v1, deprecated) | `MilvusClientV2` (v2, current) |

---

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-milvus-v2</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

---

## Search Modes

`langchain4j-milvus-v2` introduces the concept of **store-level search mode**, configured when building `MilvusV2EmbeddingStore`:

| Mode | Description |
|---|---|
| `SearchMode.VECTOR` | Dense vector search only. Default. Compatible with standard `EmbeddingStore` usage. |
| `SearchMode.HYBRID` | Hybrid search combining dense + sparse vectors, re-ranked via RRF (or custom ranker). requires `MilvusV2EmbeddingSearchRequest` during search. |

The search mode is set once on the store and applies to all `search()` calls on that store instance.

⚠️ **Important:** `SearchMode` dictates the collection schema (dense-only vs dense+sparse). Switching search modes requires creating a new collection (or manually altering the schema outside of this library).

---

## Basic Usage — Vector Search (VECTOR mode)

Identical to `langchain4j-milvus`. Drop-in replacement for standard dense embedding search.

```java
MilvusV2EmbeddingStore store = MilvusV2EmbeddingStore.builder()
        .uri("http://localhost:19530")
        .collectionName("my_collection")
        .dimension(384)
        // searchMode defaults to SearchMode.VECTOR
        .build();

// Standard EmbeddingStore usage
store.add(embedding, textSegment);

EmbeddingSearchResult<TextSegment> result = store.search(
        EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .build());
```

---

## Hybrid Search — CUSTOM Sparse Vectors (e.g. BGE-M3)

Use this when you generate sparse vectors yourself (e.g. from a BGE-M3 model that produces both dense and sparse embeddings simultaneously).

### 1. Create a HYBRID store

```java
MilvusV2EmbeddingStore store = MilvusV2EmbeddingStore.builder()
        .uri("http://localhost:19530")
        .collectionName("hybrid_collection")
        .dimension(384)
        .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)  // user-provided sparse vectors
        .searchMode(MilvusV2EmbeddingStore.SearchMode.HYBRID)
        .build();
```

### 2. Insert documents with both dense and sparse embeddings

```java
// Dense embeddings from your embedding model
List<Embedding> denseEmbeddings = ...;

// Sparse embeddings from BGE-M3 or any sparse encoder
List<SparseEmbedding> sparseEmbeddings = Arrays.asList(
        new SparseEmbedding(new long[]{1L, 42L, 300L}, new float[]{0.8f, 0.5f, 0.3f}),
        new SparseEmbedding(new long[]{7L, 99L},        new float[]{0.6f, 0.4f})
);

store.addAllHybrid(ids, denseEmbeddings, sparseEmbeddings, textSegments);
```

### 3. Search

```java
MilvusV2EmbeddingSearchRequest request = MilvusV2EmbeddingSearchRequest.milvusBuilder()
        .queryEmbedding(queryDenseEmbedding)
        .sparseEmbedding(querySparseEmbedding)
        .maxResults(10)
        .build();

EmbeddingSearchResult<TextSegment> result = store.search(request);
```

---

## Hybrid Search — BM25 Built-in Sparse (text → sparse auto-computed)

Use this when you want Milvus to automatically convert text into sparse vectors using its built-in BM25 model. No external sparse encoder needed.

### 1. Create a HYBRID store with BM25 sparse mode

```java
MilvusV2EmbeddingStore store = MilvusV2EmbeddingStore.builder()
        .uri("http://localhost:19530")
        .collectionName("bm25_hybrid_collection")
        .dimension(384)
        .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.BM25)   // default, Milvus auto-computes sparse
        .searchMode(MilvusV2EmbeddingStore.SearchMode.HYBRID)
        .build();
```

### 2. Insert documents — only dense embeddings needed

```java
// Milvus automatically builds the sparse (BM25) index from the text field
store.addAll(ids, denseEmbeddings, textSegments);
```

### 3. Search with a query string

```java
MilvusV2EmbeddingSearchRequest request = MilvusV2EmbeddingSearchRequest.milvusBuilder()
        .queryEmbedding(queryDenseEmbedding)   // dense component
        .query("full-text keywords here")       // sparse component (BM25 auto-computed by Milvus)
        .maxResults(10)
        .build();

EmbeddingSearchResult<TextSegment> result = store.search(request);
```

---

## Sparse-only Insert / Dense-only Insert

For collections that need to store both dense and sparse vectors but insert them separately:

```java
// Insert only sparse vectors (e.g. for documents that have no dense embedding)
store.addAllSparse(ids, sparseEmbeddings, textSegments);

// Insert only dense vectors (sparse field will be zero-filled)
store.addAll(ids, denseEmbeddings, textSegments);
```

---

## Full Builder Reference

```java
MilvusV2EmbeddingStore store = MilvusV2EmbeddingStore.builder()
        // Connection
        .uri("https://xxx.api.gcp-us-west1.zillizcloud.com")  // or .host() + .port()
        .token("your-api-key")                                  // for Zilliz Cloud
        .username("root")                                       // for self-managed Milvus
        .password("milvus")
        .databaseName("default")

        // Collection
        .collectionName("my_collection")
        .dimension(384)                                         // required for new collections

        // Dense index
        .indexType(IndexParam.IndexType.FLAT)                   // default: FLAT
        .metricType(IndexParam.MetricType.COSINE)               // default: COSINE

        // Sparse index (only relevant for HYBRID mode)
        .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.BM25) // BM25 (default) or CUSTOM
        .sparseIndexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX) // default: SPARSE_INVERTED_INDEX
        .sparseMetricType(IndexParam.MetricType.BM25)           // BM25 or IP

        // Search mode
        .searchMode(MilvusV2EmbeddingStore.SearchMode.VECTOR)     // VECTOR (default) or HYBRID

        // Hybrid ranker (only relevant for HYBRID mode)
        // Accepts rankers via MilvusV2Ranker builder methods
        .ranker(MilvusV2Ranker.rrf(60))                         // default: MilvusV2Ranker.rrf(60)

        // Field names (customise if your collection uses different names)
        .idFieldName("id")
        .textFieldName("text")
        .metadataFieldName("metadata")
        .vectorFieldName("vector")
        .sparseVectorFieldName("sparse_vector")

        // Behaviour
        .consistencyLevel(ConsistencyLevel.EVENTUALLY)          // default: EVENTUALLY, STRONG, BOUNDED, SESSION
        .retrieveEmbeddingsOnSearch(false)                      // default: false
        .autoFlushOnInsert(false)                               // default: false

        .build();
```

---

## Key Differences from `langchain4j-milvus`

### 1. SDK Version

| | `langchain4j-milvus` | `langchain4j-milvus-v2` |
|---|---|---|
| SDK | `MilvusServiceClient` (v1) | `MilvusClientV2` (v2) |
| Client builder | `ConnectParam` | `ConnectConfig` |

You can also provide your own `MilvusClientV2` instance directly:

```java
MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder()
        .uri("http://localhost:19530")
        .build());

MilvusV2EmbeddingStore store = MilvusV2EmbeddingStore.builder()
        .milvusClientV2(client)
        .collectionName("my_collection")
        .dimension(384)
        .build();
```

### 2. Search Mode is Store-level

In `langchain4j-milvus`, there is only one search mode (dense). In `langchain4j-milvus-v2`, the search mode is configured on the **store**, not per-request:

```java
// VECTOR store — dense search only
MilvusV2EmbeddingStore vectorStore = MilvusV2EmbeddingStore.builder()
        ...
        .searchMode(MilvusV2EmbeddingStore.SearchMode.VECTOR)
        .build();

// HYBRID store — dense + sparse search
MilvusV2EmbeddingStore hybridStore = MilvusV2EmbeddingStore.builder()
        ...
        .searchMode(MilvusV2EmbeddingStore.SearchMode.HYBRID)
        .build();
```

### 3. MilvusV2EmbeddingSearchRequest

`langchain4j-milvus-v2` introduces `MilvusV2EmbeddingSearchRequest`, which extends the standard `EmbeddingSearchRequest` with Milvus-specific fields:

```java
MilvusV2EmbeddingSearchRequest request = MilvusV2EmbeddingSearchRequest.milvusBuilder()
        .queryEmbedding(denseEmbedding)      // dense query vector
        .sparseEmbedding(sparseEmbedding)    // sparse query vector (CUSTOM mode)
        .query("search keywords")            // text query for BM25 (BM25 mode)
        .maxResults(10)
        .minScore(0.5)
        .filter(metadataFilter)
        .build();
```

Wait, `MilvusV2EmbeddingSearchRequest` is generally required when using `SearchMode.HYBRID`.
The standard `EmbeddingSearchRequest` (which lacks sparse fields) is supported but will only work with `SearchMode.VECTOR`. Using a standard request with `SearchMode.HYBRID` will result in an exception because sparse inputs are missing.

### 4. Collection Schema

A **HYBRID** collection automatically creates both a dense vector field and a sparse vector field. A **VECTOR** collection only creates a dense vector field. The two types are **not interchangeable** — you cannot point a HYBRID store at a VECTOR collection or vice versa.

---

## Compatibility

- **Milvus Server 2.5.x** and above (Recommended)
  - **BM25 (Full Text Search)**: Requires Milvus 2.5+.
  - **Hybrid Search (RRF/Weighted)**: Supported in Milvus 2.4+, fully compatible with Milvus 2.5+.
  - **Basic Vector Search**: Compatible with Milvus 2.x.
- **Java 17+**

> **Note on SDK Version:** This module uses `milvus-sdk-java` v2.6.x. It is backward compatible with Milvus Server v2.5.x for most features (including Hybrid Search and BM25). You do not need to downgrade the SDK to connect to a v2.5 Server.

---

## Hybrid Rankers

When using `SearchMode.HYBRID`, Milvus requires a reranking strategy to combine scores from dense and sparse retrievals. You can configure this via `.ranker(...)` using `MilvusV2Ranker`:

### RRF (Reciprocal Rank Fusion) - Default

```java
.ranker(MilvusV2Ranker.rrf(60))
```

### Weighted

Combines search results with specified weights. Dense search is the first component, sparse/text search is the second.

```java
.ranker(MilvusV2Ranker.weighted(Arrays.asList(0.8f, 0.2f)))
```

### Advanced Rankers

You can also use Boost, Model, or Decay rankers.

```java
// Boost
.ranker(MilvusV2Ranker.boost().weight(0.5f).build())

// Model (e.g. for external reranker)
.ranker(MilvusV2Ranker.model().endpoint("http://...").build())
```
