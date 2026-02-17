---
sidebar_position: 17
---

# OpenSearch

https://opensearch.org/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-opensearch</artifactId>
    <version>1.11.0-beta19</version>
</dependency>
```


## APIs

The `OpenSearchEmbeddingStore` uses exact k-NN with scoring script implementation for similarity search.
See [OpenSearch k-NN documentation](https://opensearch.org/docs/latest/search-plugins/knn/knn-score-script/) for more details.

### Features

- **Metadata filtering**: Supports filtering search results by metadata using the `Filter` API
- **Removal operations**:
  - Remove embeddings by ID
  - Remove embeddings by metadata filter
  - Remove all embeddings (drops the index)
- **AWS Support**: Native support for Amazon OpenSearch Service and Amazon OpenSearch Serverless

### Basic Usage

To create the `OpenSearchEmbeddingStore` instance for local or network-reachable OpenSearch:

```java
OpenSearchEmbeddingStore store = OpenSearchEmbeddingStore.builder()
        .serverUrl("http://localhost:9200")
        .indexName("my-embeddings")
        .build();
```

With authentication:

```java
OpenSearchEmbeddingStore store = OpenSearchEmbeddingStore.builder()
        .serverUrl("https://my-opensearch.example.com:9200")
        .userName("admin")
        .password("admin")
        .indexName("my-embeddings")
        .build();
```

### AWS OpenSearch

For Amazon OpenSearch Service or OpenSearch Serverless:

```java
AwsSdk2TransportOptions options = AwsSdk2TransportOptions.builder()
        .setCredentials(DefaultCredentialsProvider.create())
        .build();

OpenSearchEmbeddingStore store = OpenSearchEmbeddingStore.builder()
        .serverUrl("https://search-domain.us-east-1.es.amazonaws.com")
        .serviceName("es") // or "aoss" for Serverless
        .region("us-east-1")
        .options(options)
        .indexName("my-embeddings")
        .build();
```

### Metadata Filtering

Filter search results by metadata:

```java
Filter filter = metadataKey("category").isEqualTo("documentation");

EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .filter(filter)
        .maxResults(10)
        .build();

EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
```

Supported filter operations:
- Comparison: `isEqualTo`, `isNotEqualTo`, `isGreaterThan`, `isGreaterThanOrEqualTo`, `isLessThan`, `isLessThanOrEqualTo`
- Collection: `isIn`, `isNotIn`
- Logical: `and`, `or`, `not`

### Removal Operations

Remove embeddings by ID:
```java
store.removeAll(List.of("id1", "id2", "id3"));
```

Remove embeddings by metadata filter:
```java
Filter filter = metadataKey("status").isEqualTo("archived");
store.removeAll(filter);
```

Remove all embeddings (drops the index):
```java
store.removeAll();
```


## Examples

- [OpenSearchEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/opensearch-example/src/main/java/OpenSearchEmbeddingStoreExample.java)
