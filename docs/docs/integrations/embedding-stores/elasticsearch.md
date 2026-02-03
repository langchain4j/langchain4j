---
sidebar_position: 12
---

# Elasticsearch

https://www.elastic.co/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-elasticsearch</artifactId>
    <version>1.10.0-beta18</version>
</dependency>
```

## Overview

The `langchain4j-elasticsearch` module provides integration with Elasticsearch as an embedding store and content
retriever.

It comes with two main classes:

- [`ElasticsearchEmbeddingStore`](#elasticsearchembeddingstore): an implementation of the `EmbeddingStore` interface 
  that uses Elasticsearch to store and retrieve embeddings.
- [`ElasticsearchContentRetriever`](#elasticsearchcontentretriever): an implementation of the `ContentRetriever`
  interface that uses Elasticsearch to retrieve relevant documents based on vector similarity search.

Both classes need an Elasticsearch `RestClient` to connect to the Elasticsearch server.

```java
String apiKey = "VnVhQ2ZHY0JDZGJrU...";
RestClient restClient = RestClient
    .builder(HttpHost.create("https://localhost:9200"))
    .setDefaultHeaders(new Header[]{
        new BasicHeader("Authorization", "ApiKey " + apiKey)
    })
    .build();
```

**Note:**

> See the [Elasticsearch documentation](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/connecting.html) on how to create a RestClient instance.

## ElasticsearchEmbeddingStore

To create the `ElasticsearchEmbeddingStore` instance, you need to provide an Elasticsearch `RestClient`:

```java
ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
    .restClient(restClient)
    .build();
```

It comes with the following options:

* `indexName`: the name of the Elasticsearch index to use. Default is `default`.
* `configuration`: the `ElasticsearchConfiguration` to use. Default is `ElasticsearchConfigurationKnn`.

The previous code is equivalent to:

```java
ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
    .restClient(restClient)
    .configuration(ElasticsearchConfigurationKnn.builder().build())
    .indexName("default")
    .build();
```

## ElasticsearchContentRetriever

A ContentRetriever needs an embedding model:

```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
```

To create an `ElasticsearchContentRetriever` instance, you need to provide the Elasticsearch `RestClient` and 
the `EmbeddingModel`:

```java
ElasticsearchContentRetriever contentRetriever = ElasticsearchContentRetriever.builder()
    .restClient(restClient)
    .embeddingModel(embeddingModel)
    .build();
```

It comes with the following options:

* `configuration`: the `ElasticsearchConfiguration` to use (see [below](#elasticsearchconfiguration)). Default is `ElasticsearchConfigurationKnn`.
* `indexName`: the name of the Elasticsearch index to use. Default is `default`. Index will be created automatically 
  if not exists.
* `maxResults`: the maximum number of results to retrieve. Default is `3`.
* `minScore`: the minimum score threshold for retrieved results. Default is `0.0`.
* `filter`: a `Filter` to apply during retrieval if any. Default is `null`.

The previous code is equivalent to:

```java
ElasticsearchContentRetriever contentRetriever = ElasticsearchContentRetriever.builder()
    .restClient(restClient)
    .embeddingModel(embeddingModel)
    .configuration(ElasticsearchConfigurationKnn.builder().build())
    .indexName("default")
    .maxResults(3)
    .minScore(0.0)
    .filter(null)
    .build();
```

## ElasticsearchConfiguration

An `ElasticsearchConfiguration` defines how the embedding store or content retriever will interact with the
Elasticsearch server. You can create your own configuration by implementing the `ElasticsearchConfiguration` interface,
or use one of the provided implementations:

- [`ElasticsearchConfigurationKnn`](#elasticsearchconfigurationknn): uses approximate [kNN queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-knn-query.html)
  (default).
- [`ElasticsearchConfigurationScript`](#elasticsearchconfigurationscript): uses [scriptScore queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html).
  Note that this implementation is using cosine similarity.
- [`ElasticsearchConfigurationFullText`](#elasticsearchconfigurationfulltext): uses [full text search](https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-match-query)
  (for content retriever only).
- [`ElasticsearchConfigurationHybrid`](#elasticsearchconfigurationhybrid): uses [hybrid search](https://www.elastic.co/search-labs/tutorials/search-tutorial/vector-search/hybrid-search)
  (for content retriever only, requires paid license). It combines a kNN vector query with a full text query.

To create a configuration instance, you can use the builder provided by each implementation. For example:

```java
ElasticsearchConfiguration configuration = ElasticsearchConfigurationKnn.builder().build();
```

### ElasticsearchConfigurationKnn

The `ElasticsearchConfigurationKnn` uses approximate kNN queries to perform vector similarity search.

It is the default configuration used by both [`ElasticsearchEmbeddingStore`](#elasticsearchembeddingstore) 
and [`ElasticsearchContentRetriever`](#elasticsearchcontentretriever).

To create an instance, you can use the builder:

```java
ElasticsearchConfiguration configuration = ElasticsearchConfigurationKnn.builder().build();
```

It comes with the following options:

* `numCandidates`: the number of candidate neighbors to consider during the search. Default is `null`, meaning using
  the default Elasticsearch value.
* `includeVectorResponse`: whether to include vector fields in the search response. Default is `false`.

> **Note:**
> From version 9.2 of the elasticsearch server, vector fields are excluded from the response by default. To include
> vector fields in the responses, set the `includeVectorResponse` in the builder:
>
> ```java
> ElasticsearchConfigurationKnn configuration = ElasticsearchConfigurationKnn.builder()
>     .includeVectorResponse(true)
>     .build();
> ```

### ElasticsearchConfigurationScript

The `ElasticsearchConfigurationScript` uses scriptScore queries to perform vector similarity search. Note that this
implementation is using cosine similarity.

It is available for both [`ElasticsearchEmbeddingStore`](#elasticsearchembeddingstore)
and [`ElasticsearchContentRetriever`](#elasticsearchcontentretriever).

To create an instance, you can use the builder:

```java
ElasticsearchConfiguration configuration = ElasticsearchConfigurationScript.builder().build();
```

It comes with the following options:

* `includeVectorResponse`: whether to include vector fields in the search response. Default is `false`.

> **Note:**
> From version 9.2 of the elasticsearch server, vector fields are excluded from the response by default. To include
> vector fields in the responses, set the `includeVectorResponse` in the builder:
>
> ```java
> ElasticsearchConfiguration configuration = ElasticsearchConfigurationScript.builder()
>     .includeVectorResponse(true)
>     .build();
> ```

### ElasticsearchConfigurationFullText

The `ElasticsearchConfigurationFullText` uses full text search to retrieve relevant documents.

It is available [`ElasticsearchContentRetriever`](#elasticsearchcontentretriever) only.

To create an instance, you can use the builder:

```java
ElasticsearchConfiguration configuration = ElasticsearchConfigurationFullText.builder().build();
```

### ElasticsearchConfigurationHybrid

The `ElasticsearchConfigurationHybrid` uses hybrid search to combine a kNN vector query with a full text query. Note
that hybrid search requires an elasticsearch enterprise license or a trial.

It is available [`ElasticsearchContentRetriever`](#elasticsearchcontentretriever) only.

To create an instance, you can use the builder:

```java
ElasticsearchConfiguration configuration = ElasticsearchConfigurationHybrid.builder().build();
```

It comes with the following options:

* `numCandidates`: the number of candidate neighbors to consider during the search. Default is `null`, meaning using
  the default Elasticsearch value.
* `includeVectorResponse`: whether to include vector fields in the search response. Default is `false`.

> **Note:**
> From version 9.2 of the elasticsearch server, vector fields are excluded from the response by default. To include
> vector fields in the responses, set the `includeVectorResponse` in the builder:
>
> ```java
> ElasticsearchConfiguration configuration = ElasticsearchConfigurationHybrid.builder()
>     .includeVectorResponse(true)
>     .build();
> ```

### Creating Custom Configurations

You can create your own Elasticsearch configuration by implementing the `ElasticsearchConfiguration` interface. For example:

```java
public class MyElasticsearchConfiguration implements ElasticsearchConfiguration {
    @Override
    SearchResponse<Document> vectorSearch(
            ElasticsearchClient client,
            String indexName,
            EmbeddingSearchRequest embeddingSearchRequest) {
        // Your custom vector search implementation here
    }

    @Override
    SearchResponse<Document> fullTextSearch(
            ElasticsearchClient client, 
            String indexName, 
            String textQuery) {
        // Your custom full text search implementation here
    }

    @Override
    SearchResponse<Document> hybridSearch(
            ElasticsearchClient client,
            String indexName,
            EmbeddingSearchRequest embeddingSearchRequest,
            String textQuery) {
        // Your custom hybrid search implementation here
    }
}
```

## Examples

- [ElasticsearchEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/elasticsearch-example/src/main/java/ElasticsearchEmbeddingStoreExample.java)



