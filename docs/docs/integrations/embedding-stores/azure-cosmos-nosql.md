---
sidebar_position: 5
---

# Azure CosmosDB NoSQL

https://azure.microsoft.com/en-us/products/cosmos-db/


## Maven Dependency

You can use Azure CosmosDB NoSQL with LangChain4j in plain Java or Spring Boot applications.

### Plain Java

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-cosmos-nosql</artifactId>
    <version>1.11.0-beta19</version>
</dependency>
```

### Spring Boot

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-cosmos-nosql-spring-boot-starter</artifactId>
    <version>1.10.0-beta18</version>
</dependency>
```

Then configure the embedding store in your `application.properties` or `application.yml`:

```properties
langchain4j.azure.cosmos-nosql.endpoint=${AZURE_COSMOS_ENDPOINT}
langchain4j.azure.cosmos-nosql.key=${AZURE_COSMOS_KEY}
langchain4j.azure.cosmos-nosql.database-name=my-database
langchain4j.azure.cosmos-nosql.container-name=my-container
```

The `AzureCosmosDbNoSqlEmbeddingStore` bean will be created automatically and can be injected:

```java
@Autowired
AzureCosmosDbNoSqlEmbeddingStore embeddingStore;
```

## APIs

- `AzureCosmosDbNoSqlEmbeddingStore`


## Examples

- [AzureCosmosDbNoSqlEmbeddingStoreIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-azure-cosmos-nosql/src/test/java/dev/langchain4j/store/embedding/azure/cosmos/nosql/AzureCosmosDbNoSqlEmbeddingStoreIT.java)
