---
sidebar_position: 30
---

# ArcadeDB

https://arcadedb.com/

ArcadeDB is a multi-model NoSQL database that supports graph, document, key-value, time-series, and vector data. It provides a built-in LSM_VECTOR index (powered by JVector/HNSW) for high-performance approximate nearest neighbor (ANN) vector search.

The langchain4j integration supports two operation modes:

- **Remote mode** — connects to an ArcadeDB server over HTTP. Suitable for production deployments and shared infrastructure.
- **Embedded mode** — runs ArcadeDB in-process inside the same JVM. No server or Docker container required; the database is stored on the local filesystem. Ideal for testing, desktop applications, or single-process workloads.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-arcadedb</artifactId>
    <version>1.13.0-beta22-SNAPSHOT</version>
</dependency>
```

Note: This is a community integration module. You may need to add the langchain4j-community repository to your project configuration.

## APIs

- `ArcadeDBEmbeddingStore`

## Features

- **Two Operation Modes**: Remote (HTTP client to an ArcadeDB server) or embedded (ArcadeDB runs in-process, no server needed)
- **Multi-Model Database**: Stores embeddings as vertices in ArcadeDB's graph model alongside documents, key-value, and time-series data
- **HNSW Vector Index**: Uses ArcadeDB's LSM_VECTOR index (JVector-based) for fast approximate nearest neighbor search
- **Metadata Filtering**: Supports filtering search results by metadata using comparison and logical operators
- **Persistent Storage**: Data is durable across restarts in both remote and embedded modes
- **Auto Schema Creation**: Automatically creates the vertex type, properties, and vector index on first use
- **Multiple Similarity Functions**: Supports COSINE (default), EUCLIDEAN, and SQUARED_EUCLIDEAN distance metrics (remote mode)
- **Batch Operations**: Add multiple embeddings in a single call
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

Always call `close()` when you are finished to release resources.

```java
ArcadeDBEmbeddingStore embeddingStore = ArcadeDBEmbeddingStore.embeddedBuilder()
    .databasePath("/path/to/my-database")
    .dimension(384)           // Must match your embedding model's dimension
    .build();

// ... use the store ...

embeddingStore.close();
```

Use try-finally (or try-with-resources via a wrapper) to ensure `close()` is always called:

```java
ArcadeDBEmbeddingStore embeddingStore = ArcadeDBEmbeddingStore.embeddedBuilder()
    .databasePath("/path/to/my-database")
    .dimension(384)
    .build();
try {
    // ... use the store ...
} finally {
    embeddingStore.close();
}
```

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
    .similarityFunction("COSINE")  // Default: "COSINE" — similarity metric
    .maxConnections(16)             // Default: 16 — HNSW graph connections per node
    .beamWidth(100)                 // Default: 100 — HNSW search beam width
    .createDatabase(false)          // Default: false — auto-create the database
    .metadataPrefix("meta_")       // Default: "meta_" — prefix for metadata properties
    .build();
```

### Embedded Mode

```java
ArcadeDBEmbeddingStore embeddingStore = ArcadeDBEmbeddingStore.embeddedBuilder()
    .databasePath("/path/to/my-database") // Required: local filesystem path for the database
    .typeName("EmbeddingDocument")        // Default: "EmbeddingDocument" — vertex type name
    .dimension(384)                        // Default: 384 — embedding vector dimension
    .maxConnections(16)                    // Default: 16 — HNSW graph connections per node
    .beamWidth(100)                        // Default: 100 — HNSW search beam width
    .metadataPrefix("")                    // Default: "" (no prefix) — prefix for metadata properties
    .build();
```

### Parameter Guidelines

**Shared parameters (both modes):**

- **typeName**: The vertex type used to store embedding documents. Changing this allows multiple embedding stores within the same database
- **dimension**: Must match your embedding model's output dimension exactly
- **maxConnections**: Controls graph connectivity in the HNSW index. Higher values improve recall but increase memory and index build time. Recommended: 16–128
- **beamWidth**: Controls the quality of the HNSW index construction and search. Higher values produce better recall at the cost of speed. Recommended: 100–500
- **metadataPrefix**: Prefix applied to metadata keys when stored as vertex properties. Change if your metadata keys conflict with built-in properties

**Remote-only parameters:**

- **host**: Hostname or IP address of the ArcadeDB server (required)
- **port**: HTTP port for the ArcadeDB REST API (default: 2480)
- **databaseName**: The database to connect to or create (required)
- **username / password**: ArcadeDB credentials (required)
- **similarityFunction**:
  - `COSINE` — Cosine similarity; best for normalized vectors (default)
  - `EUCLIDEAN` — Euclidean distance
  - `SQUARED_EUCLIDEAN` — Squared Euclidean distance; faster than EUCLIDEAN
- **createDatabase**: Set to `true` to automatically create the database if it does not exist

**Embedded-only parameters:**

- **databasePath**: Path to the directory where the embedded database is stored. Created automatically if it does not exist
- **database**: Alternatively, supply an existing `com.arcadedb.database.Database` instance directly instead of a path

## Metadata Filtering

ArcadeDB supports filtering search results by metadata. Filters are applied after the vector index lookup.

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

**Logical operators:**
- `And`, `Or`, `Not`

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

- **Approximate Search**: The HNSW index is approximate. With very large result sets containing many near-identical vectors, some documents may not be returned
- **In-Memory Filter Application**: Metadata filters are applied in-memory after the vector search rather than at the index level. The store fetches up to 5× the requested number of results to account for filter reduction
- **Floating-Point Precision**: ArcadeDB returns vectors as JSON doubles, which may introduce minor floating-point precision differences compared to the original stored values. `Double.MIN_VALUE` (4.9E-324) underflows to 0.0 and cannot be stored precisely
- **No String Content Filters**: String-based content filters (e.g., `ContainsString`) are not supported; only the metadata filter types listed above are available
- **No Similarity Function Choice in Embedded Mode**: The embedded builder does not expose a `similarityFunction` option; the index uses its default metric

## Running ArcadeDB with Docker

Required for remote mode. The quickest way to get started:

```bash
docker run -d \
  --name arcadedb \
  -p 2480:2480 \
  -e JAVA_OPTS="-Darcadedb.server.rootPassword=playwithdata" \
  arcadedata/arcadedb:latest
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
