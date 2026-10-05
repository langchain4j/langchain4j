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

:::note Starter availability
The starters below require a release of
[`langchain4j-spring`](https://github.com/langchain4j/langchain4j-spring)
that includes Azure DocumentDB support. They are not available in earlier releases.
Until the artifacts are published, build the matching snapshots locally or use
the [manual bean configuration](#manual-bean-configuration).
:::

Choose the starter matching your Spring Boot version.

**Spring Boot 3:**

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-documentdb-spring-boot-starter</artifactId>
    <version>${latest version here}</version>
</dependency>
```

**Spring Boot 4:**

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-documentdb-spring-boot4-starter</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Both starters use the same configuration properties:

```properties
langchain4j.azure.documentdb.connection-string=${AZURE_DOCUMENTDB_CONNECTION_STRING}
langchain4j.azure.documentdb.database-name=my-database
langchain4j.azure.documentdb.collection-name=my-collection
langchain4j.azure.documentdb.kind=vector-hnsw
langchain4j.azure.documentdb.create-index=true
langchain4j.azure.documentdb.dimensions=1536
```

`database-name`, `collection-name`, and `kind` are required. Set `dimensions` to
match your embedding model. If `create-index=true` and `dimensions` is omitted,
the starter infers it from an `EmbeddingModel` bean. Inference requires an
unambiguous bean, or one marked `@Primary`; otherwise configuration fails.
Explicit dimensions take precedence and do not require an embedding model bean.
When `create-index=false` (the default), dimensions are optional.

The default index name remains `defaultIndexAzureCosmos`; a custom index name can
be set with `langchain4j.azure.documentdb.index-name`. Other defaults match the
plain Java builder: `application-name=LangChain4j`, `num-lists=1`, `m=16`,
`ef-construction=64`, and `ef-search=40`.

The starter creates an `AzureDocumentDbEmbeddingStore` bean and closes it when
the application context shuts down. A user-provided `MongoClient` bean takes
precedence over `connection-string`. The store never closes that client;
its owner, including Spring when managing the client bean, handles its lifecycle.
Multiple client beans require an unambiguous candidate, such as one marked
`@Primary`. A default client from Spring Boot's MongoDB auto-configuration does
not replace a missing DocumentDB connection string.

Set `langchain4j.azure.documentdb.enabled=false` to disable auto-configuration.
Defining your own `AzureDocumentDbEmbeddingStore` bean also takes precedence.

### Manual bean configuration

To configure the store without a starter, use the plain Java dependency and
register it explicitly in a Spring configuration class:

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

For Spring Boot applications, use the matching replacement starter once it is
available:

| Spring Boot version | Legacy artifact | Replacement artifact |
|---------------------|-----------------|----------------------|
| 3 | `langchain4j-azure-cosmos-mongo-vcore-spring-boot-starter` | `langchain4j-azure-documentdb-spring-boot-starter` |
| 4 | `langchain4j-azure-cosmos-mongo-vcore-spring-boot4-starter` | `langchain4j-azure-documentdb-spring-boot4-starter` |

Rename the property prefix from `langchain4j.azure.cosmos-mongo-vcore` to
`langchain4j.azure.documentdb`, preserving your connection, database, collection,
and custom index settings. Explicitly configure `kind`. When creating an index,
set `dimensions` or provide an `EmbeddingModel` bean; do not rely on the legacy
dimension default.

If you previously registered a DocumentDB store bean manually, remove that bean
to let the starter configure it, or keep it to continue using your own configuration.
Until a starter release is available, the manual configuration remains supported.

The connection string, database, collection, stored document shape, and default
index name (`defaultIndexAzureCosmos`) remain unchanged. Existing data and indexes
do not need to be rewritten.
