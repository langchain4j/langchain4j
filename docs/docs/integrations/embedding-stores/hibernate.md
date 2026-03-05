---
sidebar_position: 19
---

# Hibernate

LangChain4j integrates seamlessly with [Hibernate](https://github.com/hibernate/hibernate-orm), allowing developers to store
and query vector embeddings directly in all databases that Hibernate supports. This integration is ideal for applications like semantic search,
RAG, and more.

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-hibernate</artifactId>
    <version>1.12.0-beta20</version>
</dependency>
```

## Gradle Dependency

```implementation 'dev.langchain4j:langchain4j-hibernate:1.12.0-beta20'```

## APIs

- `HibernateEmbeddingStore`

## Parameter Summary

### Generic store

When using just the `EmbeddingStore` API without wanting to worry about Hibernate specifics, like entity class definition
and the configuration of Hibernate, this kind of store is preferred.

To configure it, use either `HibernateEmbeddingStore.dynamicBuilder()` or `HibernateEmbeddingStore.dynamicDatasourceBuilder()`.

| Plain Java Property | Description                                                                                                                                                                                                                                                                                                                                                                                    | Default Value | Required/Optional                                                                                                                                                                                                                                                                                                                            |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `datasource`        | The `DataSource` object used for database connections. Available only in the `HibernateEmbeddingStore.dynamicDatasourceBuilder()` builder variant. If not provided, `jdbcUrl`, `user` and `password` must be provided individually in the `HibernateEmbeddingStore.dynamicBuilder()` builder variant.                                                                                          | None          | Required if `jdbcUrl`, `user` and `password` are not provided individually.                                                                                                                                                                                                                                                                  |
| `jdbcUrl`           | JDBC URL of the database server. Required if `DataSource` and `host`, `port`, `database` are not provided. Available only in the `HibernateEmbeddingStore.dynamicBuilder()` builder variant.                                                                                                                                                                                                   | None          | Required if `DataSource` or `host`, `port`, `database` are not provided                                                                                                                                                                                                                                                                      |
| `host`              | Hostname of the database server. Required if neither `DataSource` or `jdbcUrl` are not provided. Available only in the `HibernateEmbeddingStore.dynamicBuilder()` builder variant.                                                                                                                                                                                                             | None          | Required if neither `DataSource` or `jdbcUrl` are not provided                                                                                                                                                                                                                                                                               |
| `port`              | Port number of the database server. Required if neither `DataSource` or `jdbcUrl` are not provided. Available only in the `HibernateEmbeddingStore.dynamicBuilder()` builder variant.                                                                                                                                                                                                          | None          | Required if neither `DataSource` or `jdbcUrl` are not provided                                                                                                                                                                                                                                                                               |
| `database`          | Name of the database to connect to. Required if neither `DataSource` or `jdbcUrl` are not provided. Available only in the `HibernateEmbeddingStore.dynamicBuilder()` builder variant.                                                                                                                                                                                                          | None          | Required if neither `DataSource` or `jdbcUrl` are not provided                                                                                                                                                                                                                                                                               |
| `databaseKind`      | The database kind. Required if `DataSource` is provided or the kind can't be inferred from the `jdbcUrl`.                                                                                                                                                                                                                                                                                      | None          | Required if `DataSource` is provided or the kind can't be inferred from the `jdbcUrl`                                                                                                                                                                                                                                                        |
| `user`              | Username for database authentication. Required if `DataSource` is not provided. Available only in the `HibernateEmbeddingStore.dynamicBuilder()` builder variant.                                                                                                                                                                                                                              | None          | Required if `DataSource` is not provided                                                                                                                                                                                                                                                                                                     |
| `password`          | Password for database authentication. Required if `DataSource` is not provided. Available only in the `HibernateEmbeddingStore.dynamicBuilder()` builder variant.                                                                                                                                                                                                                              | None          | Required if `DataSource` is not provided                                                                                                                                                                                                                                                                                                     |
| `table`             | The name of the database table used for storing embeddings.                                                                                                                                                                                                                                                                                                                                    | None          | Required                                                                                                                                                                                                                                                                                                                                     |
| `dimension`         | The dimensionality of the embedding vectors. This should match the embedding model being used. Use `embeddingModel.dimension()` to dynamically set it.                                                                                                                                                                                                                                         | None          | Required                                                                                                                                                                                                                                                                                                                                     |
| `createIndex`       | Specifies whether to automatically create an index for the vector embedding.                                                                                                                                                                                                                                                                                                                   | `false`       | Optional                                                                                                                                                                                                                                                                                                                                     |
| `indexType`         | The database specific type of index to use e.g. `ivfflat`, `hnsw`. An IVFFlat index divides vectors into lists, and then searches a subset of those lists closest to the query vector. It has faster build times and uses less memory than HNSW but has lower query performance (in terms of speed-recall tradeoff). Should use [IVFFlat](https://github.com/pgvector/pgvector#ivfflat) index. | None          | Optional. Defaults to the preferred index type e.g. `ivfflat` on PostgreSQL                                                                                                                                                                                                                                                                  |
| `indexOptions`      | The options to configure on the index for the vector embedding.                                                                                                                                                                                                                                                                                                                                | None          | When Required: If `createIndex` is `true` and the index type is `ivfflat`, on PostgreSQL the `lists = 1` option must be provided and must be greater than zero. Otherwise, the program will throw an exception during table initialization. When Optional: If `createIndex` is `false`, this property is ignored and doesn’t need to be set. |
| `createTable`       | Specifies whether to automatically create the embeddings table.                                                                                                                                                                                                                                                                                                                                | `false`       | Optional                                                                                                                                                                                                                                                                                                                                     |
| `dropTableFirst`    | Specifies whether to drop the table before recreating it (useful for tests).                                                                                                                                                                                                                                                                                                                   | `false`       | Optional                                                                                                                                                                                                                                                                                                                                     |
| `distanceFunction`  | The distance function to use for vector search. Supports varies based on database: <ul><li>**COSINE**</li><li>**EUCLIDEAN**</li><li>**EUCLIDEAN_SQUARED**</li><li>**MANHATTAN**</li><li>**INNER_PRODUCT**</li><li>**NEGATIVE_INNER_PRODUCT**</li><li>**HAMMING**</li><li>**JACCARD**</li></ul>                                                                                                 | `COSINE`      | Optional. If not set, a default configuration is used with `COSINE`.                                                                                                                                                                                                                                                                         |

### Entity store

To make use of an existing Hibernate entity model in the `EmbeddingStore` API, or to apply data model customizations,
the entity store is preferred.

To configure it, use either `HibernateEmbeddingStore.builder()`.

| Plain Java Property             | Description                                                                                                                                                                                                                                                                                    | Default Value | Required/Optional                                                                                  |
|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|----------------------------------------------------------------------------------------------------|
| `sessionFactory`                | The `SessionFactory` object where the `entityClass` is part of.                                                                                                                                                                                                                                | None          | Required                                                                                           |
| `databaseKind`                  | The database kind. Required if kind can't be inferred from the Hibernate ORM dialect.                                                                                                                                                                                                          | None          | Required if kind can't be inferred from the Hibernate ORM dialect                                  |
| `entityClass`                   | Specifies the entity class of the `SessionFactory` to use for the `EmbeddingStore`.                                                                                                                                                                                                            | None          | Required                                                                                           |
| `embeddingAttributeName`        | Specifies the name of the entity attribute that represents the vector embedding.                                                                                                                                                                                                               | None          | Optional. If not set, the entity is scanned for an attribute annotated with `@Embedding`           |
| `embeddedTextAttributeName`     | Specifies the name of the entity attribute that represents the source text of the vector embedding.                                                                                                                                                                                            | None          | Optional. If not set, the entity is scanned for an attribute annotated with `@EmbeddedText`       |
| `unmappedMetadataAttributeName` | Specifies the name of the entity attribute that represents the JSON column where unmapped metadata is stored.                                                                                                                                                                                  | None          | Optional. If not set, the entity is scanned for an attribute annotated with `@UnmappedMetadata`    |
| `metadataAttributeNames`        | Specifies the names of the entity attributes that are explicitly mapped to text metadata.                                                                                                                                                                                                      | None          | Optional. If not set, the entity is scanned for an attribute annotated with `@MetadataAttribute`   |
| `distanceFunction`              | The distance function to use for vector search. Supports varies based on database: <ul><li>**COSINE**</li><li>**EUCLIDEAN**</li><li>**EUCLIDEAN_SQUARED**</li><li>**MANHATTAN**</li><li>**INNER_PRODUCT**</li><li>**NEGATIVE_INNER_PRODUCT**</li><li>**HAMMING**</li><li>**JACCARD**</li></ul> | `COSINE`      | Optional. If not set, a default configuration is used with `COSINE`.                               |

## Examples

To demonstrate the capabilities, you can e.g. use a Dockerized PostgreSQL setup. It leverages Testcontainers to
run PostgreSQL with PGVector.

#### Quick Start with Docker

To quickly set up a PostgreSQL instance with the PGVector extension, you can use the following Docker command:

```
docker run --rm --name langchain4j-postgres-test-container -p 5432:5432 -e POSTGRES_USER=my_user -e POSTGRES_PASSWORD=my_password pgvector/pgvector
```

#### Explanation of the Command:

- ```docker run```: Runs a new container.
- ```--rm```: Automatically removes the container after it stops, ensuring no residual data.
- ```--name langchain4j-postgres-test-container```: Names the container langchain4j-postgres-test-container for easy
  identification.
- ```-p 5432:5432```: Maps port 5432 on your local machine to port 5432 in the container.
- ```-e POSTGRES_USER=my_user```: Sets the PostgreSQL username to my_user.
- ```-e POSTGRES_PASSWORD=my_password```: Sets the PostgreSQL password to my_password.
- ```pgvector/pgvector```: Specifies the Docker image to use, pre-configured with the PGVector extension.

Here are two code examples showing how to create a `HibernateEmbeddingStore`. The first uses only the required parameters,
while the second configures all available parameters.

1. Only Required Parameters

```java
HibernateEmbeddingStore embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
        .databaseKind(DatabaseKind.POSTGRESQL)                  // Required: The database kind
        .host("localhost")                                      // Required: Host of the database server
        .port(5432)                                             // Required: Port of the database server
        .database("postgres")                                   // Required: Database name
        .user("my_user")                                        // Required: Database user
        .password("my_password")                                // Required: Database password
        .table("my_embeddings")                                 // Required: Table name to store embeddings
        .dimension(embeddingModel.dimension())                  // Required: Dimension of embeddings
        .build();
```

2. All Parameters Set

In this variant, we include all the commonly used optional parameters like createIndex, indexOptions,
createTable, dropTableFirst, and distanceFunction. Adjust these values as needed:

 ```java
HibernateEmbeddingStore embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
        // Required parameters
        .databaseKind(DatabaseKind.POSTGRESQL)
        .host("localhost")
        .port(5432)
        .database("postgres")
        .user("my_user")
        .password("my_password")
        .table("my_embeddings")
        .dimension(embeddingModel.dimension())

        // Optional parameters
        .createIndex(true)                              // Enable vector index creation
        .indexType("ivfflat")                           // Index type IVFFlat
        .indexOptions("lists = 100")                    // Number of lists for IVFFlat index
        .createTable(true)                              // Automatically create the table if it doesn’t exist
        .dropTableFirst(false)                          // Don’t drop the table first (set to true if you want a fresh start)
        .distanceFunction(DistanceFunction.MANHATTEN)   // Use MANHATTAN distance function for vector search

        .build();
```

Use the first example if you just want the minimal configuration to get started quickly.
The second example shows how you can leverage all available builder parameters for more control and customization.

Don't forget to close the `HibernateEmbeddingStore` when you don't need it anymore, to close the underlying Hibernate resources.

#### Custom Hibernate entity

When you want to customize the data model or want to reuse an existing entity as source for the `EmbeddingStore`,
you can make use of the annotations `@Embedding`, `@EmbeddedText`, `@UnmappedMetadata` and `@MetadataAttribute` to mark the
entity attributes to use by the Hibernate `EmbeddingStore` implementation. 

```java
@Entity
public class MyEmbeddingEntity {
    @Id
    UUID id;
    @Embedding
    @Array(length = 384)                // The dimension of the embedding vector based on the embedding model
    float[] embedding;
    @EmbeddedText
    String text;
    @UnmappedMetadata
    Map<String, Object> metadata;       // Can be either a Map<String, Object> or a String
    
    @MetadataAttribute
    String mimeType;                    // Explicitly mapped. Synchronizes TextSegment#metadata with this attribute
    @MetadataAttribute
    String fileName;                    // Explicitly mapped. Synchronizes TextSegment#metadata with this attribute
}
```

The builder will then look for these annotations and derive the attribute names.

```java
HibernateEmbeddingStore embeddingStore = HibernateEmbeddingStore.builder()
        .sessionFactory(sessionFactory)         // Required: The SessionFactory containing your entity class
        .entityClass(MyEmbeddingEntity.class)   // Required: The embedding entity class
        .build();
```

Alternatively, if annotating the entity model is not desired, the attribute names can also be provided explicitly.

```java
HibernateEmbeddingStore embeddingStore = HibernateEmbeddingStore.builder()
        .sessionFactory(sessionFactory)
        .entityClass(MyEmbeddingEntity.class)
        .embeddingAttributeName("embedding")
        .embeddedTextAttributeName("text")
        .unmappedMetadataAttributeName("metadata")
        .metadataAttributeNames("mimeType", "fileName")
        .build();
```

Metadata can also be nested within `@OneToOne`, `@ManyToOne` or `@Embedded` attributes that are also annotated with `@MetadataAttribute`,
or by specifying an explicit attribute path with the `.` (dot) separator.

```java
@Entity
public class Book {
    @Id
    private Long id;
    private String title;
    private String content;
    @MetadataAttribute
    @Embedded
    private BookDetails details = new BookDetails();
    @MetadataAttribute
    @ManyToOne(fetch = FetchType.LAZY)
    private Author author;

    @Embedding
    @Array(length = 384)
    private float[] embedding;
    @UnmappedMetadata
    private Map<String, Object> metadata;
}
@Entity
public class Author {
    @Id
    @MetadataAttribute
    @GeneratedValue
    private Long id;
    private String firstname;
    private String lastname;
}
@Embeddable
public class BookDetails {
    @MetadataAttribute
    private String language;
    private String abstractText;
}
```

The equivalent attribute paths are `details.language` and `author.id`, which are then available for filtering,
by specifying these paths as metadata keys, e.g.

```java
MetadataFilterBuilder.metadataKey("details.language").isEqualTo("English")
```

or

```java
MetadataFilterBuilder.metadataKey("author.id").isEqualTo(2L)
```

Alternatively, the `HibernateEmbeddingStore` API also provides `search` methods that allow to use the type-safe Hibernate ORM `Restriction` API.

```java
HibernateEmbeddingStore<Book> embeddingStore = embeddingStore();
embeddingStore.search(
        embedding,
        Path.from(Book.class)
            .to(Book_.details)
            .to(BookDetails_.language)
            .equalTo("English"));
```

or

```java
HibernateEmbeddingStore<Book> embeddingStore = embeddingStore();
embeddingStore.search(
        embedding,
        Path.from(Book.class)
            .to(Book_.author)
            .to(Author_.id)
            .equalTo(2L));
```

## Complete RAG Example with Hibernate

This section demonstrates how to build a complete Retrieval-Augmented Generation (RAG) system using the Hibernate integration
with PostgreSQL with the PGVector extension for semantic search.

### Overview

A RAG system consists of two main stages:
1. **Indexing Stage (Offline)**: Load documents, split into chunks, generate embeddings, and store in pgvector
2. **Retrieval Stage (Online)**: Embed user query, search similar chunks, inject context into LLM prompt

### Prerequisites

Ensure you have a PostgreSQL instance with PGVector running (see Docker setup above).

### 1. Document Ingestion (Indexing Stage)

This example shows how to load documents, split them into chunks, and store embeddings in pgvector:

```java
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

// Load document (PDF, TXT, etc.)
Document document = loadDocument("/path/to/document.pdf", new ApachePdfBoxDocumentParser());

// Split document into smaller chunks
// 300 tokens per chunk, 50 tokens overlap for context continuity
DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);

// Create embedding model (384 dimensions for AllMiniLmL6V2)
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

// Create pgvector embedding store
HibernateEmbeddingStore embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
        .databaseKind(DatabaseKind.POSTGRESQL)
        .host("localhost")
        .port(5432)
        .database("postgres")
        .user("my_user")
        .password("my_password")
        .table("document_embeddings")
        .dimension(embeddingModel.dimension())  // 384 for AllMiniLmL6V2
        .build();

// Ingest: split document, generate embeddings, and store in pgvector
EmbeddingStoreIngestor.builder()
        .documentSplitter(splitter)
        .embeddingModel(embeddingModel)
        .embeddingStore(embeddingStore)
        .build()
        .ingest(document);

System.out.println("Document ingested successfully!");
```

### 2. Querying (Retrieval Stage)

This example shows how to query the RAG system with a user question:

```java
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.List;
import java.util.stream.Collectors;

// User's question
String question = "What is the refund policy?";

// Generate embedding for the question
Embedding questionEmbedding = embeddingModel.embed(question).content();

// Search for the most similar text segments (top 3 results)
List<EmbeddingMatch<TextSegment>> relevantSegments = embeddingStore.search(
        EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(3)  // Retrieve top 3 most similar chunks
                .build()
);

// Build context from retrieved segments
String context = relevantSegments.stream()
        .map(match -> match.embedded().text())
        .collect(Collectors.joining("\n\n"));

// Create prompt with retrieved context
String promptWithContext = String.format("""
        Answer the question based on the following context.
        If the context doesn't contain relevant information, say "I don't have enough information to answer."

        Context:
        %s

        Question: %s

        Answer:
        """, context, question);

// Send to LLM with context
ChatModel chatModel = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4")
        .build();

String answer = chatModel.generate(promptWithContext);
System.out.println("Answer: " + answer);
```

### Production Considerations

Based on real-world usage, here are important considerations for production deployments:

#### 1. Connection Pooling
For production environments, use a `DataSource` with connection pooling instead of individual connection parameters:

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres");
config.setUsername("my_user");
config.setPassword("my_password");
config.setMaximumPoolSize(10);

HikariDataSource dataSource = new HikariDataSource(config);

EmbeddingStore<TextSegment> embeddingStore = HibernateEmbeddingStore.dynamicDatasourceBuilder()
        .databaseKind(DatabaseKind.POSTGRESQL)
        .datasource(dataSource)
        .table("document_embeddings")
        .dimension(384)
        .build();
```

#### 2. Index Optimization
For large datasets (>100k embeddings), enable IVFFlat indexing on PostgreSQL to improve query performance:

```java
HibernateEmbeddingStore embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
        // ... other config ...
        .createIndex(true)
        .indexOptions("lists = 100")  // Adjust based on dataset size
        .build();
```

**Note**: Index creation can take time on large datasets. Balance between query speed and index build time.
**Note**: Index maintenance can slow down data ingestion, so consider dropping and recreating indexes when ingesting big amounts of data.

#### 3. Chunk Size Tuning
Experiment with different chunk sizes based on your use case:
- **Smaller chunks (200-300 tokens)**: Better precision, more specific answers
- **Larger chunks (500-800 tokens)**: More context, but may reduce relevance

#### 4. Error Handling
Always handle database connection failures gracefully:

```java
try {
    embeddingStore.add(embedding, textSegment);
} catch (Exception e) {
    logger.error("Failed to store embedding", e);
    // Implement retry logic or fallback behavior
}
```

#### 5. Custom Hibernate entity DDL
When using a custom Hibernate entity, you are in charge of managing the DDL.
Consider creating an `import.sql` file to create indexes, e.g. for PostgreSQL:

```sql
create index if not exists my_entity_ivfflat_index 
    on my_entity using ivfflat(embedding vector_cosine_ops) with (lists = 1);
```

Refer to [Hibernate ORM documentation](https://docs.hibernate.org/orm/7.2/userguide/html_single/) for details about the configuration of `SessionFactory`.

Vector indexes for other databases have different syntax and options. Refer to the documentation of the respective database provider for details.

##### DB2

See the [vector index article](https://community.ibm.com/community/user/blogs/christian-garcia-arellano/2025/10/04/vector-indexes-in-db2-an-early-preview)
for details.

```sql
create vector index my_entity_vector_index 
    on my_entity(embedding) with distance cosine;
```

##### MariaDB

See the [`create index` statement documentation](https://mariadb.com/docs/server/reference/sql-statements/data-definition/create/create-index)
for details.

```sql
create vector index if not exists my_entity_vector_index 
    on my_entity(embedding) distance=cosine;
```

##### MySQL

MySQL HeatWave [creates indexes automatically](https://dev.mysql.com/doc/heatwave/en/mys-hw-genai-vector-index-creation.html) and doesn't require a manual index creation.

##### PostgreSQL

See the [pgvector documentation](https://github.com/pgvector/pgvector?tab=readme-ov-file#indexing) for details.

```sql
create index if not exists my_entity_ivfflat_index
    on my_entity using ivfflat(embedding vector_cosine_ops) with (lists = 1);
```

##### Oracle

See the [`create index` statement documentation](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/create-vector-index.html)
for details.

```sql
create vector index my_entity_vector_index 
    on my_entity(embedding) organization neighbor partitions with distance cosine;
```

##### SQL Server

See the [`create vector index` statement documentation](https://learn.microsoft.com/en-us/sql/t-sql/statements/create-vector-index-transact-sql?view=sql-server-ver17)
for details.

```sql
create vector index my_entity_vector_index 
    on my_entity(embedding) with (metric='cosine');
```
