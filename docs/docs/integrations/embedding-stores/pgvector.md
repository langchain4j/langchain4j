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
    <version>0.36.2</version>  <!-- Use the latest version -->
</dependency>
```

## Gradle Dependency

```implementation 'dev.langchain4j:langchain4j-pgvector:0.36.2' // Use the latest version```

## APIs

- `PgVectorEmbeddingStore`


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
