---
sidebar_position: 28
---

# OceanBase

The OceanBase Embedding Store integrates with [OceanBase](https://www.oceanbase.com/) database for vector similarity search and hybrid search capabilities.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-oceanbase</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Note: This is a community integration module. You may need to add the langchain4j-community repository to your project configuration.

## APIs

- `OceanBaseEmbeddingStore`

## Requirements

- OceanBase database instance (version 4.3.5 or later)
- Java >= 17

## Features

- Store embeddings with metadata (JSON format)
- Vector similarity search with cosine, L2, or inner product distance
- **Hybrid search** combining vector similarity and fulltext search (RRF algorithm)
- Filter search results by metadata fields and table columns
- Automatic table and vector index creation
- Customizable field names and distance metrics

## Usage

### Basic Example

```java
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.oceanbase.OceanBaseEmbeddingStore;

// Initialize embedding model
EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

// Create embedding store
OceanBaseEmbeddingStore embeddingStore = OceanBaseEmbeddingStore.builder()
    .url("jdbc:oceanbase://127.0.0.1:2881/test")
    .user("root@test")
    .password("password")
    .tableName("embeddings")
    .dimension(384)
    .build();

// Add document with metadata
String id = embeddingStore.add(
    embeddingModel.embed("Java is a programming language").content(),
    TextSegment.from("Java is a programming language", 
        Metadata.from("category", "programming").put("language", "Java"))
);

// Search
Embedding queryEmbedding = embeddingModel.embed("programming language").content();
EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
    EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(10)
        .build()
);

// Process results
results.matches().forEach(match -> {
    System.out.println("Score: " + match.score());
    System.out.println("Text: " + match.embedded().text());
    System.out.println("Metadata: " + match.embedded().metadata());
});
```

### Advanced Configuration

```java
OceanBaseEmbeddingStore embeddingStore = OceanBaseEmbeddingStore.builder()
    .url("jdbc:oceanbase://127.0.0.1:2881/test")
    .user("root@test")
    .password("password")
    .tableName("embeddings")
    .dimension(384)
    .metricType("cosine")  // Options: "cosine", "l2", "ip"
    .retrieveEmbeddingsOnSearch(true)
    .idFieldName("id_field")
    .textFieldName("text_field")
    .metadataFieldName("metadata_field")
    .vectorFieldName("vector_field")
    .build();
```

## Distance Metrics

OceanBase embedding store supports three distance metrics. The distance values are automatically converted to relevance scores in the range [0, 1], where 1 represents the most relevant match.

### Cosine Distance (Default) - `"cosine"`

**Best for:** Text embeddings, semantic similarity search

**How it works:**
- OceanBase `cosine_distance` returns values in range [0, 2]
  - `0` = identical vectors (same direction)
  - `1` = orthogonal vectors (perpendicular)
  - `2` = opposite vectors (completely opposite direction)
- Converted to relevance score: `score = (2 - distance) / 2`
- Results are independent of vector magnitude

```java
.metricType("cosine")  // Default, recommended for text embeddings
```

### L2 Distance (Euclidean) - `"l2"` or `"euclidean"`

**Best for:** When both direction and magnitude matter

**How it works:**
- Measures straight-line distance between vectors
- Range: [0, ∞)
- Converted to relevance score: `score = 1 / (1 + distance)`

```java
.metricType("l2")  // or "euclidean"
```

### Inner Product - `"inner_product"` or `"ip"`

**Best for:** Normalized embeddings, performance-critical applications

**How it works:**
- Measures dot product of vectors
- For normalized vectors, range is [-1, 1]
- Converted to relevance score: `score = (inner_product + 1) / 2`

```java
.metricType("inner_product")  // or "ip"
```

**Reference:** [OceanBase Vector Distance Functions](https://www.oceanbase.com/docs/common-oceanbase-database-cn-1000000004475471)

## Filtering

OceanBase embedding store supports filtering search results by metadata fields and table columns.

### Filter by Metadata Fields

```java
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

// Filter by single metadata field
Filter filter = metadataKey("category").isEqualTo("programming");

// Filter with multiple conditions
Filter filter = new And(
    metadataKey("category").isEqualTo("programming"),
    metadataKey("language").isEqualTo("Java")
);

// Filter with IN operator
Filter filter = metadataKey("language").isIn("Java", "Python", "C++");

// Search with filter
EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
    EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .filter(filter)
        .maxResults(10)
        .build()
);
```

### Filter by Table Columns

You can also filter by table columns directly (id, text, metadata, vector):

```java
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;

// Filter by ID field
Filter filter = new IsIn("id", List.of("id1", "id2", "id3"));

// Filter by text field (contains)
Filter textFilter = new ContainsString("text", "programming");

// Filter by exact text match
Filter exactTextFilter = new IsEqualTo("text", "Java programming");
```

**Note**: When filtering by table columns, use the actual field names defined in your `FieldDefinition`. The mapper automatically recognizes common aliases:
- `id` → id field
- `text` or `document` → text field  
- `metadata` → metadata field
- `vector` or `embedding` → vector field

### Supported Filter Operations

- `isEqualTo`: Equal comparison
- `isNotEqualTo`: Not equal comparison
- `isGreaterThan`: Greater than comparison
- `isGreaterThanOrEqualTo`: Greater than or equal comparison
- `isLessThan`: Less than comparison
- `isLessThanOrEqualTo`: Less than or equal comparison
- `isIn`: IN operator (multiple values)
- `isNotIn`: NOT IN operator
- `containsString`: LIKE operator (pattern matching)
- `And`: Logical AND
- `Or`: Logical OR
- `Not`: Logical NOT

## Hybrid Search

Hybrid search combines vector similarity search and fulltext search to provide better search results. When enabled, it automatically creates a fulltext index on the text field and combines results using the **Reciprocal Rank Fusion (RRF)** algorithm.

### Enable Hybrid Search

```java
OceanBaseEmbeddingStore embeddingStore = OceanBaseEmbeddingStore.builder()
    .url("jdbc:oceanbase://127.0.0.1:2881/test")
    .user("root@test")
    .password("password")
    .tableName("embeddings")
    .dimension(384)
    .enableHybridSearch(true)  // Enable hybrid search
    .build();
```

### Perform Hybrid Search

```java
// Perform hybrid search by providing both query embedding and query text
EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
    EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)  // Vector embedding for similarity search
        .query("search text")            // Text query for fulltext search
        .maxResults(10)
        .build()
);
```

### How Hybrid Search Works

1. **Vector Search**: Performs similarity search using the query embedding
2. **Fulltext Search**: Performs fulltext search using `MATCH AGAINST` on the text field
3. **Result Fusion**: Combines results using RRF (Reciprocal Rank Fusion) algorithm
   - Formula: `score = Σ(1 / (k + rank))` where k=60
   - Each result from both searches contributes to the final score based on its rank
   - Results are normalized and sorted by the combined RRF score

**Benefits:**
- Better recall: Finds documents by semantic similarity or exact keywords
- Improved precision: RRF balances both search types effectively
- Handles exact keyword matches better than vector search alone

## Implementation Details

### Score Calculation

The embedding store calculates relevance scores directly in SQL queries:
- **Cosine**: `score = (2 - cosine_distance) / 2`
- **L2/Euclidean**: `score = 1 / (1 + distance)`
- **Inner Product**: `score = (inner_product + 1) / 2`

Scores are returned in the range [0, 1], where 1 represents the most relevant match.

### Metadata Handling

- Metadata is stored as JSON in the database
- Large `Long` values (> 2^53-1) are automatically serialized as strings to preserve precision
- Filtering supports both direct column filtering and JSON metadata filtering

### Table Schema

By default, the embedding table will have the following columns:

| Name | Type | Description |
| ---- | ---- | ----------- |
| id | VARCHAR(36) | Primary key. Used to store UUID strings which are generated when the embedding store |
| vector | JSON | Stores the embedding vector as JSON array |
| text | TEXT | Stores the text segment |
| metadata | JSON | Stores the metadata as JSON |

## Limitations

- `removeAll(Filter)` and `removeAll()` methods are not yet supported. Use `removeAll(Collection<String> ids)` instead.
- When filtering by table columns, the field names are case-insensitive but must match the actual column names or recognized aliases.

## References

- [OceanBase Documentation](https://www.oceanbase.com/docs)
- [Reciprocal Rank Fusion](https://learn.microsoft.com/en-us/azure/search/hybrid-search-ranking)
- [LangChain4j Documentation](https://docs.langchain4j.dev)

