---
sidebar_position: 33
---

# CockroachDB

[CockroachDB](https://www.cockroachlabs.com/) is a distributed SQL database that
speaks the PostgreSQL wire protocol. Since v24.2 it ships a native `VECTOR`
column type, and since v25.2 it offers a distributed approximate nearest
neighbour index called **C-SPANN**. The `langchain4j-community-cockroachdb`
module integrates both with LangChain4j as:

- a vector `EmbeddingStore<TextSegment>` (`CockroachDbEmbeddingStore`)
- a `ChatMemoryStore` (`CockroachDbChatMemoryStore`)

The Java module mirrors the feature set of the official Python
[`langchain-cockroachdb`](https://github.com/cockroachdb/langchain-cockroachdb)
library where the Java equivalents exist.

## Version Requirements

| Feature | Minimum CockroachDB version |
| --- | --- |
| `VECTOR(n)` column type | v24.2 |
| `CREATE VECTOR INDEX` (C-SPANN) | v25.2 |
| Row-level TTL via `ttl_expiration_expression` | v23.1 |

On CockroachDB v25.2, vector indexes are gated by a cluster setting. Enable it
once per cluster before creating a store with a `CSpannIndex`:

```sql
SET CLUSTER SETTING feature.vector_index.enabled = true;
```

## Maven Dependency

:::note
Since CockroachDB support is part of `langchain4j-community`, it will be
available starting from version `1.17.0-beta27` or later.
:::

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-cockroachdb</artifactId>
    <version>1.17.0-beta27</version>
</dependency>
```

If you import the Community BOM, you can omit the version.

## APIs

The module exposes four public classes:

### `CockroachDbEngine`

Wraps a HikariCP `DataSource` and handles connection pooling. Builds from
individual `host`/`port`/`database`/`username`/`password` fields, from a full
connection string (the Python-style `cockroachdb://` scheme is rewritten to
`jdbc:postgresql://` automatically), or from an existing `DataSource` via
`CockroachDbEngine.from(dataSource)`.

### `CockroachDbSchema`

Encapsulates the embedding table layout: table and column names, vector
dimension, distance metric, optional namespace column for multi-tenancy, the
chosen vector index strategy, and an optional generated `tsvector` column for
future hybrid search.

### `CockroachDbEmbeddingStore`

Implements LangChain4j's `EmbeddingStore<TextSegment>` against the native
CockroachDB `VECTOR` column. Supports batch insert, JSONB metadata
filtering, removal by id / by `Filter` / in bulk, optional namespace scoping,
and optional per-query `vector_search_beam_size` tuning for C-SPANN.

### `CockroachDbChatMemoryStore`

Implements LangChain4j's `ChatMemoryStore`. Persists serialised chat messages
in a JSONB column ordered by an explicit insertion index, with optional
row-level TTL.

## Connecting

`CockroachDbEngine` wraps a `HikariDataSource`. You can build one from a
connection string or from individual fields.

```java
import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEngine;

CockroachDbEngine engine = CockroachDbEngine.builder()
        .host("localhost")
        .port(26257)
        .database("defaultdb")
        .username("root")
        .password("")
        .sslMode("disable")
        .build();
```

The builder also accepts a full connection string. The Python-style
`cockroachdb://` scheme is rewritten to `jdbc:postgresql://` automatically,
so you can paste the same URL the Python library uses:

```java
CockroachDbEngine engine = CockroachDbEngine.fromConnectionString(
        "cockroachdb://root@localhost:26257/defaultdb?sslmode=disable");
```

If you already have a `DataSource`, use `CockroachDbEngine.from(dataSource)`.

## Vector store

A minimal vector store uses sequential scan (`NoIndex`), which is appropriate
for small datasets and tests:

```java
import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;

EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

CockroachDbEmbeddingStore store = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(model.dimension())
        .tableName("embeddings")
        .build();

TextSegment segment = TextSegment.from("Cockroaches are surprisingly resilient.");
Embedding embedding = model.embed(segment).content();
store.add(embedding, segment);
```

For production workloads on CockroachDB v25.2+, add a C-SPANN vector index:

```java
import dev.langchain4j.community.store.embedding.cockroachdb.index.CSpannIndex;

CockroachDbEmbeddingStore store = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(model.dimension())
        .vectorIndex(CSpannIndex.builder()
                .minPartitionSize(16)
                .maxPartitionSize(128)
                .build())
        .build();
```

The DDL emitted for the index is:

```sql
CREATE VECTOR INDEX IF NOT EXISTS embeddings_embedding_vector_idx
  ON public.embeddings (embedding)
  WITH (min_partition_size = 16, max_partition_size = 128);
```

C-SPANN picks the distance function from the query operator (`<=>` for cosine,
`<->` for L2, `<#>` for inner product), so `MetricType` is selected at query
time on the store, not bound to the index.

### Searching

`EmbeddingSearchRequest` works the same as in any other LangChain4j store:

```java
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

EmbeddingSearchResult<TextSegment> result = store.search(
        EmbeddingSearchRequest.builder()
                .queryEmbedding(model.embed("resilience").content())
                .maxResults(5)
                .minScore(0.6)
                .build());

result.matches().forEach(m ->
        System.out.printf("%s (%.3f) %s%n", m.embeddingId(), m.score(), m.embedded().text()));
```

### Tuning C-SPANN at query time

CockroachDB exposes a session variable, `vector_search_beam_size`, that
controls the recall/latency tradeoff. Set it on the store builder to wrap
each search in a transaction that scopes the setting with `SET LOCAL`:

```java
CockroachDbEmbeddingStore store = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(model.dimension())
        .vectorIndex(CSpannIndex.builder().build())
        .searchBeamSize(32)
        .build();
```

Higher values trade latency for recall. The default beam size is decided by
CockroachDB if you leave the field unset.

### Metadata filtering

Metadata is stored in a JSONB column and filtered at query time using
LangChain4j `Filter` expressions:

```java
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

EmbeddingSearchResult<TextSegment> result = store.search(
        EmbeddingSearchRequest.builder()
                .queryEmbedding(query)
                .maxResults(10)
                .filter(MetadataFilterBuilder.metadataKey("category").isEqualTo("biology")
                        .and(MetadataFilterBuilder.metadataKey("year").isGreaterThan(2020)))
                .build());
```

Comparison filters (`>`, `>=`, `<`, `<=`) cast the JSONB value to `numeric`.
Equality on strings compares JSON text. The filter key must contain only
alphanumeric characters, dots, underscores or hyphens.

### Multi-tenancy with a namespace column

To scope rows by tenant, add a `namespaceColumn` to the schema and configure a
namespace value on each store instance. The column is added as a prefix to the
C-SPANN index so per-tenant queries stay fast:

```java
CockroachDbEmbeddingStore tenantA = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(model.dimension())
        .namespaceColumn("tenant_id")
        .namespace("acme")
        .vectorIndex(CSpannIndex.builder().build())
        .build();
```

The generated index becomes `CREATE VECTOR INDEX ... ON embeddings (tenant_id, embedding)`,
and every read/write performed through this store is filtered to `tenant_id = 'acme'`.

### Optional full-text column

If you intend to combine vector search with full-text search later, enable a
generated `tsvector` column at table creation time. A GIN index is created
alongside it:

```java
CockroachDbEmbeddingStore store = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(model.dimension())
        .createTsvectorColumn(true)
        .build();
```

Hybrid (vector + FTS) query execution is not yet implemented; the column is
created so it can be used by application code or a future release.

## Chat memory

`CockroachDbChatMemoryStore` implements `ChatMemoryStore` and persists
serialised chat messages in a JSONB column ordered by insertion time:

```java
import dev.langchain4j.community.store.memory.chat.cockroachdb.CockroachDbChatMemoryStore;

CockroachDbChatMemoryStore memory = CockroachDbChatMemoryStore.builder()
        .engine(engine)
        .tableName("chat_memory")
        .build();
```

The schema is:

```sql
CREATE TABLE chat_memory (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id TEXT NOT NULL,
  message JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX chat_memory_session_idx ON chat_memory (session_id, created_at);
```

`updateMessages` replaces the full session inside a transaction, so partial
writes are not visible to readers.

### Row-level TTL

CockroachDB can expire rows automatically. Pass a `ttl` duration to enable
[row-level TTL](https://www.cockroachlabs.com/docs/stable/row-level-ttl) on
the chat memory table:

```java
import java.time.Duration;

CockroachDbChatMemoryStore memory = CockroachDbChatMemoryStore.builder()
        .engine(engine)
        .tableName("chat_memory")
        .ttl(Duration.ofDays(7))
        .ttlJobCron("@daily")
        .build();
```

The schema setup emits:

```sql
ALTER TABLE chat_memory SET (
  ttl_expiration_expression = $$(created_at + '7 days')$$,
  ttl_job_cron = '@daily'
);
```

To disable TTL on an existing table:

```java
memory.disableTtl();
```

## Retries

CockroachDB returns SQLSTATE `40001` when a transaction must be retried under
its default `SERIALIZABLE` isolation. The store wraps each unit of work in a
retry loop with exponential backoff and jitter (5 attempts by default,
starting at 100 ms, doubling up to 10 seconds). No additional configuration
is needed.

## Connection string formats

The following forms are all accepted by `CockroachDbEngine.fromConnectionString`:

| Form | Example |
| --- | --- |
| Python style | `cockroachdb://root@localhost:26257/defaultdb?sslmode=disable` |
| psycopg style | `cockroachdb+psycopg://user:pw@host:26257/db` |
| libpq style | `postgresql://user@host:26257/db` |
| JDBC style | `jdbc:postgresql://localhost:26257/defaultdb` |

For CockroachDB Cloud, use the connection string from the cluster console,
typically:

```
cockroachdb://USER:PASSWORD@HOST:26257/DATABASE?sslmode=verify-full
```

## Parameter Summary

### `CockroachDbEngine` parameters

| Parameter | Description | Default | Required/Optional |
| --- | --- | --- | --- |
| `host` | Hostname of the CockroachDB server | `localhost` | Required (if no `connectionString`) |
| `port` | Port number of the CockroachDB server | `26257` | Required (if no `connectionString`) |
| `database` | Database to connect to | `defaultdb` | Required (if no `connectionString`) |
| `username` | Username for authentication | `root` | Required |
| `password` | Password for authentication | `""` (empty) | Optional |
| `schema` | Default schema name | `public` | Optional |
| `sslMode` | SSL mode (`disable`, `require`, `verify-full`, etc.) | `disable` | Optional |
| `maxPoolSize` | Maximum HikariCP pool size | `10` | Optional |
| `minPoolSize` | Minimum idle connections | `5` | Optional |
| `connectionTimeoutMs` | Connection timeout in milliseconds | `10000` | Optional |
| `idleTimeoutMs` | Idle timeout in milliseconds | `300000` | Optional |
| `maxLifetimeMs` | Maximum connection lifetime in milliseconds | `3600000` | Optional |
| `connectionString` | Full URL; overrides individual host/port/db when set | `null` | Optional |

### `CockroachDbEmbeddingStore` parameters

| Parameter | Description | Default | Required/Optional |
| --- | --- | --- | --- |
| `engine` | `CockroachDbEngine` instance | None | **Required** |
| `dimension` | Embedding vector dimension | None | **Required** |
| `tableName` | Embeddings table name | `embeddings` | Optional |
| `schemaName` | Database schema name | `public` | Optional |
| `metricType` | Distance metric: `COSINE`, `EUCLIDEAN`, or `DOT_PRODUCT` | `COSINE` | Optional |
| `vectorIndex` | `CSpannIndex` or `NoIndex` | `NoIndex` (sequential scan) | Optional |
| `namespaceColumn` | Tenant column name for multi-tenancy | `null` (disabled) | Optional |
| `namespace` | Tenant value applied on every read and write | `null` | Optional, requires `namespaceColumn` |
| `searchBeamSize` | Per-query `vector_search_beam_size` session variable | `null` (CockroachDB default) | Optional |
| `createTableIfNotExists` | Create the table at build time | `true` | Optional |
| `createTsvectorColumn` | Add a generated `tsvector` column + GIN index | `false` | Optional |

### `CSpannIndex` parameters (CockroachDB v25.2+)

| Parameter | Description | Default | Required/Optional |
| --- | --- | --- | --- |
| `name` | Custom index name | `{table}_{column}_vector_idx` | Optional |
| `minPartitionSize` | Minimum partition size (emitted via `WITH`) | CockroachDB default | Optional |
| `maxPartitionSize` | Maximum partition size (emitted via `WITH`) | CockroachDB default | Optional |

### `CockroachDbChatMemoryStore` parameters

| Parameter | Description | Default | Required/Optional |
| --- | --- | --- | --- |
| `engine` | `CockroachDbEngine` instance | None | **Required** |
| `tableName` | Chat history table name | `message_store` | Optional |
| `schemaName` | Database schema name | `public` | Optional |
| `ttl` | Row-level TTL duration; enables CockroachDB TTL when set | `null` (disabled) | Optional |
| `ttlJobCron` | TTL job schedule | `@daily` | Optional, requires `ttl` |
| `createTableIfNotExists` | Create the table at build time | `true` | Optional |

## Example

A minimal end-to-end RAG demo that boots a CockroachDB Testcontainer,
indexes two text segments, and runs a similarity search:

```java
import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEmbeddingStore;
import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEngine;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.testcontainers.containers.CockroachContainer;

public class CockroachDbEmbeddingStoreExample {

    public static void main(String[] args) {
        try (CockroachContainer cockroach = new CockroachContainer("cockroachdb/cockroach:latest-v25.2")) {
            cockroach.start();

            CockroachDbEngine engine = CockroachDbEngine.builder()
                    .connectionString(cockroach.getJdbcUrl())
                    .username(cockroach.getUsername())
                    .password(cockroach.getPassword())
                    .build();

            EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

            EmbeddingStore<TextSegment> embeddingStore = CockroachDbEmbeddingStore.builder()
                    .engine(engine)
                    .dimension(embeddingModel.dimension())
                    .tableName("demo_embeddings")
                    .build();

            TextSegment segment1 = TextSegment.from("I like football.");
            Embedding embedding1 = embeddingModel.embed(segment1).content();
            embeddingStore.add(embedding1, segment1);

            TextSegment segment2 = TextSegment.from("The weather is good today.");
            Embedding embedding2 = embeddingModel.embed(segment2).content();
            embeddingStore.add(embedding2, segment2);

            Embedding queryEmbedding = embeddingModel.embed("What is your favourite sport?").content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(1)
                    .build();

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
            EmbeddingMatch<TextSegment> match = matches.get(0);

            System.out.println(match.score());           // ~0.81
            System.out.println(match.embedded().text()); // I like football.

            engine.close();
        }
    }
}
```

The example uses the default sequential-scan index so it runs on any
CockroachDB v24.2 or later without extra cluster setup. To switch to the
C-SPANN distributed ANN index on v25.2 or later, enable the feature flag once
per cluster and pass `CSpannIndex.builder().build()` to the store via
`.vectorIndex(...)`:

```sql
SET CLUSTER SETTING feature.vector_index.enabled = true;
```

A more complete runnable version lives under
[langchain4j-examples/cockroachdb-example](https://github.com/langchain4j/langchain4j-examples/blob/main/cockroachdb-example/src/main/java/CockroachDbEmbeddingStoreExample.java).

## Known Limitations

- C-SPANN vector indexes require CockroachDB v25.2 or later, and the
  `feature.vector_index.enabled` cluster setting must be enabled.
- Vector values are sent as text and cast with `?::vector` because
  CockroachDB's pgwire layer does not accept the binary format for the
  `VECTOR` type.
- Hybrid (vector + full-text) query execution is not implemented yet. The
  tsvector column and GIN index can be created via `createTsvectorColumn`
  for use by application code or a future release.
- The Python `langchain-cockroachdb` library also ships a LangGraph
  checkpointer (`CockroachDBSaver` and `AsyncCockroachDBSaver`). The
  Java equivalent lives in the third-party [langgraph4j](https://github.com/langgraph4j/langgraph4j)
  project as `langgraph4j-cockroachdb-saver`. langgraph4j's checkpoint
  contract has no async API, so only the sync `CockroachDBSaver` is
  provided; callers on JDK 21 or later can invoke it from a virtual
  thread for non-blocking concurrency.
