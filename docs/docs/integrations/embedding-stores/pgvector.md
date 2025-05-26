---
sidebar_position: 19
---

# PGVector

LangChain4j integrates seamlessly with [PGVector](https://github.com/pgvector/pgvector), allowing developers to store
and query vector embeddings directly in PostgreSQL. This integration is ideal for applications like semantic search,
RAG, and more.

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

## Gradle Dependency

```implementation 'dev.langchain4j:langchain4j-pgvector:1.0.1-beta6'```

## APIs

- `PgVectorEmbeddingStore`

## Parameter Summary

| Plain Java Property     | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                    | Default Value   | Required/Optional                                                                                                                                                                                                                                                                 |
|-------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `datasource`            | The `DataSource` object used for database connections. If not provided, `host`, `port`, `user`, `password`, and `database` must be provided individually.                                                                                                                                                                                                                                                                                                      | None            | Required if `host`, `port`, `user`, `password`, and `database` are not provided individually.                                                                                                                                                                                     |
| `host`                  | Hostname of the PostgreSQL server. Required if `DataSource` is not provided.                                                                                                                                                                                                                                                                                                                                                                                   | None            | Required if `DataSource` is not provided                                                                                                                                                                                                                                          |
| `port`                  | Port number of the PostgreSQL server. Required if `DataSource` is not provided.                                                                                                                                                                                                                                                                                                                                                                                | None            | Required if `DataSource` is not provided                                                                                                                                                                                                                                          |
| `user`                  | Username for database authentication. Required if `DataSource` is not provided.                                                                                                                                                                                                                                                                                                                                                                                | None            | Required if `DataSource` is not provided                                                                                                                                                                                                                                          |
| `password`              | Password for database authentication. Required if `DataSource` is not provided.                                                                                                                                                                                                                                                                                                                                                                                | None            | Required if `DataSource` is not provided                                                                                                                                                                                                                                          |
| `database`              | Name of the database to connect to. Required if `DataSource` is not provided.                                                                                                                                                                                                                                                                                                                                                                                  | None            | Required if `DataSource` is not provided                                                                                                                                                                                                                                          |
| `table`                 | The name of the database table used for storing embeddings.                                                                                                                                                                                                                                                                                                                                                                                                    | None            | Required                                                                                                                                                                                                                                                                          |
| `dimension`             | The dimensionality of the embedding vectors. This should match the embedding model being used. Use `embeddingModel.dimension()` to dynamically set it.                                                                                                                                                                                                                                                                                                         | None            | Required                                                                                                                                                                                                                                                                          |
| `useIndex`              | An IVFFlat index divides vectors into lists, and then searches a subset of those lists closest to the query vector. It has faster build times and uses less memory than HNSW but has lower query performance (in terms of speed-recall tradeoff). Should use [IVFFlat](https://github.com/pgvector/pgvector#ivfflat) index.                                                                                                                                    | `false`         | Optional                                                                                                                                                                                                                                                                          |
| `indexListSize`         | The number of lists for the IVFFlat index.                                                                                                                                                                                                                                                                                                                                                                                                                     | None            | When Required: If `useIndex` is `true`, `indexListSize` must be provided and must be greater than zero. Otherwise, the program will throw an exception during table initialization. When Optional: If `useIndex` is `false`, this property is ignored and doesn’t need to be set. |
| `createTable`           | Specifies whether to automatically create the embeddings table.                                                                                                                                                                                                                                                                                                                                                                                                | `true`          | Optional                                                                                                                                                                                                                                                                          |
| `dropTableFirst`        | Specifies whether to drop the table before recreating it (useful for tests).                                                                                                                                                                                                                                                                                                                                                                                   | `false`         | Optional                                                                                                                                                                                                                                                                          |
| `metadataStorageConfig` | Configuration object for handling metadata associated with embeddings. Supports three storage modes: <ul><li>**COLUMN_PER_KEY**: For static metadata when you know the metadata keys in advance.</li><li>**COMBINED_JSON**: For dynamic metadata when you don’t know the metadata keys in advance. Stores data as JSON. (Default)</li><li>**COMBINED_JSONB**: Similar to JSON, but stored in binary format for optimized querying on large datasets.</li></ul> | `COMBINED_JSON` | Optional. If not set, a default configuration is used with `COMBINED_JSON`.                                                                                                                                                                                                       |

## Examples

To demonstrate the capabilities of PGVector, you can use a Dockerized PostgreSQL setup. It leverages Testcontainers to
run PostgreSQL with PGVector.

#### Quick Start with Docker

To quickly set up a PostgreSQL instance with the PGVector extension, you can use the following Docker command:

```
docker run --rm --name langchain4j-postgres-test-container -p 5432:5432 -e POSTGRES_USER=my_user -e POSTGRES_PASSWORD=my_password pgvector/pgvector
```

#### Explanation of the Command:

- ```docker run```: Runs a new container.
- ```--rm```: Automatically removes the container after it stops, ensuring no residual data.
- ```--name langchain4j-postgres-test-container```: Names the container langchain4j-postgres-test-container for easy
  identification.
- ```-p 5432:5432```: Maps port 5432 on your local machine to port 5432 in the container.
- ```-e POSTGRES_USER=my_user```: Sets the PostgreSQL username to my_user.
- ```-e POSTGRES_PASSWORD=my_password```: Sets the PostgreSQL password to my_password.
- ```pgvector/pgvector```: Specifies the Docker image to use, pre-configured with the PGVector extension.

Here are two code examples showing how to create a PgVectorEmbeddingStore. The first uses only the required parameters,
while the second configures all available parameters.

1. Only Required Parameters

```java
EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
        .host("localhost")                           // Required: Host of the PostgreSQL instance
        .port(5432)                                  // Required: Port of the PostgreSQL instance
        .database("postgres")                        // Required: Database name
        .user("my_user")                             // Required: Database user
        .password("my_password")                     // Required: Database password
        .table("my_embeddings")                      // Required: Table name to store embeddings
        .dimension(embeddingModel.dimension())       // Required: Dimension of embeddings
        .build();
```

2. All Parameters Set

In this variant, we include all the commonly used optional parameters like DataSource, useIndex, indexListSize,
createTable, dropTableFirst, and metadataStorageConfig. Adjust these values as needed:

 ```java
DataSource dataSource = ...;                 // Pre-configured DataSource, if available

EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
        // Connection and table parameters
        .datasource(dataSource)                      // Optional: If using a DataSource instead of host/port credentials
        .host("localhost")
        .port(5432)
        .database("postgres")
        .user("my_user")
        .password("my_password")
        .table("my_embeddings")

        // Embedding dimension
        .dimension(embeddingModel.dimension())      // Required: Must match the embedding model’s output dimension

        // Indexing and performance options
        .useIndex(true)                             // Enable IVFFlat index
        .indexListSize(100)                         // Number of lists for IVFFlat index

        // Table creation options
        .createTable(true)                          // Automatically create the table if it doesn’t exist
        .dropTableFirst(false)                      // Don’t drop the table first (set to true if you want a fresh start)

        // Metadata storage format
        .metadataStorageConfig(MetadataStorageConfig.combinedJsonb()) // Store metadata as a combined JSONB column

        .build();
```

Use the first example if you just want the minimal configuration to get started quickly.
The second example shows how you can leverage all available builder parameters for more control and customization.

- [Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/pgvector-example/src/main/java)
