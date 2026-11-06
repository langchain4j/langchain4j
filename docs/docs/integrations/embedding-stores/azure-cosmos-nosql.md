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
    <version>1.23.0-beta33</version>
</dependency>
```

### Spring Boot

**Spring Boot 3:**

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-cosmos-nosql-spring-boot-starter</artifactId>
    <version>${latest version here}</version>
</dependency>
```

For **Spring Boot 4**, use `langchain4j-azure-cosmos-nosql-spring-boot4-starter`
instead, with the same configuration properties below.

Then configure the embedding store in your `application.properties` or `application.yml`:

```properties
langchain4j.azure.cosmos-nosql.endpoint=${AZURE_COSMOS_ENDPOINT}
langchain4j.azure.cosmos-nosql.key=${AZURE_COSMOS_KEY}
langchain4j.azure.cosmos-nosql.database-name=my-database
langchain4j.azure.cosmos-nosql.container-name=my-container
langchain4j.azure.cosmos-nosql.dimensions=1536
```

Set `dimensions` to match your embedding model. It can be omitted only when an
`EmbeddingModel` bean is available for the starter to infer the dimensions.

The `AzureCosmosDbNoSqlEmbeddingStore` bean will be created automatically and can be injected:

```java
@Autowired
AzureCosmosDbNoSqlEmbeddingStore embeddingStore;
```

## APIs

- `AzureCosmosDbNoSqlEmbeddingStore`
- `AzureCosmosDBNoSqlContentRetriever`

## Metadata filtering

Use the standard LangChain4j filter API to restrict searches to matching `TextSegment` metadata:

```java
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

Filter filter = metadataKey("category").isEqualTo("documentation")
        .and(metadataKey("year").isGreaterThanOrEqualTo(2024));

EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(10)
        .filter(filter)
        .build();

embeddingStore.search(request);
```

Equality, inequality, ordering, `isIn`, `isNotIn`, `containsString`, and logical `and`, `or`, and
`Filter.not` are supported. Negated comparisons also match documents where the metadata entry is missing.

The default mapper resolves a comparison key such as `category` to `c["metadata"]["category"]`, not
the document root. Keys are literal metadata names, so `source.file` addresses a single metadata entry.
For backward compatibility, keys starting with `metadata.` retain their previous dotted-path meaning:
`metadata.category` also addresses `c["metadata"]["category"]`.

Metadata continues to be stored as strings, including metadata already written by this integration.
Numeric filter values compare numerically using `StringToNumber`, and also match native JSON numbers;
string filter values compare lexicographically. Numeric conversion does not use the Cosmos DB index
and can increase request-unit consumption.

The same filters are available through `AzureCosmosDBNoSqlContentRetriever.builder().filter(filter)`
for vector, full-text, full-text ranking, and hybrid searches. Full-text operators retain their document
paths and can be combined with metadata filters. In `FULL_TEXT_SEARCH` mode, include a full-text
condition in the filter when you want term matching as well as metadata filtering:

```java
import dev.langchain4j.rag.content.retriever.azure.cosmos.nosql.FullTextContains;

Filter filter = new FullTextContains("text", "bicycle")
        .and(metadataKey("category").isEqualTo("sports"));
```

Both builders accept a custom `AzureCosmosDBNoSqlFilterMapper` through `filterMapper(...)` for other
document schemas or comparisons against top-level document fields. Custom mappers are responsible for
safely escaping both keys and values.


## Examples

- [AzureCosmosDbNoSqlEmbeddingStoreIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-azure-cosmos-nosql/src/test/java/dev/langchain4j/store/embedding/azure/cosmos/nosql/AzureCosmosDbNoSqlEmbeddingStoreIT.java)
