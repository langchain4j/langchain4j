---
sidebar_position: 18
---

# PGVector

LangChain4j integrates seamlessly with [PGVector](https://github.com/pgvector/pgvector), allowing developers to store and query vector embeddings directly in PostgreSQL. This integration is ideal for applications like semantic search, recommendation systems, and more.


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>0.36.2</version>
</dependency>
```

## Gradle Dependency

```implementation 'dev.langchain4j:langchain4j-pgvector:0.36.2'```

## APIs

- `PgVectorEmbeddingStore`

## Parameter Summary

| Plain Java Property      | Spring Application Property | Description                                                                 | Default Value       | Required/Optional                                                                                                                                                                                                                                                                                |
|--------------------------|-----------------------------|-----------------------------------------------------------------------------|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `datasource`             | `coming soon`               | The `DataSource` object used for database connections.                      | None                | Required if `host`, `port`, `user`, `password`, and `database` are not provided individually.                                                                                                                                                                                                    |
| `table`                  | `coming soon`               | The name of the database table used for storing embeddings.                 | None                | Required                                                                                                                                                                                                                                                                                         |
| `dimension`              | `coming soon`               | The dimensionality of the embedding vectors.                               | None                | Required                                                                                                                                                                                                                                                                                         |
| `useIndex`               | `coming soon`               | An IVFFlat index divides vectors into lists, and then searches a subset of those lists closest to the query vector. It has faster build times and uses less memory than HNSW but has lower query performance (in terms of speed-recall tradeoff). Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index.     | `false`             | Optional                                                                                                                                                                                                                                                                                         |
| `indexListSize`          | `coming soon`               | The number of lists for the IVFFlat index. Required only if `useIndex` is `true`.                                | None                | When Required: <br> If `useIndex` is `true`, `indexListSize` must be provided and must be greater than zero. Otherwise, the program will throw an exception during table initialization. <br> When Optional: <br> If `useIndex` is `false`, this property is ignored and doesn’t need to be set. |
| `createTable`            | `coming soon`               | Specifies whether to automatically create the embeddings table.             | `true`              | Optional                                                                                                                                                                                                                                                                                         |
| `dropTableFirst`         | `coming soon`               | Specifies whether to drop the table before recreating it (useful for tests).| `false`             | Optional                                                                                                                                                                                                                                                                                         |
| `metadataStorageConfig`  | `coming soon`               | Configuration object for handling metadata associated with embeddings. Supports three storage modes: <ul><li>**COLUMN_PER_KEY**: For static metadata when you know the metadata keys in advance.</li><li>**COMBINED_JSON**: For dynamic metadata when you don’t know the metadata keys in advance. Stores data as JSON. (Default)</li><li>**COMBINED_JSONB**: Similar to JSON, but stored in binary format for optimized querying on large datasets.</li></ul> | Default configuration using `COMBINED_JSON` mode. | Optional. If not set, a default configuration is used with `COMBINED_JSON`.|
| `host`                   | `coming soon`               | Hostname of the PostgreSQL server.                                          | None                | Required if `DataSource` is not provided                                                                                                                                                                                                                                                         |
| `port`                   | `coming soon`               | Port number of the PostgreSQL server.                                       | None                | Required if `DataSource` is not provided                                                                                                                                                                                                                                                         |
| `user`                   | `coming soon`               | Username for database authentication.                                       | None                | Required if `DataSource` is not provided                                                                                                                                                                                                                                                         |
| `password`               | `coming soon`               | Password for database authentication.                                       | None                | Required if `DataSource` is not provided                                                                                                                                                                                                                                                         |
| `database`               | `coming soon`               | Name of the database to connect to.                                         | None                | Required if `DataSource` is not provided                                                                                                                                                                                                                                                         |


## Examples
To demonstrate the capabilities of PGVector, you can use a Dockerized PostgreSQL setup. It leverages Testcontainers to run PostgreSQL with PGVector.

#### Quick Start with Docker
To quickly set up a PostgreSQL instance with the PGVector extension, you can use the following Docker command:
```
docker run --rm --name langchain4j-postgres-test-container -p 5432:5432 -e POSTGRES_USER=my_user -e POSTGRES_PASSWORD=my_password pgvector/pgvector
```

#### Explanation of the Command:<br>
```docker run```: Runs a new container.<br>
```--rm```: Automatically removes the container after it stops, ensuring no residual data.<br>
```--name langchain4j-postgres-test-container```: Names the container langchain4j-postgres-test-container for easy identification.<br>
```-p 5432:5432```: Maps port 5432 on your local machine to port 5432 in the container.<br>
```-e POSTGRES_USER=my_user```: Sets the PostgreSQL username to my_user.<br>
```-e POSTGRES_PASSWORD=my_password```: Sets the PostgreSQL password to my_password.<br>
```gvector/pgvector```: Specifies the Docker image to use, pre-configured with the PGVector extension.<br>

```
// Initialize the PGVector embedding store
        EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
                .host("localhost")          // Hostname for the Docker container
                .port(5432)                 // Port mapped from the Docker container
                .database("postgres")       // Default database created by the container
                .user("my_user")            // Username set in the Docker command
                .password("my_password")    // Password set in the Docker command
                .table("test")              // Custom table name
                .dimension(384)             // Embedding dimensionality
                .build();
```

- [Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/pgvector-example/src/main/java)
