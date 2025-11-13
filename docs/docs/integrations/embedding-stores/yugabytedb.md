---
sidebar_position: 25
---

# YugabyteDB

[YugabyteDB](https://www.yugabyte.com/) is a distributed SQL database that provides PostgreSQL compatibility with horizontal scalability and high availability across multiple regions. YugabyteDB's native vector search capabilities with the `pgvector` extension make it an excellent choice for storing and querying vector embeddings in distributed environments.

## Maven Dependency

:::note
Since YugabyteDB support is part of `langchain4j-community`, it will be available starting from version `1.8.0-beta15` or later.
:::

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-yugabytedb</artifactId>
    <version>1.8.0-beta15</version>
</dependency>
```


## APIs

The YugabyteDB integration provides three main classes:

### `YugabyteDBEmbeddingStore`

The main interface for storing and searching vector embeddings. This implements LangChain4j's `EmbeddingStore` interface and provides methods for:
- Adding embeddings (single or batch)
- Searching for similar embeddings
- Removing embeddings
- Filtering by metadata

### `YugabyteDBEngine`

Manages the database connection and connection pooling using HikariCP. This class:
- Handles JDBC connection configuration
- Manages connection pool settings (max pool size, timeouts, etc.)
- Supports both PostgreSQL JDBC driver and YugabyteDB Smart Driver
- Provides SSL/TLS configuration options

### `YugabyteDBSchema`

Defines the database schema configuration including:
- Table and column names
- Vector index type (HNSW or NoIndex)
- Distance metric (COSINE, EUCLIDEAN, DOT_PRODUCT)
- Metadata storage configuration
- Table creation settings

## Usage Examples

### Basic YugabyteDBEmbeddingStore

Here is how to create a `YugabyteDBEmbeddingStore` instance:

```java
YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
    .<builderParameters>
    .build();
```

Where `<builderParameters>` must include `dimension` and `engine`, along with other optional ones.

## Parameter Summary

### YugabyteDBEngine Parameters

| Parameter | Description | Default Value | Required/Optional |
| --- | --- | --- | --- |
| `host` | Hostname of the YugabyteDB server | `localhost` | Required if using engine builder |
| `port` | Port number of the YugabyteDB server | `5433` | Required if using engine builder |
| `database` | Name of the database to connect to | `yugabyte` | Required if using engine builder |
| `username` | Username for database authentication | `yugabyte` | Required if using engine builder |
| `password` | Password for database authentication | `""` (empty) | Required if using engine builder |
| `schema` | Database schema name | `public` | Optional |
| `usePostgreSQLDriver` | Use PostgreSQL JDBC driver instead of YugabyteDB Smart Driver | `false` | Optional |
| `useSsl` | Enable SSL/TLS for database connection | `false` | Optional |
| `sslMode` | SSL mode configuration | `disable` | Optional |
| `maxPoolSize` | Maximum number of connections in the pool | `10` | Optional |
| `minPoolSize` | Minimum number of idle connections in the pool | `5` | Optional |
| `connectionTimeout` | Connection timeout in milliseconds | `10000` | Optional |
| `idleTimeout` | Idle timeout in milliseconds | `300000` | Optional |
| `maxLifetime` | Maximum lifetime of a connection in milliseconds | `900000` | Optional |
| `applicationName` | Application name for connection identification | `langchain4j-yugabytedb` | Optional |

### YugabyteDBEmbeddingStore Parameters

| Parameter | Description | Default Value | Required/Optional |
| --- | --- | --- | --- |
| `engine` | The `YugabyteDBEngine` instance for database connections | None | **Required** |
| `dimension` | The dimensionality of the embedding vectors. This should match the embedding model being used. Use `embeddingModel.dimension()` to dynamically set it. | None | **Required** |
| `tableName` | The name of the database table used for storing embeddings | `langchain4j_embeddings` | Optional |
| `schemaName` | Database schema name | `public` | Optional |
| `idColumn` | Name of the ID column | `id` | Optional |
| `contentColumn` | Name of the content/text column | `content` | Optional |
| `embeddingColumn` | Name of the embedding vector column | `embedding` | Optional |
| `metadataColumn` | Name of the metadata column | `metadata` | Optional |
| `metricType` | Distance metric for similarity search: `COSINE`, `EUCLIDEAN`, or `DOT_PRODUCT` | `COSINE` | Optional |
| `vectorIndex` | Vector index configuration (see Index Configuration below) | `HNSWIndex` with default settings | Optional |
| `createTableIfNotExists` | Specifies whether to automatically create the embeddings table | `true` | Optional |
| `metadataStorageConfig` | Configuration object for handling metadata associated with embeddings. Supports three storage modes:<br/>• `COMBINED_JSONB`: For dynamic metadata stored in JSONB format for optimized querying (recommended)<br/>• `COMBINED_JSON`: For dynamic metadata stored as JSON<br/>• `COLUMN_PER_KEY`: For static metadata when you know the metadata keys in advance | `COMBINED_JSONB` | Optional |

### Index Configuration

#### HNSW Index Parameters

| Parameter | Description | Default Value | Required/Optional |
| --- | --- | --- | --- |
| `m` | Maximum number of connections per layer. Higher values = better recall but more memory | `16` | Optional |
| `efConstruction` | Size of dynamic candidate list during construction. Higher values = better index quality but slower build time | `64` | Optional |
| `metricType` | Distance metric: `COSINE`, `EUCLIDEAN`, or `DOT_PRODUCT` | `COSINE` | Optional |
| `name` | Custom index name | Auto-generated | Optional |

#### NoIndex

Use `new NoIndex()` for sequential scan without an index. Best for small datasets (< 10,000 vectors) or when exact results are required.

### Basic Usage

```java
// Create engine first
YugabyteDBEngine engine = YugabyteDBEngine.builder()
    .host("localhost")
    .port(5433)
    .database("yugabyte")
    .username("yugabyte")
    .password("")
    .usePostgreSQLDriver(true) // Use PostgreSQL JDBC driver
    .build();

// Minimal configuration
YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
    .engine(engine)
    .dimension(384)
    .build();

// Custom configuration
YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
    .engine(engine)
    .dimension(768)
    .tableName("my_embeddings")
    .metricType(MetricType.EUCLIDEAN)
    .build();
```

### Using YugabyteDBEngine

For more control over connection settings, use `YugabyteDBEngine`:

```java
// Create engine with custom settings
YugabyteDBEngine engine = YugabyteDBEngine.builder()
    .host("localhost")
    .port(5433)
    .database("yugabyte")
    .username("yugabyte")
    .password("")
    .maxPoolSize(20)
    .minPoolSize(5)
    .connectionTimeout("30000")
    .idleTimeout("300000")
    .maxLifetime("900000")
    .useSsl(false)
    .usePostgreSQLDriver(false) // Use YugabyteDB Smart Driver
    .build();

// Use engine in embedding store
YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
    .engine(engine)
    .dimension(384)
    .tableName("embeddings")
    .build();
```

### Vector Index Configuration

YugabyteDB supports different vector index types for similarity search optimization:

#### HNSW Index (Recommended)

```java
// Create engine
YugabyteDBEngine engine = YugabyteDBEngine.builder()
    .host("localhost")
    .port(5433)
    .database("yugabyte")
    .username("yugabyte")
    .password("")
    .build();

// HNSW index with custom parameters
HNSWIndex hnswIndex = HNSWIndex.builder()
    .m(16)                    // Maximum connections per layer
    .efConstruction(64)       // Construction quality
    .metricType(MetricType.COSINE)
    .name("my_hnsw_index")
    .build();

YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
    .engine(engine)
    .dimension(384)
    .vectorIndex(hnswIndex)
    .build();
```

#### No Index (Sequential Scan)

```java
// Create engine
YugabyteDBEngine engine = YugabyteDBEngine.builder()
    .host("localhost")
    .port(5433)
    .database("yugabyte")
    .username("yugabyte")
    .password("")
    .build();

// No index for exact search (slower but exact)
YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
    .engine(engine)
    .dimension(384)
    .vectorIndex(new NoIndex()) // Sequential scan
    .build();
```

### Adding and Searching Embeddings

```java
// Create engine first
YugabyteDBEngine engine = YugabyteDBEngine.builder()
    .host("localhost")
    .port(5433)
    .database("yugabyte")
    .username("yugabyte")
    .password("")
    .build();

// Create embedding store
YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
    .engine(engine)
    .dimension(384)
    .build();

// Add embeddings
TextSegment segment1 = TextSegment.from("YugabyteDB is a distributed SQL database");
Embedding embedding1 = embeddingModel.embed(segment1).content();
String id1 = store.add(embedding1, segment1);

TextSegment segment2 = TextSegment.from("PostgreSQL compatibility with horizontal scalability");
Embedding embedding2 = embeddingModel.embed(segment2).content();
String id2 = store.add(embedding2, segment2);

// Search embeddings
Embedding queryEmbedding = embeddingModel.embed("What is YugabyteDB?").content();
EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    .queryEmbedding(queryEmbedding)
    .maxResults(5)
    .minScore(0.7)
    .build();

List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();
matches.forEach(match -> {
    System.out.println("Score: " + match.score());
    System.out.println("Text: " + match.embedded().text());
});
```

### Metadata Storage Configuration

YugabyteDB supports different metadata storage modes:

```java
// Create engine
YugabyteDBEngine engine = YugabyteDBEngine.builder()
    .host("localhost")
    .port(5433)
    .database("yugabyte")
    .username("yugabyte")
    .password("")
    .build();

// JSONB storage (recommended for PostgreSQL compatibility)
MetadataStorageConfig jsonbConfig = MetadataStorageConfig.builder()
    .storageMode(MetadataStorageMode.COMBINED_JSONB)
    .build();

// JSON storage
MetadataStorageConfig jsonConfig = MetadataStorageConfig.builder()
    .storageMode(MetadataStorageMode.COMBINED_JSON)
    .build();

// Column-per-key storage
MetadataStorageConfig columnConfig = MetadataStorageConfig.builder()
    .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
    .build();

YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
    .engine(engine)
    .dimension(384)
    .metadataStorageConfig(jsonbConfig)
    .build();
```

### Driver Configuration

YugabyteDB supports both PostgreSQL JDBC driver and YugabyteDB Smart Driver:

```java
// PostgreSQL JDBC Driver (standard SQL compatibility)
YugabyteDBEngine postgresEngine = YugabyteDBEngine.builder()
    .host("localhost")
    .port(5433)
    .database("yugabyte")
    .username("yugabyte")
    .password("")
    .usePostgreSQLDriver(true)
    .build();

YugabyteDBEmbeddingStore postgresStore = YugabyteDBEmbeddingStore.builder()
    .engine(postgresEngine)
    .dimension(384)
    .build();

// YugabyteDB Smart Driver (advanced distributed features)
YugabyteDBEngine smartEngine = YugabyteDBEngine.builder()
    .host("localhost")
    .port(5433)
    .database("yugabyte")
    .username("yugabyte")
    .password("")
    .usePostgreSQLDriver(false) // Default: use Smart Driver
    .build();

YugabyteDBEmbeddingStore smartStore = YugabyteDBEmbeddingStore.builder()
    .engine(smartEngine)
    .dimension(384)
    .build();
```


## Index Types

### HNSW (ybhnsw) - Recommended

- **Best for**: Most use cases, especially large datasets
- **Performance**: Fast approximate similarity search with high recall
- **Parameters**: 
  - `m` (default: 16): Maximum connections per layer
  - `efConstruction` (default: 64): Construction quality

### NoIndex - Sequential Scan

- **Best for**: Small datasets (< 10,000 vectors) or when exact results are required
- **Performance**: Exact search but slower as dataset grows

## Known Limitations

- YugabyteDB requires the `pgvector` extension to be enabled for vector operations
- Vector dimensions must be consistent across all embeddings in the same table
- HNSW index parameters (`m`, `efConstruction`) affect both performance and memory usage
- Sequential scan (NoIndex) is only recommended for small datasets (< 10,000 vectors)

## Performance Considerations

- **HNSW Index**: Best for production use with large datasets, provides fast approximate search
- **NoIndex**: Only suitable for small datasets or when exact results are required
- **Connection Pooling**: Configure `maxPoolSize` and `minPoolSize` based on your workload
- **Driver Choice**: PostgreSQL JDBC driver is recommended by YugabyteDB for better compatibility

## Examples

:::note
Code examples demonstrating YugabyteDB integration will be added soon.
:::
