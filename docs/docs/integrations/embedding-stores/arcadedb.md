---
sidebar_position: 30
---

# ArcadeDB

https://arcadedb.com/

ArcadeDB is a multi-model NoSQL database that supports graph, document, key-value, time-series, and vector data. It provides a built-in LSM_VECTOR index (powered by JVector/HNSW) for high-performance approximate nearest neighbor (ANN) vector search, and a Lucene-based FULL_TEXT index for keyword search.

The langchain4j integration supports two operation modes:

- **Remote mode** — connects to an ArcadeDB server over HTTP. Suitable for production deployments and shared infrastructure.
- **Embedded mode** — runs ArcadeDB in-process inside the same JVM. No server or Docker container required; the database is stored on the local filesystem. Ideal for testing, desktop applications, or single-process workloads.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-arcadedb</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Note: This is a community integration module. You may need to add the langchain4j-community repository to your project configuration.

## APIs

- `ArcadeDBEmbeddingStore`

## Features

- **Two Operation Modes**: Remote (HTTP client to an ArcadeDB server) or embedded (ArcadeDB runs in-process, no server needed)
- **Multi-Model Database**: Stores embeddings as vertices in ArcadeDB's graph model alongside documents, key-value, and time-series data
- **HNSW Vector Index**: Uses ArcadeDB's LSM_VECTOR index (JVector-based) for fast approximate nearest neighbor search, with a configurable similarity function (COSINE, EUCLIDEAN, DOT_PRODUCT) and optional quantization
- **Hybrid Search**: Combines vector search with full-text (BM25) keyword search, fused with RRF, LINEAR or DBSF
- **Metadata Filtering**: Filters by metadata with comparison and logical operators; filters are evaluated in the database, so selective filters still return complete results
- **Upserts**: `addAll` with ids that already exist replaces those records
- **Batched Ingestion**: Large `addAll` calls are written in batches, one transaction per batch
- **Persistent Storage**: Data is durable across restarts in both remote and embedded modes
- **Auto Schema Creation**: Automatically creates the vertex type, properties, vector index and full-text index on first use; existing indexes are kept
- **Flexible Removal**: Remove embeddings by ID, by filter, or clear all

## Basic Usage

### Remote Mode

Remote mode connects to a running ArcadeDB server. See [Running ArcadeDB with Docker](#running-arcadedb-with-docker) to start one locally.

#### Connect to an Existing Database

```java
EmbeddingStore<TextSegment> embeddingStore = ArcadeDBEmbeddingStore.builder()
    .host("localhost")
    .port(2480)
    .databaseName("my_database")
    .username("root")
    .password("playwithdata")
    .dimension(384)           // Must match your embedding model's dimension
    .build();
```

#### Auto-Create the Database

```java
EmbeddingStore<TextSegment> embeddingStore = ArcadeDBEmbeddingStore.builder()
    .host("localhost")
    .port(2480)
    .databaseName("my_database")
    .username("root")
    .password("playwithdata")
    .dimension(384)
    .createDatabase(true)     // Create database if it doesn't exist
    .build();
```

### Embedded Mode

Embedded mode runs ArcadeDB inside the same JVM. No server is needed — just provide a path on the local filesystem where the database should be stored. The database is created automatically if it does not already exist.

`ArcadeDBEmbeddingStore` implements `Closeable`. Always close an embedded store when you are finished to release the database, for example with try-with-resources:

```java
try (ArcadeDBEmbeddingStore embeddingStore = ArcadeDBEmbeddingStore.embeddedBuilder()
        .databasePath("/path/to/my-database")
        .dimension(384)           // Must match your embedding model's dimension
        .build()) {
    // ... use the store ...
}
```

`close()` also closes a `Database` that was passed to the builder with `database(...)`.

### Add and Search Embeddings

The search API is identical in both modes:

```java
// Add a text segment with its embedding
TextSegment segment = TextSegment.from("Hello, world!", Metadata.from("source", "example"));
Embedding embedding = embeddingModel.embed(segment).content();
embeddingStore.add(embedding, segment);

// Search for similar embeddings
EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    .queryEmbedding(queryEmbedding)
    .maxResults(5)
    .minScore(0.7)
    .build();

List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
```

Adding embeddings with ids that already exist (for example with `addAll(ids, embeddings, segments)`) replaces the stored records.

## Hybrid Search

When the search request contains query text, the store runs a **hybrid search**: a vector search and a full-text (BM25) search over the segments' text, whose rankings are fused into one. Without query text, it runs a vector search only. No extra configuration is needed: the full-text index is created with the schema.

```java
String question = "Which database supports graph and vector data?";
Embedding questionEmbedding = embeddingModel.embed(question).content();

EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    .queryEmbedding(questionEmbedding)  // For the vector search
    .query(question)                    // For the full-text search
    .maxResults(5)
    .build();

List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
```

Matches are returned in fused order. Their `score` is the vector similarity of each match.

### Query Text

Query text is treated as plain text: Lucene's special characters (`+ - ! ( ) : ^ [ ] " { } ~ * ? | & / \`) are escaped, and the words `AND`, `OR` and `NOT` are searched as ordinary words, so a natural-language question never fails to parse. To pass Lucene query syntax through instead (for example `"exact phrase"` or `title:arcadedb`), build the store with `rawFullTextQuery(true)`. If a hybrid query fails, the store falls back to a vector search.

### Fusion

| Option | Description | Default |
|---|---|---|
| `hybridFusion` | How the two rankings are combined: `RRF` (reciprocal rank fusion), `LINEAR` (weighted sum of min-max normalized scores) or `DBSF` (distribution-based score fusion) — values of `ArcadeDBHybridFusion` | `RRF` |
| `hybridWeights(dense, fullText)` | Weights of the vector and the full-text ranking | `8, 1` |
| `hybridRrfK` | The constant `k` of RRF, `weight / (k + rank)` | `60` |

The defaults weight the vector ranking eight times more than the full-text ranking. They gave the best results in an evaluation on BEIR Natural Questions with `bge-small-en-v1.5` embeddings; equal weights ranked noticeably worse there. Tune the weights for your data and embedding model.

### Full-Text Index

| Option | Description | Default |
|---|---|---|
| `fullTextAnalyzer` | Lucene analyzer class of the full-text index; `null` for ArcadeDB's default (`StandardAnalyzer`) | `org.apache.lucene.analysis.en.EnglishAnalyzer` |
| `fullTextBm25K1` | BM25 `k1` | ArcadeDB's default (1.2) |
| `fullTextBm25B` | BM25 `b`, between 0 and 1 | ArcadeDB's default (0.75) |

The default `EnglishAnalyzer` removes English stop words and stems words, so a question such as "who sang the song" matches documents on its meaningful terms. **For text in other languages, set `fullTextAnalyzer`** to a suitable analyzer, for example `org.apache.lucene.analysis.de.GermanAnalyzer`, or to `org.apache.lucene.analysis.standard.StandardAnalyzer` for no language-specific processing.

These settings apply when the full-text index is created. A store opened on an existing index keeps that index's settings; to change them, rebuild the index.

```java
EmbeddingStore<TextSegment> embeddingStore = ArcadeDBEmbeddingStore.builder()
    .host("localhost")
    .port(2480)
    .databaseName("my_database")
    .username("root")
    .password("playwithdata")
    .dimension(384)
    .hybridFusion(ArcadeDBHybridFusion.LINEAR)
    .hybridWeights(0.7, 0.3)
    .fullTextAnalyzer("org.apache.lucene.analysis.de.GermanAnalyzer")
    .build();
```

## Configuration Options

### Remote Mode

```java
EmbeddingStore<TextSegment> embeddingStore = ArcadeDBEmbeddingStore.builder()
    .host("localhost")              // Required: ArcadeDB server hostname
    .port(2480)                     // Default: 2480 (HTTP port)
    .databaseName("my_database")   // Required: database name
    .username("root")               // Required: username
    .password("playwithdata")       // Required: password
    .typeName("EmbeddingDocument") // Default: "EmbeddingDocument" — vertex type name
    .dimension(384)                 // Required: embedding vector dimension
    .similarityFunction("COSINE")  // Default: "COSINE" — similarity function of the vector index
    .quantization(ArcadeDBVectorQuantization.NONE) // Default: NONE — vector index quantization
    .maxConnections(16)             // Default: 16 — HNSW graph connections per node
    .beamWidth(100)                 // Default: 100 — HNSW beam width while building the index
    .efSearch(null)                 // Default: null — search beam width per query (see below)
    .filterStrategy(ArcadeDBFilterStrategy.ADAPTIVE) // Default: ADAPTIVE — metadata filter execution
    .batchSize(500)                 // Default: 500 — records per request in addAll
    .createDatabase(false)          // Default: false — auto-create the database
    .metadataPrefix("meta_")       // Default: "meta_" — prefix for metadata properties
    .build();
```

### Embedded Mode

```java
ArcadeDBEmbeddingStore embeddingStore = ArcadeDBEmbeddingStore.embeddedBuilder()
    .databasePath("/path/to/my-database") // Required (or database(...)): local filesystem path for the database
    .typeName("EmbeddingDocument")        // Default: "EmbeddingDocument" — vertex type name
    .dimension(384)                        // Default: 384 — embedding vector dimension
    .similarityFunction("COSINE")         // Default: "COSINE" — similarity function of the vector index
    .quantization(ArcadeDBVectorQuantization.NONE) // Default: NONE — vector index quantization
    .maxConnections(16)                    // Default: 16 — HNSW graph connections per node
    .beamWidth(100)                        // Default: 100 — HNSW beam width while building the index
    .efSearch(null)                        // Default: null — search beam width per query (see below)
    .filterStrategy(ArcadeDBFilterStrategy.ADAPTIVE) // Default: ADAPTIVE — metadata filter execution
    .batchSize(1000)                       // Default: 1000 — records per transaction in addAll
    .metadataPrefix("")                    // Default: "" (no prefix) — prefix for metadata properties
    .build();
```

Both builders also take the [hybrid search](#hybrid-search) options (`rawFullTextQuery`, `hybridFusion`, `hybridWeights`, `hybridRrfK`, `fullTextAnalyzer`, `fullTextBm25K1`, `fullTextBm25B`).

### Parameter Guidelines

**Shared parameters (both modes):**

- **typeName**: The vertex type used to store embedding documents. Changing this allows multiple embedding stores within the same database
- **dimension**: Must match your embedding model's output dimension exactly
- **similarityFunction**: The similarity function of the vector index, and how scores are computed:
  - `COSINE` — cosine similarity (default); score `(1 + cosine) / 2`
  - `EUCLIDEAN` — Euclidean distance; score `1 / (1 + distance)`
  - `DOT_PRODUCT` — dot product, for unit-length (normalized) vectors only; score `(1 + dot) / 2`
- **quantization**: Quantization of the vectors in the index (`ArcadeDBVectorQuantization`): `NONE` (default, full precision), `INT8` (about 4× less index memory), `BINARY` (about 32× less index memory, lower recall) or `PRODUCT` (product quantization). The stored embeddings stay full precision
- **maxConnections**: Controls graph connectivity in the HNSW index. Higher values improve recall but increase memory and index build time. Recommended: 16–128
- **beamWidth**: Controls the quality of the HNSW graph when the index is built. Higher values produce a better graph at the cost of build time. Recommended: 100–500. It does not affect the search; see `efSearch`
- **efSearch**: The search beam width per query. Higher values improve recall at the cost of latency. By default, embedded mode uses ArcadeDB's default beam for the number of candidates it fetches, and remote mode an equivalent beam, so both modes reach the same recall. When set, it is at least the number of candidates fetched
- **filterStrategy**: How searches with a metadata filter are executed; see [Metadata Filtering](#metadata-filtering)
- **batchSize**: Number of records written per transaction (embedded) or per request and transaction (remote) when adding embeddings
- **metadataPrefix**: Prefix applied to metadata keys when stored as vertex properties. Change if your metadata keys conflict with built-in properties

`similarityFunction`, `quantization`, `maxConnections` and `beamWidth` apply when the vector index is created. A store opened on an existing index keeps that index's settings.

**Remote-only parameters:**

- **host**: Hostname or IP address of the ArcadeDB server (required)
- **port**: HTTP port for the ArcadeDB REST API (default: 2480)
- **databaseName**: The database to connect to or create (required)
- **username / password**: ArcadeDB credentials (required)
- **createDatabase**: Set to `true` to automatically create the database if it does not exist

**Embedded-only parameters:**

- **databasePath**: Path to the directory where the embedded database is stored. Created automatically if it does not exist
- **database**: Alternatively, supply an existing `com.arcadedb.database.Database` instance directly instead of a path

## Metadata Filtering

ArcadeDB supports filtering search results by metadata, in vector and in hybrid search:

```java
// Filter by a single metadata value
Filter filter = new IsEqualTo("source", "wikipedia");

EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    .queryEmbedding(queryEmbedding)
    .maxResults(5)
    .filter(filter)
    .build();

List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
```

### Supported Filter Types

**Comparison operators:**
- `IsEqualTo`, `IsNotEqualTo`
- `IsGreaterThan`, `IsGreaterThanOrEqualTo`
- `IsLessThan`, `IsLessThanOrEqualTo`
- `IsIn`, `IsNotIn`
- `ContainsString`

**Logical operators:**
- `And`, `Or`, `Not`

### Filter Strategies

Filters are translated to SQL and evaluated in the database, so a filter that matches only a small fraction of the records still returns the requested number of results. `filterStrategy` controls how (values of `ArcadeDBFilterStrategy`):

- `ADAPTIVE` (default):
  - **remote mode** — as `PRE_FILTER`: one query on the server.
  - **embedded mode** — the nearest candidates are fetched and filtered first, which costs about as much as an unfiltered search when the filter is broad; when fewer than `maxResults` of them pass, the search is repeated as `PRE_FILTER`.
- `PRE_FILTER` — the filter selects the allowed records, and the vector search returns the nearest of them. In hybrid search, the full-text results are filtered too.
- `POST_FILTER` — a fixed number of nearest candidates is fetched and filtered. Fast, but a selective filter returns fewer than `maxResults` results, or none.

`ContainsString` has no SQL translation. A filter that contains it anywhere (also inside `And`, `Or` or `Not`) is applied as a whole to the fetched candidates, as with `POST_FILTER`.

To select the allowed records, the database scans the vertex type unless the filtered metadata property is indexed. For very selective filters on large stores, an index on that property makes filtered searches faster; create it on the vertex type with ArcadeDB's `CREATE PROPERTY` and `CREATE INDEX`, using the metadata key with its `metadataPrefix` as the property name.

## Removal Operations

```java
// Remove by list of IDs
embeddingStore.removeAll(List.of("id1", "id2"));

// Remove by metadata filter
embeddingStore.removeAll(new IsEqualTo("source", "old-source"));

// Remove all embeddings
embeddingStore.removeAll();
```

## Current Limitations

- **Approximate Search**: The HNSW index is approximate: a vector search can miss some of the true nearest neighbors. Raise `efSearch` (per query) or `maxConnections` and `beamWidth` (when the index is built) for higher recall
- **Index Settings Are Fixed at Creation**: The vector index settings (`similarityFunction`, `quantization`, `maxConnections`, `beamWidth`) and the full-text index settings (`fullTextAnalyzer`, `fullTextBm25K1`, `fullTextBm25B`) apply when an index is created. A store opened on an existing index keeps that index's settings
- **Language of the Default Analyzer**: The default full-text analyzer is English. Set `fullTextAnalyzer` for text in other languages
- **`ContainsString` Is Applied After the Search**: Unlike the other filters, it is not evaluated in the database, so a selective filter containing `ContainsString` can return fewer than `maxResults` results
- **Floating-Point Precision**: ArcadeDB returns vectors as JSON doubles, which may introduce minor floating-point precision differences compared to the original stored values. `Double.MIN_VALUE` (4.9E-324) underflows to 0.0 and cannot be stored precisely

## Running ArcadeDB with Docker

Required for remote mode. The quickest way to get started:

```bash
docker run -d \
  --name arcadedb \
  -p 2480:2480 \
  -e JAVA_OPTS="-Darcadedb.server.rootPassword=playwithdata" \
  arcadedata/arcadedb:26.10.2
```

Then connect your store:

```java
EmbeddingStore<TextSegment> embeddingStore = ArcadeDBEmbeddingStore.builder()
    .host("localhost")
    .port(2480)
    .databaseName("embeddings")
    .username("root")
    .password("playwithdata")
    .dimension(384)
    .createDatabase(true)
    .build();
```

## Examples

- For integration test examples, check the test files in the [langchain4j-community-arcadedb module](https://github.com/langchain4j/langchain4j-community/tree/main/embedding-stores/langchain4j-community-arcadedb/src/test/java)
- You can also find a few examples in the [langchain4j-examples project](https://github.com/langchain4j/langchain4j-examples)
