---
sidebar_position: 14
---

# Milvus

https://milvus.io/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-milvus</artifactId>
    <version>0.36.2</version>
</dependency>
```


## APIs

- `MilvusEmbeddingStore`

## Creation

There are 2 ways to create `MilvusEmbeddingStore`:


1. Create MilvusEmbeddingStore with Automatic MilvusServiceClient Creation: Use this option to set up a new MilvusServiceClient internally with specified host, port, and authentication details for easy setup.


```java
MilvusEmbeddingStore store = MilvusEmbeddingStore.builder()

    .host("localhost")                         // Host for Milvus instance
    .port(19530)                               // Port for Milvus instance
    .collectionName("example_collection")      // Name of the collection
    .dimension(128)                            // Dimension of vectors
    .indexType(IndexType.FLAT)                 // Index type
    .metricType(MetricType.COSINE)             // Metric type
    .username("username")                      // Username for Milvus
    .password("password")                      // Password for Milvus
    .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)  // Consistency level
    .autoFlushOnInsert(true)                   // Auto flush after insert
    .idFieldName("id")                         // ID field name
    .textFieldName("text")                     // Text field name
    .metadataFieldName("metadata")             // Metadata field name
    .vectorFieldName("vector")                 // Vector field name
    .build();                                  // Build the MilvusEmbeddingStore instance
```

2. Create MilvusEmbeddingStore with an Existing MilvusServiceClient: If you already have a MilvusServiceClient, this option lets you directly use it in the builder, allowing customized configurations.


```java

// Set up a custom MilvusServiceClient
MilvusServiceClient customMilvusClient = new MilvusServiceClient(
    ConnectParam.newBuilder()
        .withHost("localhost")
        .withPort(19530)
        .build()
);

// Use the custom client in the builder
MilvusEmbeddingStore store = MilvusEmbeddingStore.builder()

    .milvusClient(customMilvusClient)          // Use an existing Milvus client
    .collectionName("example_collection")      // Name of the collection
    .dimension(128)                            // Dimension of vectors
    .indexType(IndexType.FLAT)                 // Index type
    .metricType(MetricType.COSINE)             // Metric type
    .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)  // Consistency level
    .autoFlushOnInsert(true)                   // Auto flush after insert
    .idFieldName("id")                         // ID field name
    .textFieldName("text")                     // Text field name
    .metadataFieldName("metadata")             // Metadata field name
    .vectorFieldName("vector")                 // Vector field name
    .build();                                  // Build the MilvusEmbeddingStore instance


```


## Examples

- [MilvusEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/milvus-example/src/main/java/MilvusEmbeddingStoreExample.java)
