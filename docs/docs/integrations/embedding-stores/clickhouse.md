---
sidebar_position: 8
---

# ClickHouse

[ClickHouse](https://clickhouse.com/) is the fastest and most resource efficient open-source
database for real-time apps and analytics with full SQL support and a wide range of functions to
assist users in writing analytical queries. Lately added data structures and distance search
functions (like cosineDistance) as well
as [approximate nearest neighbor search indexes](https://clickhouse.com/docs/en/engines/table-engines/mergetree-family/annindexes)
enable ClickHouse to be used as a high performance and scalable vector database to store and search
vectors with SQL.

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-clickhouse</artifactId>
    <version>${latest version here}</version>
</dependency>
```

## APIs

LangChain4j uses `client-v2` as ClickHouse client. To create `ClickHouseEmbeddingStore` instance, you need to provide a `ClickHouseSettings`:

```java
// Mapping metadata key to ClickHouse data type.
Map<String, ClickHouseDataType> metadataTypeMap = new HashMap<>();

ClickHouseSettings settings = ClickHouseSettings.builder()
    .url("http://localhost:8123")
    .table("langchain4j_table")
    .username(System.getenv("USERNAME"))
    .password(System.getenv("PASSWORD"))
    .dimension(embeddingModel.dimension())
    .metadataTypeMap(metadataTypeMap)
    .build();
```

Then you can create the embedding store:

```java
ClickHouseEmbeddingStore embeddingStore = ClickHouseEmbeddingStore.builder()
    .settings(settings)
    .build();
```

## Examples

- [ClickHouseEmbeddingStoreIT](https://github.com/langchain4j/langchain4j-community/blob/main/langchain4j-community-clickhouse/src/test/java/dev/langchain4j/community/store/embedding/clickhouse/ClickHouseEmbeddingStoreIT.java)
