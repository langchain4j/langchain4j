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
    <version>1.20.0-beta30</version>
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

Both classes need an [Elasticsearch Client](https://www.elastic.co/docs/reference/elasticsearch/clients/java) to 
connect to the Elasticsearch server.

```java
String apiKey = "VnVhQ2ZHY0JDZGJrU...";
ElasticsearchClient client = ElasticsearchClient.of(ec -> ec
        .host("https://localhost:9200")
        .apiKey(apiKey));
```

**Note:**

> See the [Elasticsearch documentation](https://www.elastic.co/docs/reference/elasticsearch/clients/java/setup/connecting) 
> on how to create an ElasticsearchClient instance.

## ElasticsearchEmbeddingStore

To create the `ElasticsearchEmbeddingStore` instance, you need to provide an `ElasticsearchClient`:

```java
ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
    .client(client)
    .build();
```

It comes with the following options:

* `indexName`: the name of the Elasticsearch index to use. Default is `default`.
* `configuration`: the `ElasticsearchConfiguration` to use. Default is `ElasticsearchConfigurationKnn`.
* `refresh`: when documents written or removed by ID become visible to search. Default is `Refresh.False`.

The previous code is equivalent to:

```java
ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
    .client(client)
    .configuration(ElasticsearchConfigurationKnn.builder().build())
    .indexName("default")
    .build();
```

### Refresh policy

Elasticsearch does not make a document searchable the moment the write request returns. By default a document becomes
visible to search on the next periodic index refresh (once per second, unless the index says otherwise). Until then the
document is safely stored and can be fetched by ID, but it will not show up in a search.

This matters when you add documents and then immediately do something that searches for them. The `refresh` option lets
you wait for search visibility instead:

```java
import co.elastic.clients.elasticsearch._types.Refresh;

ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
    .client(client)
    .refresh(Refresh.WaitFor)
    .build();
```

The three values are:

* `Refresh.False` (default) returns as soon as the document is stored, and leaves refreshing to Elasticsearch. This is
  the fastest option.
* `Refresh.WaitFor` returns once the document is visible to search. It does not force extra refreshes, so it is usually
  the right choice when you need visibility. Note that if the index has automatic refreshing turned off
  (`index.refresh_interval: -1`), the call waits until something else triggers a refresh.
* `Refresh.True` forces a refresh immediately. This gives visibility without waiting, but creating a new segment on
  every request reduces indexing throughput, so avoid it on write-heavy indices.

The option applies to `add`, `addAll` and `removeAll(Collection<String> ids)`. It does not change searches, and it does
not isolate you from writes made concurrently by someone else. The same option is available on
`ElasticsearchContentRetriever.builder()`.

Filtered removal (`removeAll(Filter)`) is a delete-by-query, so it too only matches documents that are already
visible to search: embeddings added moments earlier can survive it. If you add embeddings and then immediately remove
them by filter, configure `Refresh.WaitFor` so the writes are searchable before the removal runs.

See the
[Elasticsearch refresh parameter documentation](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/refresh-parameter)
for details.

### Storing documents without an embedding

Next to the usual `add(Embedding, TextSegment)` methods, the store can also index plain text, without computing an
embedding for it:

```java
store.add("Printer troubleshooting guide");                    // generates an id
store.add("my-id", "Printer troubleshooting guide");           // with your own id
store.addAllText(List.of("First guide", "Second guide"));      // several at once
```

Because these documents have no vector, vector search never returns them, neither with
[`ElasticsearchConfigurationKnn`](#elasticsearchconfigurationknn) nor with
[`ElasticsearchConfigurationScript`](#elasticsearchconfigurationscript). They are still found by full text search, so a
single index can hold both embedded and text-only documents, and you can search it both ways with
[`ElasticsearchConfigurationFullText`](#elasticsearchconfigurationfulltext) or
[`ElasticsearchConfigurationHybrid`](#elasticsearchconfigurationhybrid).

## ElasticsearchContentRetriever

A ContentRetriever needs an embedding model:

```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
```

To create an `ElasticsearchContentRetriever` instance, you need to provide the `ElasticsearchClient` and 
the `EmbeddingModel`:

```java
ElasticsearchContentRetriever contentRetriever = ElasticsearchContentRetriever.builder()
    .client(client)
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
    .client(client)
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
> vector fields in the responses (not recommended), set the `includeVectorResponse` in the builder:
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
> vector fields in the responses (not recommended), set the `includeVectorResponse` in the builder:
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
> vector fields in the responses (not recommended), set the `includeVectorResponse` in the builder:
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
        // Your optional custom vector search implementation here
    }

    @Override
    SearchResponse<Document> fullTextSearch(
            ElasticsearchClient client,
            String indexName,
            FullTextSearchRequest request) {
        // Your optional custom full text search implementation here
    }

    @Override
    SearchResponse<Document> hybridSearch(
            ElasticsearchClient client,
            String indexName,
            EmbeddingSearchRequest embeddingSearchRequest,
            String textQuery) {
        // Your optional custom hybrid search implementation here
    }
}
```

Please note that you can implement only the methods relevant to your use case:

* `vectorSearch` for vector similarity search (used by both `ElasticsearchEmbeddingStore` and `ElasticsearchContentRetriever`).
* `fullTextSearch` for full text search (used by `ElasticsearchContentRetriever` only).
* `hybridSearch` for hybrid search (used by `ElasticsearchContentRetriever` only).

The `FullTextSearchRequest` carries the `textQuery` to search for, together with the `maxResults`, `minScore` and
`filter` configured on the `ElasticsearchContentRetriever`. Your implementation is responsible for applying them,
otherwise documents which do not match the filter can be returned.

> **Note:**
> There is also a deprecated `fullTextSearch(ElasticsearchClient client, String indexName, String textQuery)` method.
> Configurations which only implement that one keep working, but the `maxResults`, `minScore` and `filter` of the
> retriever are ignored, and a warning is logged. Please implement the method taking a `FullTextSearchRequest` instead.

## Examples

- [ElasticsearchEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/elasticsearch-example/src/main/java/ElasticsearchEmbeddingStoreExample.java)
- [ElasticsearchEmbeddingStoreWithScriptExample](https://github.com/langchain4j/langchain4j-examples/blob/main/elasticsearch-example/src/main/java/ElasticsearchEmbeddingStoreWithScriptExample.java)
