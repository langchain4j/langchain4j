---
sidebar_position: 19
---

# PGVector

LangChain4j integrates seamlessly with [PGVector](https://github.com/pgvector/pgvector), allowing developers to store
and query vector embeddings directly in PostgreSQL. This integration is ideal for applications like semantic search,
RAG, and more.

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>1.11.0-beta19</version>
</dependency>
```

## Gradle Dependency

```implementation 'dev.langchain4j:langchain4j-pgvector:1.11.0-beta19'```

## APIs

- `PgVectorEmbeddingStore`

## Parameter Summary

| Plain Java Property     | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                    | Default Value   | Required/Optional                                                                                                                                                                                                                                                                 |
|-------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `datasource`            | The `DataSource` object used for database connections. Available only in the `PgVectorEmbeddingStore.datasourceBuilder()` builder variant. If not provided, `host`, `port`, `user`, `password`, and `database` must be provided individually in the `PgVectorEmbeddingStore.builder()` builder variant.                                                                                                                                                        | None            | Required if `host`, `port`, `user`, `password`, and `database` are not provided individually.                                                                                                                                                                                     |
| `host`                  | Hostname of the PostgreSQL server. Required if `DataSource` is not provided.                                                                                                                                                                                                                                                                                                                                                                                   | None            | Required if `DataSource` is not provided                                                                                                                                                                                                                                          |
| `port`                  | Port number of the PostgreSQL server. Required if `DataSource` is not provided.                                                                                                                                                                                                                                                                                                                                                                                | None            | Required if `DataSource` is not provided                                                                                                                                                                                                                                          |
| `user`                  | Username for database authentication. Required if `DataSource` is not provided.                                                                                                                                                                                                                                                                                                                                                                                | None            | Required if `DataSource` is not provided                                                                                                                                                                                                                                          |
| `password`              | Password for database authentication. Required if `DataSource` is not provided.                                                                                                                                                                                                                                                                                                                                                                                | None            | Required if `DataSource` is not provided                                                                                                                                                                                                                                          |
| `database`              | Name of the database to connect to. Required if `DataSource` is not provided.                                                                                                                                                                                                                                                                                                                                                                                  | None            | Required if `DataSource` is not provided                                                                                                                                                                                                                                          |
| `table`                 | The name of the database table used for storing embeddings.                                                                                                                                                                                                                                                                                                                                                                                                    | None            | Required                                                                                                                                                                                                                                                                          |
| `dimension`             | The dimensionality of the embedding vectors. This should match the embedding model being used. Use `embeddingModel.dimension()` to dynamically set it.                                                                                                                                                                                                                                                                                                         | None            | Required                                                                                                                                                                                                                                                                          |
| `useIndex`              | An IVFFlat index divides vectors into lists, and then searches a subset of those lists closest to the query vector. It has faster build times and uses less memory than HNSW but has lower query performance (in terms of speed-recall tradeoff). Should use [IVFFlat](https://github.com/pgvector/pgvector#ivfflat) index.                                                                                                                                    | `false`         | Optional                                                                                                                                                                                                                                                                          |
| `indexListSize`         | The number of lists for the IVFFlat index.                                                                                                                                                                                                                                                                                                                                                                                                                     | None            | When Required: If `useIndex` is `true`, `indexListSize` must be provided and must be greater than zero. Otherwise, the program will throw an exception during table initialization. When Optional: If `useIndex` is `false`, this property is ignored and doesn’t need to be set. |
| `createTable`           | Specifies whether to automatically create the embeddings table.                                                                                                                                                                                                                                                                                                                                                                                                | `true`          | Optional                                                                                                                                                                                                                                                                          |
| `dropTableFirst`        | Specifies whether to drop the table before recreating it (useful for tests).                                                                                                                                                                                                                                                                                                                                                                                   | `false`         | Optional                                                                                                                                                                                                                                                                          |
| `searchMode`            | Search mode to use. Options: <ul><li>**VECTOR**: Standard vector similarity search using cosine distance.</li><li>**HYBRID**: Combines vector search with full-text keyword search using Reciprocal Rank Fusion (RRF).</li></ul>                                                                                                                                                                                                                               | `VECTOR`        | Optional                                                                                                                                                                                                                                                                          |
| `rrfK`                  | The constant `k` used in the RRF (Reciprocal Rank Fusion) algorithm: `Score = 1/(k + rank_vector) + 1/(k + rank_keyword)`. Lower values (20-40) emphasize top results more; higher values (80-100) create more balanced rankings. Only relevant when `searchMode` is set to `HYBRID`.                                                                                                                                                                          | `60`            | Optional. Only used in HYBRID search mode.                                                                                                                                                                                                                                        |
| `textSearchConfig`      | PostgreSQL text search configuration name used for keyword search (e.g., `simple`, `english`, `german`). Only applies when `searchMode` is `HYBRID`.                                                                                                                                                                                                                                                                                                  | `simple`        | Optional. Only used in HYBRID search mode.                                                                                                                                                                                                                                        |
| `metadataStorageConfig` | Configuration object for handling metadata associated with embeddings. Supports three storage modes: <ul><li>**COLUMN_PER_KEY**: For static metadata when you know the metadata keys in advance.</li><li>**COMBINED_JSON**: For dynamic metadata when you don't know the metadata keys in advance. Stores data as JSON. (Default)</li><li>**COMBINED_JSONB**: Similar to JSON, but stored in binary format for optimized querying on large datasets.</li></ul> | `COMBINED_JSON` | Optional. If not set, a default configuration is used with `COMBINED_JSON`.                                                                                                                                                                                                       |

## Examples

To demonstrate the capabilities of PGVector, you can use a Dockerized PostgreSQL setup. It leverages Testcontainers to
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

Here are two code examples showing how to create a `PgVectorEmbeddingStore`. The first uses only the required parameters,
while the second configures all available parameters.

1. Only Required Parameters

```java
EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
        .host("localhost")                           // Required: Host of the PostgreSQL instance
        .port(5432)                                  // Required: Port of the PostgreSQL instance
        .database("postgres")                        // Required: Database name
        .user("my_user")                             // Required: Database user
        .password("my_password")                     // Required: Database password
        .table("my_embeddings")                      // Required: Table name to store embeddings
        .dimension(embeddingModel.dimension())       // Required: Dimension of embeddings
        .build();
```

2. All Parameters Set

In this variant, we include all the commonly used optional parameters like useIndex, indexListSize,
createTable, dropTableFirst, and metadataStorageConfig. Adjust these values as needed:

 ```java
EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
        // Required parameters
        .host("localhost")
        .port(5432)
        .database("postgres")
        .user("my_user")
        .password("my_password")
        .table("my_embeddings")
        .dimension(embeddingModel.dimension())

        // Optional parameters
        .useIndex(true)                             // Enable IVFFlat index
        .indexListSize(100)                         // Number of lists for IVFFlat index
        .createTable(true)                          // Automatically create the table if it doesn’t exist
        .dropTableFirst(false)                      // Don’t drop the table first (set to true if you want a fresh start)
        .metadataStorageConfig(MetadataStorageConfig.combinedJsonb()) // Store metadata as a combined JSONB column

        .build();
```

Use the first example if you just want the minimal configuration to get started quickly.
The second example shows how you can leverage all available builder parameters for more control and customization.

## Complete RAG Example with PGVector

This section demonstrates how to build a complete Retrieval-Augmented Generation (RAG) system using PostgreSQL with the PGVector extension for semantic search.

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
EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
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
List<EmbeddingMatch<TextSegment>> relevantSegments = embeddingStore.findRelevant(
        questionEmbedding,
        3  // Retrieve top 3 most similar chunks
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

EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.datasourceBuilder()
        .datasource(dataSource)
        .table("document_embeddings")
        .dimension(384)
        .build();
```

#### 2. Index Optimization
For large datasets (>100k embeddings), enable IVFFlat indexing to improve query performance:

```java
EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
        // ... other config ...
        .useIndex(true)
        .indexListSize(100)  // Adjust based on dataset size
        .build();
```

**Note**: Index creation can take time on large datasets. Balance between query speed and index build time.

#### 3. Metadata Storage
For better query performance on large datasets, use JSONB for metadata storage:

```java
import dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig;

EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
        // ... other config ...
        .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
        .build();
```

#### 4. Chunk Size Tuning
Experiment with different chunk sizes based on your use case:
- **Smaller chunks (200-300 tokens)**: Better precision, more specific answers
- **Larger chunks (500-800 tokens)**: More context, but may reduce relevance

#### 5. Error Handling
Always handle database connection failures gracefully:

```java
try {
    embeddingStore.add(embedding, textSegment);
} catch (Exception e) {
    logger.error("Failed to store embedding", e);
    // Implement retry logic or fallback behavior
}
```

### Hybrid Search (Vector + Keyword)

PGVector supports **hybrid search** that combines vector similarity search with PostgreSQL's full-text keyword search. This approach often provides better results than vector-only search by leveraging both semantic understanding and exact keyword matching.

#### When to Use Hybrid Search

- When you need both semantic similarity and exact keyword matches
- For queries with domain-specific terms, product names, or technical jargon
- To improve retrieval accuracy in RAG applications

#### Configuration

Enable hybrid search by setting the `searchMode` parameter:

```java
import dev.langchain4j.store.embedding.pgvector.SearchMode;

EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
        .host("localhost")
        .port(5432)
        .database("postgres")
        .user("my_user")
        .password("my_password")
        .table("document_embeddings")
        .dimension(embeddingModel.dimension())
        .searchMode(SearchMode.HYBRID)  // Enable hybrid search (default: SearchMode.VECTOR)
        .textSearchConfig("english")    // Optional: PostgreSQL text search config (default: "simple")
        .rrfK(60)    // Optional: RRF algorithm parameter (default: 60)
        .build();
```

#### Usage

When using hybrid search, you must provide **both** the embedding and the query text:

```java
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

String question = "How to configure PostgreSQL vector search?";

// Generate embedding for the query
Embedding questionEmbedding = embeddingModel.embed(question).content();

// Search with both embedding and text (required for HYBRID mode)
EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(questionEmbedding)  // For vector similarity search
        .query(question)                    // For keyword search (REQUIRED in HYBRID mode)
        .maxResults(3)
        .build();

List<EmbeddingMatch<TextSegment>> results = embeddingStore.search(request);
```

#### How It Works

Hybrid search uses **Reciprocal Rank Fusion (RRF)** to combine results:

1. **Vector Search**: Finds semantically similar text using cosine similarity
2. **Keyword Search**: Finds text with matching keywords using PostgreSQL's `tsvector`
3. **RRF Fusion**: Combines rankings using the formula:

```
RRF_Score = 1/(k + rank_vector) + 1/(k + rank_keyword)
```

Where:
- `k` is a constant (configurable via `rrfK()`, default: 60)
- `rank_vector` is the ranking position from vector search (1 = best match)
- `rank_keyword` is the ranking position from keyword search (1 = best match)

**Example Score Calculation** (k = 80 as used in tests):

If a document ranks 1st in both vector and keyword search:
```
Score = 1/(80+1) + 1/(80+1)
      = 1/81 + 1/81
      ≈ 0.0247
```

**Score range notes**
- Maximum score is `2/(k+1)` when a result ranks first in both searches (e.g., k=60 → ~0.0328; k=80 → ~0.0247).
- Scores decay toward 0 as ranks increase; they do **not** reach 1.0.
- RRF scores are rank-based and not directly comparable to cosine similarity (0.0–1.0) from vector-only search.

#### Key Differences from Vector-Only Search

| Aspect | Vector Search | Hybrid Search |
|--------|--------------|---------------|
| **Query Input** | Only `queryEmbedding` | Both `queryEmbedding` AND `query` text |
| **Score Type** | Cosine similarity (0.0-1.0) | RRF rank-based score (max `≈ 2/(k+1)`; ~0.033 with k=60) |
| **Best For** | Semantic similarity, paraphrasing | Exact keywords + semantic meaning |

#### Tuning RRF Parameter

Adjust the `rrfK` parameter to control ranking sensitivity:

```java
.rrfK(40)   // More weight to top-ranked results (higher scores for top matches)
.rrfK(80)   // More balanced between top and lower-ranked results
```

- **Lower k (20-40)**: Emphasizes top-ranked results more
- **Higher k (80-100)**: More balanced ranking distribution
- **Default (60)**: Good balance for most use cases

### Spring Boot Integration

For a complete production-ready example integrating pgvector with Spring Boot microservices,
see the [pgvector RAG Spring Boot example](https://github.com/langchain4j/langchain4j-examples/tree/main/pgvector-rag-springboot).

This example demonstrates:
- Spring Boot autoconfiguration for PgVectorEmbeddingStore
- REST API endpoints for document ingestion and querying
- Proper connection pooling and error handling
- Docker Compose setup for local development

- [More Examples](https://github.com/langchain4j/langchain4j-examples/tree/main/pgvector-example/src/main/java)
