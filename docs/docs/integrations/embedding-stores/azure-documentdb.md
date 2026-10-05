---
sidebar_position: 5.5
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

Replace `${latest version here}` with the version of this module you are using.

## Configuration

The vector index `kind` is required, including when you use an existing index.
Use `AzureDocumentDbEmbeddingStore.VectorIndexType.VECTOR_IVF` or
`AzureDocumentDbEmbeddingStore.VectorIndexType.VECTOR_HNSW`; the strings
`"vector-ivf"` and `"vector-hnsw"` are also supported.

When `createIndex(true)` is set, `dimensions(...)` is required and must match
your embedding model's output dimensions. There is no default dimension.
HNSW requires an M30 or higher Azure DocumentDB cluster tier.

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
        .kind(AzureDocumentDbEmbeddingStore.VectorIndexType.VECTOR_HNSW)
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

There is no dedicated Azure DocumentDB starter for Spring Boot 3 or Spring Boot 4.
Use the plain Java dependency above and register the store explicitly in a
Spring configuration class:

```java
@Bean(destroyMethod = "close")
AzureDocumentDbEmbeddingStore embeddingStore() {
    return AzureDocumentDbEmbeddingStore.builder()
            .connectionString(System.getenv("AZURE_DOCUMENTDB_CONNECTION_STRING"))
            .databaseName("my-database")
            .collectionName("my-collection")
            .createIndex(true)
            .kind(AzureDocumentDbEmbeddingStore.VectorIndexType.VECTOR_HNSW)
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
3. Setting the vector index kind and, when creating an index, explicitly setting
   the dimensions to match your embedding model.
4. Managing the client lifecycle as described above.

If you used the legacy Spring Boot starter, replace it with the plain Java
dependency and register the bean shown above. There is no replacement starter,
and the old starter's configuration properties are not applied automatically.

The connection string, database, collection, stored document shape, and default
index name (`defaultIndexAzureCosmos`) remain unchanged. Existing data and indexes
do not need to be rewritten.
