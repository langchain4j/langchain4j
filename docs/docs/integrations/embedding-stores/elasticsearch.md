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
    <version>0.36.2</version>
</dependency>
```


## APIs

The `ElasticsearchEmbeddingStore` comes with 2 implementations:

* Using approximate [kNN queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-knn-query.html) 
with the `ElasticsearchConfigurationKnn` configuration class (default).
* Using [scriptScore queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html) 
with the `ElasticsearchConfigurationScript` configuration class. Note that this implementation is using cosine similarity.

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
