---
sidebar_position: 18
---

# PGVector

LangChain4j integrates seamlessly with [PGVector] (https://github.com/pgvector/pgvector), allowing developers to store and query vector embeddings directly in PostgreSQL. This integration is ideal for applications like semantic search, recommendation systems, and more.


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
- [Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/pgvector-example/src/main/java)
