---
sidebar_position: 17
---

# OceanBase
The OceanBase Embedding Store integrates with the vector search capabilities of [OceanBase Database](https://www.oceanbase.com/), a high-performance distributed SQL database.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-oceanbase</artifactId>
    <version>1.1.0-beta7-SNAPSHOT</version>
</dependency>
```

## APIs

- `OceanBaseEmbeddingStore`

## Examples

- [OceanBaseEmbeddingStoreIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-oceanbase/src/test/java/dev/langchain4j/store/embedding/oceanbase/OceanBaseEmbeddingStoreIT.java)

## Usage

Instances of this store can be created by configuring a builder. The builder requires a DataSource and an embedding table to be provided. The distance between two vectors is calculated using the selected distance function, with cosine similarity as the default.

It is recommended to configure a DataSource which pools connections for improved performance. A connection pool will avoid the latency of repeatedly creating new database connections.

If an embedding table already exists in your database, provide the table name:

```java
EmbeddingStore<TextSegment> embeddingStore = OceanBaseEmbeddingStore.builder()
   .dataSource(myDataSource)
   .embeddingTable("my_embedding_table")
   .build();
```

If the table does not already exist, it can be created by passing a CreateOption to the builder:

```java
EmbeddingStore<TextSegment> embeddingStore = OceanBaseEmbeddingStore.builder()
   .dataSource(myDataSource)
   .embeddingTable("my_embedding_table", CreateOption.CREATE_IF_NOT_EXISTS)
   .build();
```

By default, the embedding table will have the following columns:

| Name | Type | Description |
| ---- | ---- | ----------- |
| id | VARCHAR(36) | Primary key. Used to store UUID strings generated when the embedding is stored |
| embedding | VECTOR(*, FLOAT32) | Stores the embedding vector |
| text | TEXT | Stores the text segment |
| metadata | JSON | Stores the metadata |

If the columns of your existing table do not match the predefined column names or you would like to use different column names, you can use an EmbeddingTable builder to configure your embedding table:

```java
OceanBaseEmbeddingStore<TextSegment> embeddingStore =
OceanBaseEmbeddingStore.builder()
    .dataSource(myDataSource)
    .embeddingTable(EmbeddingTable.builder("my_embedding_table")
            .createOption(CreateOption.CREATE_OR_REPLACE) // use NONE if the table already exists
            .idColumn("id_column_name")
            .embeddingColumn("embedding_column_name")
            .textColumn("text_column_name")
            .metadataColumn("metadata_column_name")
            .vectorDimension(1536) // Specify the dimension of your embedding vectors
            .build())
    .build();
```

### Distance Functions

OceanBase supports multiple distance functions for vector similarity search. You can specify the distance function when building the embedding store:

```java
OceanBaseEmbeddingStore<TextSegment> embeddingStore =
OceanBaseEmbeddingStore.builder()
    .dataSource(myDataSource)
    .embeddingTable("my_embedding_table")
    .distanceFunction(DistanceFunction.COSINE_DISTANCE)
    .build();
```

Available distance functions:
- `COSINE_DISTANCE` (default)
- `DOT_PRODUCT`
- `L2_DISTANCE`

### Metadata Filtering

The OceanBase embedding store supports filtering results based on metadata:

```java
List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
    EmbeddingSearchRequest.builder()
        .queryEmbedding(embedding)
        .maxResults(10)
        .filter(MetadataFilterBuilder.metadataKey("category").isEqualTo("technology"))
        .build()
).matches();
```

For more information about OceanBase's vector capabilities, refer to the [OceanBase documentation](https://www.oceanbase.com/docs/common-oceanbase-database-cn-1000000002826816).
