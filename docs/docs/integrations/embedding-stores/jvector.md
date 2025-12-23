---
sidebar_position: 32
---

# JVector

https://github.com/jbellis/jvector

JVector is a pure Java embedded vector search engine that provides high-performance approximate nearest neighbor (ANN) search using graph-based indexing. It merges the DiskANN and HNSW algorithm families, offering fast similarity search with configurable accuracy/performance tradeoffs.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-jvector</artifactId>
    <version>1.9.0-beta16</version>
</dependency>
```

Note: This is a community integration module. You may need to add the langchain4j-community repository to your project configuration.

## APIs

- `JVectorEmbeddingStore`

## Features

- **Pure Java Implementation**: No native dependencies required, runs anywhere Java runs
- **Graph-Based Indexing**: Uses HNSW hierarchy with Vamana algorithm for high-performance ANN search
- **In-Memory by Default**: Fast searches with optional disk persistence
- **Configurable Performance**: Tune accuracy/speed tradeoffs with parameters like `maxDegree` and `beamWidth`
- **Multiple Similarity Functions**: Supports DOT_PRODUCT (default), COSINE, and EUCLIDEAN distance metrics
- **Thread-Safe**: Nonblocking concurrency control allows safe concurrent access
- **Disk Persistence**: Optional save/load functionality for indexes
- **Dynamic Updates**: Add and remove embeddings after index creation

## Basic Usage

### In-Memory Store

Create a simple in-memory embedding store:

```java
EmbeddingStore<TextSegment> store = JVectorEmbeddingStore.builder()
    .dimension(384)                    // Must match your embedding model's dimension
    .build();
```

### Persistent Store

Create an embedding store with disk persistence:

```java
EmbeddingStore<TextSegment> store = JVectorEmbeddingStore.builder()
    .dimension(384)
    .persistencePath("/path/to/index") // Base path for index files
    .build();

// Add embeddings...
store.add(embedding, textSegment);

// Save to disk
((JVectorEmbeddingStore) store).save();
```

When you create a store with a `persistencePath`, the index will automatically load from disk if files exist at that location.

## Configuration Options

JVector provides several builder options to tune performance:

```java
EmbeddingStore<TextSegment> store = JVectorEmbeddingStore.builder()
    .dimension(384)                    // Required: embedding dimension
    .maxDegree(16)                     // Graph connectivity (default: 16)
    .beamWidth(100)                    // Index construction quality (default: 100)
    .neighborOverflow(1.2f)            // Overflow during construction (default: 1.2)
    .alpha(1.2f)                       // Diversity parameter (default: 1.2)
    .similarityFunction(VectorSimilarityFunction.DOT_PRODUCT) // Default
    .persistencePath("/path/to/index") // Optional: enable persistence
    .build();
```

### Parameter Guidelines

- **dimension**: Must match your embedding model's output dimension (required)
- **maxDegree**: Controls graph connections per node. Higher values improve recall but use more memory. Recommended: 16 (default)
- **beamWidth**: Controls index construction quality. Higher values build better indexes but take longer. Recommended: 100 (default)
- **neighborOverflow**: Recommended 1.2 for in-memory indexes (default), 1.5 for disk-based indexes
- **alpha**: Controls edge distance vs diversity tradeoff. Recommended: 1.2 for high-dimensional vectors (default), 2.0 for low-dimensional (2D/3D) vectors
- **similarityFunction**:
  - `DOT_PRODUCT` - Fastest for normalized vectors (default)
  - `COSINE` - For cosine similarity
  - `EUCLIDEAN` - For Euclidean distance

## Persistence

JVector supports saving and loading indexes from disk:

```java
// Create store with persistence enabled
JVectorEmbeddingStore store = JVectorEmbeddingStore.builder()
    .dimension(384)
    .persistencePath("/path/to/index")
    .build();

// Add embeddings
store.add(embeddings, textSegments);

// Save to disk (creates .graph and .metadata files)
store.save();

// Later: Load automatically when creating with same path
JVectorEmbeddingStore loadedStore = JVectorEmbeddingStore.builder()
    .dimension(384)
    .persistencePath("/path/to/index")
    .build();
// All previous embeddings and index structure are restored
```

Persistence creates two files:
- `{path}.graph` - The graph index structure with vectors
- `{path}.metadata` - Embedding IDs, text segments, and metadata

## Current Limitations

- **No Metadata Filtering**: JVector does not support filtering search results by metadata during the search operation. All filtering must be done post-search.
- **Index Rebuild on Modification**: Adding or removing embeddings invalidates the index, which is rebuilt on the next search. For best performance, batch your additions when possible.
- **Dimension Must Match**: All embeddings must have the same dimension as specified during store creation.

## Performance Characteristics

JVector is optimized for:
- **Fast similarity search**: Logarithmic time complexity for searches
- **Linear scalability**: Index construction scales linearly with CPU cores
- **Memory efficiency**: In-memory indexes only, with optional disk persistence
- **High recall**: Graph-based approach typically achieves >98% recall with proper tuning

Ideal use cases:
- Embedded applications requiring vector search without external dependencies
- Development and testing environments
- Production deployments where you want full control over the index
- Applications requiring disk persistence without a separate database

## Examples

- Example code can be found in the [JVector source repository](https://github.com/jbellis/jvector/tree/main/jvector-examples)
- For LangChain4j-specific integration examples, check the test files in the [langchain4j-community-jvector module](https://github.com/langchain4j/langchain4j-community/tree/main/embedding-stores/langchain4j-community-jvector/src/test/java)
