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
    <version>1.8.0-beta15</version>
</dependency>
```


## APIs

The `ElasticsearchEmbeddingStore` comes with 2 implementations:

* Using approximate [kNN queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-knn-query.html) 
with the `ElasticsearchConfigurationKnn` configuration class (default).
* Using [scriptScore queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html) 
with the `ElasticsearchConfigurationScript` configuration class. Note that this implementation is using cosine similarity.

The `ElasticsearchContentRetriever` comes with 4 implementations:
* Using approximate [kNN queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-knn-query.html)
  with the `ElasticsearchConfigurationKnn` configuration class (default).
* Using [scriptScore queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html)
  with the `ElasticsearchConfigurationScript` configuration class. Note that this implementation is using cosine similarity.
* Using [full text search](https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-match-query)
  with the `ElasticsearchConfigurationFullText` configuration class.
* Using [hybrid search](https://www.elastic.co/search-labs/tutorials/search-tutorial/vector-search/hybrid-search)
    with the `ElasticsearchConfigurationHybrid` configuration class to combine a kNN vector query with a full text query (paid feature).

## ElasticsearchEmbeddingStore

### Common options

To create the `ElasticsearchEmbeddingStore` instance, you need to provide an Elasticsearch 
`RestClient`:

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

Then you can create the embedding store. It uses the approximate kNN query implementation by default.

```java
ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
        .restClient(restClient)
        .build();
```

**Note:**

> From version 9.2 of the elasticsearch server, vector fields are excluded from the response by default. To include vector fields in the responses, set the `includeVectorResponse` in the builder:
```java
ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
        .restClient(restClient)
        .includeVectorResponse(true)
        .build();
```


### ElasticsearchConfigurationKnn configuration (default)

The previous is equivalent to:

```java
ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
        .configuration(ElasticsearchConfigurationKnn.builder().build())
        .restClient(restClient)
        .build();
```

### ElasticsearchConfigurationScript configuration

If you want to use the previous, but slower behavior, you can use the `ElasticsearchConfigurationScript`
configuration class:

```java
ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
        .configuration(ElasticsearchConfigurationScript.builder().build())
        .restClient(restClient)
        .build();
```

## Examples

- [ElasticsearchEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/elasticsearch-example/src/main/java/ElasticsearchEmbeddingStoreExample.java)

## ElasticsearchContentRetriever

### Common options

To create an `ElasticsearchContentRetriever` instance, you need to provide an Elasticsearch
`RestClient`:

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

Then you can create the content retriever. It uses the approximate kNN query implementation by default.

```java
ElasticsearchContentRetriever contentRetriever = ElasticsearchContentRetriever.builder()
        .restClient(restClient)
        .build();
```

**Note:**

> From version 9.2 of the elasticsearch server, vector fields are excluded from the response by default. To include vector fields in the responses, set the `includeVectorResponse` in the builder:
```java
ElasticsearchContentRetriever contentRetriever = ElasticsearchContentRetriever.builder()
        .restClient(restClient)
        .includeVectorResponse(true)
        .build();
```


### ElasticsearchConfigurationKnn configuration (default)

The previous is equivalent to:

```java
ElasticsearchContentRetriever contentRetriever = ElasticsearchContentRetriever.builder()
        .configuration(ElasticsearchConfigurationKnn.builder().build())
        .restClient(restClient)
        .build();
```

### ElasticsearchConfigurationScript configuration

If you want to use the previous, but slower behavior, you can use the `ElasticsearchConfigurationScript`
configuration class:

```java
ElasticsearchContentRetriever contentRetriever = ElasticsearchContentRetriever.builder()
        .configuration(ElasticsearchConfigurationScript.builder().build())
        .restClient(restClient)
        .build();
```

### ElasticsearchConfigurationFullText configuration

```java
ElasticsearchContentRetriever contentRetriever = ElasticsearchContentRetriever.builder()
        .configuration(ElasticsearchConfigurationFullText.builder().build())
        .restClient(restClient)
        .build();
```

### ElasticsearchConfigurationHybrid configuration

Note that hybrid search requires an elasticsearch paid license (https://www.elastic.co/subscriptions). 

```java
ElasticsearchContentRetriever contentRetriever = ElasticsearchContentRetriever.builder()
        .configuration(ElasticsearchConfigurationHybrid.builder().build())
        .restClient(restClient)
        .build();
```

