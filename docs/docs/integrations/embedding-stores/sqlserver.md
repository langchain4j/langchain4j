---
sidebar_position: 23
---

# SQL Server

The SQL Server Embedding Store integrates with the [Vector search and vector indexes](https://learn.microsoft.com/en-us/sql/sql-server/ai/vectors?view=sql-server-ver17) introduced in SQL Server 2025.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-sqlserver</artifactId>
    <version>${latest version here}</version>
</dependency>
```

## APIs

- `SQLServerEmbeddingStore`

## Usage

Instances of this store can be created by configuring a builder. The builder 
requires that a DataSource and an embedding table be provided.

It is recommended to configure a DataSource which pools connections, such as the
Universal Connection Pool or Hikari. A connection pool will avoid the latency of
repeatedly creating new database connections.

### Examples of Embedding Store Configuration

If an embedding table already exists in your database, provide the table configuration:

```java
EmbeddingStore<TextSegment> embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
   .dataSource(myDataSource)
   .embeddingTable(EmbeddingTable.builder()
           .name("my_embedding_table")
           .dimension(384) // Must specify dimension
           .build())
   .build();
```

If the table does not already exist, it can be created by setting the create option:

```java
EmbeddingStore<TextSegment> embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
   .dataSource(myDataSource)
   .embeddingTable(EmbeddingTable.builder()
           .name("my_embedding_table")
           .createOption(CreateOption.CREATE)
           .dimension(384) 
           .build())
   .build();
```

The previous option will fail if the table already exists. In that case, you can use the CREATE_IF_NOT_EXISTS option:

```java
EmbeddingStore<TextSegment> embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
   .dataSource(myDataSource)
   .embeddingTable(EmbeddingTable.builder()
           .name("my_embedding_table")
           .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
           .dimension(384) 
           .build())
   .build();
```

Finally, If you want to recreate the table, you can use the CREATE_OR_REPLACE option:

```java
EmbeddingStore<TextSegment> embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
   .dataSource(myDataSource)
   .embeddingTable(EmbeddingTable.builder()
           .name("my_embedding_table")
           .createOption(CreateOption.CREATE_OR_REPLACE)
           .dimension(384) 
           .build())
   .build();
```

If the columns of your existing table do not match the predefined column names
or you would like to use different column names, you can customize the table configuration:

```java
SQLServerEmbeddingStore embeddingStore =
SQLServerEmbeddingStore.dataSourceBuilder()
    .dataSource(myDataSource)
    .embeddingTable(EmbeddingTable.builder()
            .createOption(CreateOption.CREATE_OR_REPLACE)
            .name("my_embedding_table")
            .idColumn("id_column_name")
            .embeddingColumn("embedding_column_name")
            .textColumn("text_column_name")
            .metadataColumn("metadata_column_name")
            .dimension(1024)
            .build())
    .build();
```

You can also configure the SQL Server connection directly without providing a DataSource:

```java
SQLServerEmbeddingStore embeddingStore =
SQLServerEmbeddingStore.connectionBuilder()
    .host("localhost")
    .port(1433)
    .database("MyDatabase")
    .userName("myuser")
    .password("mypassword")
    .embeddingTable(EmbeddingTable.builder()
            .name("embeddings")
            .createOption(CreateOption.CREATE_OR_REPLACE)
            .dimension(384)
            .build())
    .build();
```

### Embeddings table schema

By default, the embedding table will have the following columns:

| Name | Type              | Description |
| ---- |-------------------| ----------- |
| id | NVARCHAR(36)      | Primary key. Used to store UUID strings which are generated when the embedding store |
| embedding | VECTOR(dimension) | Stores the embedding using SQL Server 2025 native vector type |
| text | NVARCHAR(MAX)     | Stores the text segment |
| metadata | JSON              | Stores the metadata using SQL Server 2025 native JSON data type |


## Important Notes

### Numeric Types
All number values are written as JSON Strings in the metadata fields to avoid overflow issues with numbers as `Long.MAX_VALUE`.

### Vector Storage and Similarity
SQL Server 2025+ supports native VECTOR data types and this module uses the [VECTOR_DISTANCE](https://learn.microsoft.com/en-us/sql/t-sql/functions/vector-distance-transact-sql?view=sql-server-ver17) similarity function. 
This module supports the following metrics for the `VECTOR_DISTANCE` function:

- **COSINE**: Cosine similarity (default)
- **EUCLIDEAN**: Euclidean distance. The euclidean metric needs to perform some additional calculations to get the score from the distance.

### JSON Metadata Support

SQL Server 2025 provides native JSON data type support and JSON indexing capabilities. The module 
uses the native JSON data type for metadata storage and supports creating JSON indexes for 
optimized metadata filtering using [JSON_VALUE](https://learn.microsoft.com/es-es/sql/t-sql/functions/json-value-transact-sql?view=sql-server-ver17) function.

You can configure JSON index creation for specific metadata keys, optionally indicating the order of the keys:

```java
EmbeddingTable embeddingTable = EmbeddingTable.builder()
    .name("test_table")
    .createOption(CreateOption.CREATE_OR_REPLACE)
    .dimension(4)
    .build();

SQLServerEmbeddingStore embeddingStore =
    SQLServerEmbeddingStore.dataSourceBuilder()
        .dataSource(myDataSource)
        .embeddingTable(embeddingTable)
        .addIndex(Index.jsonIndexBuilder()
            .createOption(CreateOption.CREATE_OR_REPLACE)
            .key("author", String.class, JSONIndexBuilder.Order.ASC)
            .key("year", Integer.class)
            .build()
        )
        .build();
```

- Indexes created with `Index.jsonIndexBuilder()` do not support the `CreateOption.CREATE_IF_NOT_EXISTS` option.

## Limitations

- Vector indexing performance depends on data size and distribution
- DiskANN indexes on the vector column are not supported
- The database collation should be set to a case-sensitive collation for metadata case-sensitive string comparisons
- Distance DOT metric is not supported
