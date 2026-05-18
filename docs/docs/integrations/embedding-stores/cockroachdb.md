---
sidebar_position: 33
---

# CockroachDB

[CockroachDB](https://www.cockroachlabs.com/) is a distributed SQL database
that speaks the PostgreSQL wire protocol. Since v24.2 it ships a native
`VECTOR` column type, and since v25.2 it offers a distributed approximate
nearest neighbour index called **C-SPANN**. The
`langchain4j-community-cockroachdb` module integrates both with LangChain4j as
an `EmbeddingStore` and a `ChatMemoryStore`.

## Maven Dependency

:::note
Since CockroachDB support is part of `langchain4j-community`, it will be
available starting from version `1.16.0-beta26` or later.
:::

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-cockroachdb</artifactId>
    <version>1.16.0-beta26</version>
</dependency>
```

## Version Requirements

| Feature | Minimum CockroachDB version |
| --- | --- |
| `VECTOR(n)` column type | v24.2 |
| `CREATE VECTOR INDEX` (C-SPANN) | v25.2 |
| Row-level TTL via `ttl_expiration_expression` | v23.1 |

On CockroachDB v25.2, vector indexes are gated by a cluster setting. Enable
it once per cluster before creating a store with a `CSpannIndex`:

```sql
SET CLUSTER SETTING feature.vector_index.enabled = true;
```

## APIs

The CockroachDB integration provides the following classes:

### `CockroachDbEmbeddingStore`

The main interface for storing and searching vector embeddings. Implements
LangChain4j's `EmbeddingStore<TextSegment>` and supports:

- Adding embeddings (single or batch via JDBC batch insert)
- Approximate nearest neighbour search using the C-SPANN index
- Metadata filtering against a `JSONB` column
- Removing embeddings by id, by filter, or in bulk
- Optional multi-tenancy via a namespace column
- Optional per-query `vector_search_beam_size` tuning

### `CockroachDbEngine`

Wraps a HikariCP `DataSource` and handles connection pooling. Accepts either
individual host/port/database fields, an existing `DataSource`, or a
connection string. The Python-style `cockroachdb://` URL scheme is rewritten
to `jdbc:postgresql://` so the same connection strings used by the Python
`langchain-cockroachdb` library work unchanged.

### `CockroachDbSchema`

Configures the embedding table layout: table and column names, vector
dimension, distance metric, optional namespace column for multi-tenancy, and
the chosen vector index strategy.

### `CockroachDbChatMemoryStore`

Implements LangChain4j's `ChatMemoryStore`. Persists serialised chat messages
in a `JSONB` column ordered by insertion index, with optional row-level TTL.

## Quick Start

```java
import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEmbeddingStore;
import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEngine;
import dev.langchain4j.community.store.embedding.cockroachdb.index.CSpannIndex;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;

EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

CockroachDbEngine engine = CockroachDbEngine.builder()
        .host("localhost")
        .port(26257)
        .database("defaultdb")
        .username("root")
        .password("")
        .sslMode("disable")
        .build();

CockroachDbEmbeddingStore store = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(embeddingModel.dimension())
        .vectorIndex(CSpannIndex.builder().build())
        .build();

TextSegment segment = TextSegment.from("CockroachDB is a distributed SQL database.");
Embedding embedding = embeddingModel.embed(segment).content();
store.add(embedding, segment);
```

## Connection String Formats

The Python-style `cockroachdb://` scheme is rewritten to `jdbc:postgresql://`
automatically. The following forms are all accepted by
`CockroachDbEngine.fromConnectionString`:

| Form | Example |
| --- | --- |
| Python style | `cockroachdb://root@localhost:26257/defaultdb?sslmode=disable` |
| psycopg style | `cockroachdb+psycopg://user:pw@host:26257/db` |
| libpq style | `postgresql://user@host:26257/db` |
| JDBC style | `jdbc:postgresql://localhost:26257/defaultdb` |

For CockroachDB Cloud, paste the connection string from the cluster console:

```java
CockroachDbEngine engine = CockroachDbEngine.fromConnectionString(
        "cockroachdb://USER:PASSWORD@HOST:26257/DATABASE?sslmode=verify-full");
```

## Parameter Summary

### CockroachDbEngine Parameters

| Parameter | Description | Default Value | Required/Optional |
| --- | --- | --- | --- |
| `host` | Hostname of the CockroachDB server | `localhost` | Required (if not using `connectionString`) |
| `port` | Port number of the CockroachDB server | `26257` | Required (if not using `connectionString`) |
| `database` | Name of the database to connect to | `defaultdb` | Required (if not using `connectionString`) |
| `username` | Username for database authentication | `root` | Required |
| `password` | Password for database authentication | `""` (empty) | Optional |
| `schema` | Database schema name | `public` | Optional |
| `sslMode` | SSL mode (`disable`, `require`, `verify-full`, etc.) | `disable` | Optional |
| `maxPoolSize` | Maximum number of connections in the pool | `10` | Optional |
| `minPoolSize` | Minimum idle connections in the pool | `5` | Optional |
| `connectionTimeoutMs` | Connection timeout in milliseconds | `10000` | Optional |
| `idleTimeoutMs` | Idle timeout in milliseconds | `300000` | Optional |
| `maxLifetimeMs` | Maximum lifetime of a connection in milliseconds | `3600000` | Optional |
| `connectionString` | Full connection URL (overrides individual fields when set) | None | Optional |

### CockroachDbEmbeddingStore Parameters

| Parameter | Description | Default Value | Required/Optional |
| --- | --- | --- | --- |
| `engine` | The `CockroachDbEngine` instance | None | **Required** |
| `dimension` | Dimensionality of the embedding vectors | None | **Required** |
| `tableName` | Name of the embeddings table | `embeddings` | Optional |
| `schemaName` | Database schema name | `public` | Optional |
| `metricType` | Distance metric: `COSINE`, `EUCLIDEAN`, or `DOT_PRODUCT` | `COSINE` | Optional |
| `vectorIndex` | `CSpannIndex` or `NoIndex` | `NoIndex` (sequential scan) | Optional |
| `namespaceColumn` | Name of the optional tenant column | `null` (multi-tenancy disabled) | Optional |
| `namespace` | Tenant value applied on every read and write | `null` | Optional, requires `namespaceColumn` |
| `searchBeamSize` | Per-query `vector_search_beam_size` session variable | `null` (CockroachDB default) | Optional |
| `createTableIfNotExists` | Create the table at builder time | `true` | Optional |
| `createTsvectorColumn` | Add a generated `tsvector` column + GIN index | `false` | Optional |

### Vector Index Configuration

#### CSpannIndex Parameters (CockroachDB v25.2+)

| Parameter | Description | Default Value | Required/Optional |
| --- | --- | --- | --- |
| `name` | Custom index name | `{table}_{column}_vector_idx` | Optional |
| `minPartitionSize` | Minimum partition size (passed via `WITH`) | CockroachDB default | Optional |
| `maxPartitionSize` | Maximum partition size (passed via `WITH`) | CockroachDB default | Optional |

C-SPANN picks the distance function from the query operator (`<=>` for
cosine, `<->` for L2, `<#>` for inner product), so `MetricType` is selected
at query time on the store and is not bound to the index.

The emitted DDL has the form:

```sql
CREATE VECTOR INDEX IF NOT EXISTS embeddings_embedding_vector_idx
  ON public.embeddings (embedding)
  WITH (min_partition_size = 16, max_partition_size = 128);
```

#### NoIndex

Use `new NoIndex()` for sequential scan without an index. Best for small
datasets or tests.

## Multi-Tenancy

To scope rows by tenant, add a `namespaceColumn` to the schema and configure
a namespace value on each store instance. The column is added as a prefix to
the C-SPANN index so per-tenant queries stay fast:

```java
CockroachDbEmbeddingStore tenantA = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(embeddingModel.dimension())
        .namespaceColumn("tenant_id")
        .namespace("acme")
        .vectorIndex(CSpannIndex.builder().build())
        .build();
```

The generated index becomes:

```sql
CREATE VECTOR INDEX ... ON embeddings (tenant_id, embedding);
```

Every read and write performed through this store is filtered to
`tenant_id = 'acme'`.

## Chat Memory

```java
import dev.langchain4j.community.store.memory.chat.cockroachdb.CockroachDbChatMemoryStore;
import java.time.Duration;

CockroachDbChatMemoryStore memory = CockroachDbChatMemoryStore.builder()
        .engine(engine)
        .tableName("chat_memory")
        .ttl(Duration.ofDays(7))
        .ttlJobCron("@daily")
        .build();
```

Setting `ttl` enables CockroachDB's row-level TTL on the chat memory table
via `ALTER TABLE ... SET (ttl_expiration_expression, ttl_job_cron)`. The
expression form is used so the TTL change does not rewrite the full table.
Call `memory.disableTtl()` to turn it off.

## Retries

CockroachDB returns SQLSTATE `40001` when a transaction must be retried
under its default `SERIALIZABLE` isolation. The store wraps each unit of
work in a retry loop with exponential backoff and jitter (5 attempts by
default, starting at 100 ms, doubling up to 10 seconds). No additional
configuration is required.

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

## Examples

- [CockroachDbEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/cockroachdb-example/src/main/java/CockroachDbEmbeddingStoreExample.java) - Basic example with Testcontainers
