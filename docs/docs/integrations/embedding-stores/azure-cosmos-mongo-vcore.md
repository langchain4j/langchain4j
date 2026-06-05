---
sidebar_position: 4
---

# Azure CosmosDB Mongo vCore

https://azure.microsoft.com/en-us/products/cosmos-db/

:::warning Deprecated — renamed to Azure DocumentDB
Azure CosmosDB for MongoDB vCore has been rebranded by Microsoft as
**Azure DocumentDB** (see https://learn.microsoft.com/en-us/azure/documentdb/).

This module and its Spring Boot starter are kept for backwards compatibility
but will be deprecated in favor of a new
[Azure DocumentDB](./azure-documentdb.md) integration. New projects should
use the DocumentDB integration; existing projects can continue to use the
module documented below and migrate at their own pace.
:::

## Maven Dependency

You can use Azure CosmosDB Mongo vCore with LangChain4j in plain Java or Spring Boot applications.

### Plain Java

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-cosmos-mongo-vcore</artifactId>
    <version>1.15.1-beta25</version>
</dependency>
```

### Spring Boot

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-cosmos-mongo-vcore-spring-boot-starter</artifactId>
    <version>1.10.0-beta18</version>
</dependency>
```

Then configure the embedding store in your `application.properties` or `application.yml`:

```properties
langchain4j.azure.cosmos-mongo-vcore.connection-string=${AZURE_COSMOS_CONNECTION_STRING}
langchain4j.azure.cosmos-mongo-vcore.database-name=my-database
langchain4j.azure.cosmos-mongo-vcore.collection-name=my-collection
langchain4j.azure.cosmos-mongo-vcore.index-name=my-index
langchain4j.azure.cosmos-mongo-vcore.create-index=true
langchain4j.azure.cosmos-mongo-vcore.dimensions=1536
langchain4j.azure.cosmos-mongo-vcore.kind=vector-hnsw
langchain4j.azure.cosmos-mongo-vcore.m=16
langchain4j.azure.cosmos-mongo-vcore.ef-construction=64
langchain4j.azure.cosmos-mongo-vcore.ef-search=40
```

The `AzureCosmosDbMongoVCoreEmbeddingStore` bean will be created automatically and can be injected:

```java
@Autowired
AzureCosmosDbMongoVCoreEmbeddingStore embeddingStore;
```

## APIs

- `AzureCosmosDbMongoVCoreEmbeddingStore`


## Examples

- [AzureCosmosDBMongoVCoreEmbeddingStoreIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-azure-cosmos-mongo-vcore/src/test/java/dev/langchain4j/store/embedding/azure/cosmos/mongo/vcore/AzureCosmosDBMongoVCoreEmbeddingStoreIT.java)
