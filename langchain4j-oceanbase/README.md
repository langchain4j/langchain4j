# OceanBase Embedding Store
This module implements `EmbeddingStore` using [OceanBase](https://www.oceanbase.com/) database.

## Features

- Store embeddings with metadata (JSON format)
- Vector similarity search with cosine, L2, or inner product distance
- **Hybrid search** combining vector similarity and fulltext search (RRF algorithm)
- Filter search results by metadata fields and table columns
- Automatic table and vector index creation
- Customizable field names and distance metrics

## Requirements

- OceanBase database instance (version 4.3.5 or later)
- OceanBase obvec_jdbc SDK (version 1.0.4 or later)

## Installation

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-oceanbase</artifactId>
    <version>1.10.0-beta18-SNAPSHOT</version>
</dependency>
```

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
    .uri("jdbc:oceanbase://127.0.0.1:2881/test")
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
    .uri("jdbc:oceanbase://127.0.0.1:2881/test")
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

### Distance Metrics

OceanBase embedding store supports three distance metrics. The distance values are automatically converted to relevance scores in the range [0, 1], where 1 represents the most relevant match.

#### 1. Cosine Distance (Default) - `"cosine"`

**Best for:** Text embeddings, semantic similarity search

**How it works:**
- OceanBase `cosine_distance` returns values in range [0, 2]
  - `0` = identical vectors (same direction)
  - `1` = orthogonal vectors (perpendicular)
  - `2` = opposite vectors (completely opposite direction)
- Converted to relevance score: `score = (2 - distance) / 2`
- Results are independent of vector magnitude

**Example:**
```java
.metricType("cosine")  // Default, recommended for text embeddings
```

#### 2. L2 Distance (Euclidean) - `"l2"` or `"euclidean"`

**Best for:** When both direction and magnitude matter

**How it works:**
- Measures straight-line distance between vectors
- Range: [0, ∞)
- Converted to relevance score: `score = 1 / (1 + distance)`

**Example:**
```java
.metricType("l2")  // or "euclidean"
```

#### 3. Inner Product - `"inner_product"` or `"ip"`

**Best for:** Normalized embeddings, performance-critical applications

**How it works:**
- Measures dot product of vectors
- For normalized vectors, range is [-1, 1]
- Converted to relevance score: `score = (inner_product + 1) / 2`

**Example:**
```java
.metricType("inner_product")  // or "ip"
```

**Reference:** [OceanBase Vector Distance Functions](https://www.oceanbase.com/docs/common-oceanbase-database-cn-1000000004475471)

### Filtering

OceanBase embedding store supports filtering search results by metadata fields and table columns.

#### Filter by Metadata Fields

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

#### Filter by Table Columns

You can also filter by table columns directly (id, text, metadata, vector):

```java
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;

// Filter by ID field
Filter filter = new IsIn("id", List.of("id1", "id2", "id3"));

// Filter by text field (contains)
Filter textFilter = new ContainsString("text", "programming");

// Filter by exact text match (if text field is a table column)
Filter exactTextFilter = new IsEqualTo("text", "Java programming");
```

**Note**: When filtering by table columns, use the actual field names defined in your `FieldDefinition`. The mapper automatically recognizes common aliases:

- `id` → id field
- `text` or `document` → text field  
- `metadata` → metadata field
- `vector` or `embedding` → vector field

#### Supported Filter Operations

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

#### Filter with Hybrid Search

Filters work with both regular vector search and hybrid search:

```java
// Hybrid search with filter
EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
    EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .query("search text")
        .filter(metadataKey("category").isEqualTo("programming"))
        .maxResults(10)
        .build()
);
```

#### Complex Filter Examples

```java
import dev.langchain4j.store.embedding.filter.logical.Or;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;

// OR condition
Filter filter = new Or(
    metadataKey("category").isEqualTo("programming"),
    metadataKey("category").isEqualTo("ai")
);

// Complex filter: (category = "programming" AND language IN ("Java", "Python")) OR year > 2020
Filter complexFilter = new Or(
    new And(
        metadataKey("category").isEqualTo("programming"),
        metadataKey("language").isIn("Java", "Python")
    ),
    new IsGreaterThan("year", 2020)
);

// NOT condition
Filter notFilter = new Not(metadataKey("category").isEqualTo("deprecated"));
```

### Hybrid Search

Hybrid search combines vector similarity search and fulltext search to provide better search results.
When enabled, it automatically creates a fulltext index on the text field and combines results using the **Reciprocal Rank Fusion (RRF)** algorithm.

#### Enable Hybrid Search

```java
OceanBaseEmbeddingStore embeddingStore = OceanBaseEmbeddingStore.builder()
    .uri("jdbc:oceanbase://127.0.0.1:2881/test")
    .user("root@test")
    .password("password")
    .tableName("embeddings")
    .dimension(384)
    .enableHybridSearch(true)  // Enable hybrid search
    .build();
```

#### Perform Hybrid Search

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

#### How Hybrid Search Works

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

### Using Existing ObVecClient

```java
import com.oceanbase.obvec_jdbc.ObVecClient;

ObVecClient obVecClient = new ObVecClient(uri, user, password);

OceanBaseEmbeddingStore embeddingStore = OceanBaseEmbeddingStore.builder()
    .obVecClient(obVecClient)
    .tableName("embeddings")
    .dimension(384)
    .build();
```

**Note**: If you only provide `ObVecClient` without connection details (uri, user, password), hybrid search will not be available as it requires JDBC connection to execute SQL queries.

## Complete Example

```java
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.oceanbase.OceanBaseEmbeddingStore;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

public class OceanBaseExample {
    public static void main(String[] args) {
        // Initialize embedding model
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        
        // Create embedding store with hybrid search enabled
        OceanBaseEmbeddingStore embeddingStore = OceanBaseEmbeddingStore.builder()
            .uri("jdbc:oceanbase://127.0.0.1:2881/test")
            .user("root@test")
            .password("password")
            .tableName("documents")
            .dimension(384)
            .enableHybridSearch(true)
            .build();
        
        // Add documents with metadata
        String id1 = embeddingStore.add(
            embeddingModel.embed("Java is a programming language").content(),
            TextSegment.from("Java is a programming language",
                new Metadata()
                    .put("category", "programming")
                    .put("language", "Java")
                    .put("year", 2023))
        );
        
        String id2 = embeddingStore.add(
            embeddingModel.embed("Python is great for data science").content(),
            TextSegment.from("Python is great for data science",
                new Metadata()
                    .put("category", "programming")
                    .put("language", "Python")
                    .put("year", 2024))
        );
        
        // Perform hybrid search with filter
        String queryText = "programming language";
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();
        
        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
            EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query(queryText)  // Enable hybrid search
                .filter(metadataKey("category").isEqualTo("programming"))  // Filter by category
                .maxResults(10)
                .minScore(0.5)  // Minimum relevance score
                .build()
        );
        
        // Process results
        results.matches().forEach(match -> {
            System.out.println("Score: " + match.score());
            System.out.println("ID: " + match.embeddingId());
            System.out.println("Text: " + match.embedded().text());
            System.out.println("Metadata: " + match.embedded().metadata());
        });
        
        // Delete documents
        embeddingStore.removeAll(List.of(id1, id2));
    }
}
```

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

### Hybrid Search

- Uses Reciprocal Rank Fusion (RRF) with k=60
- Fulltext scores are normalized to [0, 1] range
- Results are sorted by combined RRF score

## Limitations

- `removeAll(Filter)` and `removeAll()` methods are not yet supported. Use `removeAll(Collection<String> ids)` instead.
- Hybrid search requires JDBC connection information (uri, user, password). It won't work if only `ObVecClient` is provided without connection details.
- When filtering by table columns, the field names are case-insensitive but must match the actual column names or recognized aliases.

## Running the Test Suite

By default, integration tests require an OceanBase database instance. To run integration tests, set the following environment variables:

- `OCEANBASE_URI`: JDBC URI (e.g., "jdbc:oceanbase://127.0.0.1:2881/test")
- `OCEANBASE_USER`: Username (e.g., "root@test")
- `OCEANBASE_PASSWORD`: Password

Then run:

```bash
mvn test
```

### Running Specific Tests

```bash
# Run filtering tests
mvn test -Dtest=OceanBaseEmbeddingStoreIT

# Run removal tests
mvn test -Dtest=OceanBaseEmbeddingStoreRemovalIT

# Run hybrid search tests
mvn test -Dtest=OceanBaseHybridSearchIT

# Run filter-specific tests
mvn test -Dtest=OceanBaseFilterIT
```

## Algorithm Details

### Reciprocal Rank Fusion (RRF)

Hybrid search combines results from vector search and fulltext search using RRF:

- **Formula**: `score = Σ(1 / (k + rank))` where k=60
- **rank**: Position in the result list (1-based)
- Results are normalized to [0, 1] range before returning

**Advantages:**

- Doesn't depend on score ranges
- Standard algorithm used by many search systems
- Effectively balances results from multiple sources

## References

- [OceanBase Documentation](https://www.oceanbase.com/docs)
- [Reciprocal Rank Fusion](https://learn.microsoft.com/en-us/azure/search/hybrid-search-ranking)
- [LangChain4j Documentation](https://docs.langchain4j.dev)
