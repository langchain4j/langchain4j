---
sidebar_position: 6
---

# Azure DocumentDB

https://learn.microsoft.com/en-us/azure/documentdb/

Azure DocumentDB is the new name for the service formerly known as
**Azure CosmosDB for MongoDB vCore**. This integration replaces the legacy
[Azure CosmosDB Mongo vCore](./azure-cosmos-mongo-vcore.md) module; existing
users of that module should plan a migration.

:::note Spring Boot starter
The Spring Boot starter (`langchain4j-azure-documentdb-spring-boot-starter`)
is delivered from the companion
[`langchain4j-spring`](https://github.com/langchain4j/langchain4j-spring/pull/163)
repository. The configuration property prefix
(`langchain4j.azure.documentdb.*`) and bean class name documented below match
that starter.
:::

## Maven Dependency

You can use Azure DocumentDB with LangChain4j in plain Java or Spring Boot applications.

### Plain Java

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-documentdb</artifactId>
    <version>1.15.0-beta25</version>
</dependency>
```

### Spring Boot

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-documentdb-spring-boot-starter</artifactId>
    <version>1.10.0-beta18</version>
</dependency>
```

Then configure the embedding store in your `application.properties` or `application.yml`:

```properties
langchain4j.azure.documentdb.connection-string=${AZURE_DOCUMENTDB_CONNECTION_STRING}
langchain4j.azure.documentdb.database-name=my-database
langchain4j.azure.documentdb.collection-name=my-collection
langchain4j.azure.documentdb.index-name=my-index
langchain4j.azure.documentdb.create-index=true
langchain4j.azure.documentdb.dimensions=1536
langchain4j.azure.documentdb.kind=vector-hnsw
langchain4j.azure.documentdb.m=16
langchain4j.azure.documentdb.ef-construction=64
langchain4j.azure.documentdb.ef-search=40
```

The `AzureDocumentDbEmbeddingStore` bean will be created automatically and can be injected:

```java
@Autowired
AzureDocumentDbEmbeddingStore embeddingStore;
```

## APIs

- `AzureDocumentDbEmbeddingStore`

## Migrating from Azure CosmosDB Mongo vCore

If you previously used the `langchain4j-azure-cosmos-mongo-vcore` module or its
Spring Boot starter, migration consists of:

1. Replacing the artifact ID
   `langchain4j-azure-cosmos-mongo-vcore[-spring-boot-starter]` with
   `langchain4j-azure-documentdb[-spring-boot-starter]`.
2. Replacing the `langchain4j.azure.cosmos-mongo-vcore.*` configuration
   property prefix with `langchain4j.azure.documentdb.*`.
3. Replacing references to `AzureCosmosDbMongoVCoreEmbeddingStore` with
   `AzureDocumentDbEmbeddingStore`.

The underlying connection string, database, and collection values remain
unchanged — only the LangChain4j-side naming differs.
