---
sidebar_position: 18
---

# Oracle
The Oracle Embedding Store integrates with
the [AI Vector Search Feature](https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/overview-ai-vector-search.html) of Oracle Database.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-oracle</artifactId>
    <version>1.17.0-beta27</version>

</dependency>
```

## APIs

- `OracleEmbeddingStore`
- `OracleChatMemoryStore`


## Examples

- [OracleEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/oracle-example/src/main/java/OracleEmbeddingStoreExample.java)

## Usage

Instances of this store can be created by configuring a builder. The builder 
requires that a DataSource and an embedding table be provided. The distance 
between two vectors is calculated using [cosine similarity](https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/cosine-similarity.html)
which measures the cosine of the angle between two vectors.

It is recommended to configure a DataSource which pools connections, such as the
Universal Connection Pool or Hikari. A connection pool will avoid the latency of
repeatedly creating new database connections.

If an embedding table already exists in your database provide the table name.

```java
EmbeddingStore embeddingStore = OracleEmbeddingStore.builder()
   .dataSource(myDataSource)
   .embeddingTable("my_embedding_table")
   .build();
```

If the table does not already exist, it can be created by passing a CreateOption
to the builder.

```java
EmbeddingStore embeddingStore = OracleEmbeddingStore.builder()
   .dataSource(myDataSource)
   .embeddingTable("my_embedding_table", CreateOption.CREATE_IF_NOT_EXISTS)
   .build();
```

By default the embedding table will have the following columns:

| Name | Type | Description |
| ---- | ---- | ----------- |
| id | VARCHAR(36) | Primary key. Used to store UUID strings which are generated when the embedding store |
| embedding | VECTOR(*, FLOAT32) | Stores the embedding |
| text | CLOB | Stores the text segment |
| metadata | JSON | Stores the metadata |

If the columns of your existing table do not match the predefined column names 
or you would like to use different column names, you can use a EmbeddingTable 
builder to configure your embedding table.

```java
OracleEmbeddingStore embeddingStore =
OracleEmbeddingStore.builder()
    .dataSource(myDataSource)
    .embeddingTable(EmbeddingTable.builder()
            .createOption(CREATE_OR_REPLACE) // use NONE if the table already exists
            .name("my_embedding_table")
            .idColumn("id_column_name")
            .embeddingColumn("embedding_column_name")
            .textColumn("text_column_name")
            .metadataColumn("metadata_column_name")
            .build())
    .build();
```

The builder allows you to create indexes on the embedding and metadata columns of the
EmbeddingTable by providing an instance of the Index class. Two builders allow to
create instances of the Index class: IVFIndexBuilder and JSONIndexBuilder.

*IVFIndexBuilder* allows you to configure an **IVF (Inverted File Flat)** index on the embedding
column of the EmbeddingTable.

```java
OracleEmbeddingStore embeddingStore =
    OracleEmbeddingStore.builder()
        .dataSource(myDataSource)
        .embeddingTable(EmbeddingTable.builder()
            .createOption(CreateOption.CREATE_OR_REPLACE) // use NONE if the table already exists
            .name("my_embedding_table")
            .idColumn("id_column_name")
            .embeddingColumn("embedding_column_name")
            .textColumn("text_column_name")
            .metadataColumn("metadata_column_name")
            .build())
        .index(Index.ivfIndexBuilder().createOption(CreateOption.CREATE_OR_REPLACE).build())
        .build();
```

*JSONIndexBuilder* allows you to configure a **function-based index** on keys of the metadata
column of the EmbeddingTable.

```java
OracleEmbeddingStore.builder()
    .dataSource(myDataSource)
    .embeddingTable(EmbeddingTable.builder()
        .createOption(CreateOption.CREATE_OR_REPLACE) // use NONE if the table already exists
        .name("my_embedding_table")
        .idColumn("id_column_name")
        .embeddingColumn("embedding_column_name")
        .textColumn("text_column_name")
        .metadataColumn("metadata_column_name")
        .build())
    .index(Index.jsonIndexBuilder()
        .createOption(CreateOption.CREATE_OR_REPLACE)
        .key("name", String.class, JSONIndexBuilder.Order.ASC)
        .key("year", Integer.class, JSONIndexBuilder.Order.DESC)
        .build())
    .build();
```

For more information about Oracle AI Vector Search refer to the [documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/overview-ai-vector-search.html).

## Chat Memory

`OracleChatMemoryStore` can be used to persist chat memory in Oracle Database.

Create a table:

```sql
CREATE TABLE chat_memory (
    memory_id VARCHAR2(255) PRIMARY KEY,
    content CLOB NOT NULL
);
```

Use it in chat memory:

```java
ChatMemoryStore store = OracleChatMemoryStore.builder()
   .dataSource(myDataSource)
   .tableName("chat_memory")
   .build();

ChatMemory chatMemory = MessageWindowChatMemory.builder()
   .id("conversation-1")
   .maxMessages(10)
   .chatMemoryStore(store)
   .build();
```

`OracleChatMemoryStore` stores one row per memory id, with all messages serialized as JSON in the `content` column.
