---
sidebar_position: 6
---

# Azure DocumentDB

https://learn.microsoft.com/en-us/azure/documentdb/

Azure DocumentDB is the new name for the service formerly known as
**Azure CosmosDB for MongoDB vCore**. This integration replaces the legacy
[Azure CosmosDB Mongo vCore](./azure-cosmos-mongo-vcore.md) module; existing
users of that module should plan a migration.

## Maven Dependency

You can use Azure DocumentDB with LangChain4j in plain Java or Spring Boot applications.

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-documentdb</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Replace `${latest version here}` with the first released version containing this
module or a newer version. This artifact is not available in earlier releases.

## Client lifecycle

`AzureDocumentDbEmbeddingStore` implements `AutoCloseable`. When configured with
`connectionString(...)`, it creates and owns a `MongoClient`. Close the store when
it is no longer needed, for example with try-with-resources:

```java
try (AzureDocumentDbEmbeddingStore embeddingStore = AzureDocumentDbEmbeddingStore.builder()
        .connectionString(System.getenv("AZURE_DOCUMENTDB_CONNECTION_STRING"))
        .databaseName("my-database")
        .collectionName("my-collection")
        .createIndex(true)
        .kind("vector-hnsw")
        .dimensions(1536)
        .build()) {
    // Add and search embeddings using embeddingStore.
}
```

If you instead supply a client with `mongoClient(...)`, that client remains
caller-owned. Closing the store does not close it; close the client yourself
after all stores sharing it are no longer needed. A supplied client takes
precedence over a connection string.

If initialization fails after the store creates a client, that client is closed
automatically. Repeated calls to `close()` have no effect.

## Spring Boot

There is no dedicated Azure DocumentDB Spring Boot starter or automatic
configuration. Use the plain Java dependency above and register the store
explicitly in a Spring configuration class:

```java
@Bean(destroyMethod = "close")
AzureDocumentDbEmbeddingStore embeddingStore() {
    return AzureDocumentDbEmbeddingStore.builder()
            .connectionString(System.getenv("AZURE_DOCUMENTDB_CONNECTION_STRING"))
            .databaseName("my-database")
            .collectionName("my-collection")
            .createIndex(true)
            .kind("vector-hnsw")
            .dimensions(1536)
            .build();
}
```

Spring closes this store and its internally created client when the application
context shuts down.

## APIs

- `AzureDocumentDbEmbeddingStore`

## Migrating from Azure CosmosDB Mongo vCore

If you previously used the `langchain4j-azure-cosmos-mongo-vcore` module,
migration consists of:

1. Replacing the artifact ID
   `langchain4j-azure-cosmos-mongo-vcore` with `langchain4j-azure-documentdb`.
2. Replacing references to `AzureCosmosDbMongoVCoreEmbeddingStore` with
   `AzureDocumentDbEmbeddingStore` and updating imports to the
   `dev.langchain4j.store.embedding.azure.documentdb` package.
3. Managing the client lifecycle as described above.

If you used the legacy Spring Boot starter, replace it with the plain Java
dependency and register the bean shown above. There is no replacement starter,
and the old starter's configuration properties are not applied automatically.

The underlying connection string, database, collection, and stored data remain
unchanged.
