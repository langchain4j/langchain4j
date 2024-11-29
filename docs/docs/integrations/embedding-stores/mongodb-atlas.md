---
sidebar_position: 15
---

# MongoDB Atlas and Vector Search

[MongoDB Atlas](https://www.mongodb.com/docs/atlas/) is a fully-managed cloud database available in AWS, Azure, and GCP. It supports native Vector Search and full text search (BM25) on your MongoDB document data.

[MongoDB Atlas Vector Search](https://www.mongodb.com/products/platform/atlas-vector-search) allows you to store your embeddings in MongoDB documents, create vector search indexes, and perform KNN search with an approximate nearest neighbor algorithm called Hierarchical Navigable Small Worlds. You can implement this feature by using the `$vectorSearch` MQL aggregation stage.

## Prerequisites

Vector Search requires the following or later MongoDB versions:

- 6.0.11
- 7.0.2

Vector Search is available on Atlas, and as of 2024 is available in early access for Community Edition.

MongoDB offers a free forever cluster. See the [Get Started with Atlas](https://www.mongodb.com/docs/atlas/getting-started/) tutorial to learn more. Once you deploy a cluster, you can create a vector search index by using the index JSON editor. Alternatively, the LangChain4j `MongoDbEmbeddingStore` builder offers a `createIndex` option to automate index creation.

MongoDB also offers [Atlas CLI](https://www.mongodb.com/docs/atlas/cli/current/) for local development.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-mongodb-atlas</artifactId>
    <version>0.36.2</version>
</dependency>
```

## APIs

- `MongoDbEmbeddingStore`

## Examples

- [MongoDbEmbeddingStoreIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-mongodb-atlas/src/test/java/dev/langchain4j/store/embedding/mongodb/MongoDbEmbeddingStoreIT.java)

