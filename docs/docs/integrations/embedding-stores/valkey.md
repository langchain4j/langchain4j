---
sidebar_position: 31
---

# Valkey

https://valkey.io/


## Maven Dependency

You can use Valkey with LangChain4j in plain Java or Spring Boot applications.

### Plain Java

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-valkey</artifactId>
    <version>${latest version here}</version>
</dependency>
```

### Spring Boot

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-valkey-spring-boot-starter</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Or, you can use BOM to manage dependencies consistently:

```xml
<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-bom</artifactId>
        <version>${latest version here}</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```


## Overview

The `langchain4j-community-valkey` module provides integration with [Valkey](https://valkey.io/) as an embedding store
using Valkey's built-in vector search capabilities (Valkey 8+).

It uses the official [valkey-glide](https://github.com/valkey-io/valkey-glide) client to connect to the Valkey server.
Embeddings, text, and metadata are stored as structured JSON documents with HNSW indexing for sub-millisecond similarity search.

### Prerequisites

Requires Valkey 9.1+ (currently available as `9.1.0-rc2`). The `valkey-bundle` image includes the JSON and Search modules needed for vector indexing.

```bash
docker run -d --name valkey -p 6379:6379 valkey/valkey-bundle:9.1.0-rc2
```

:::note
Once Valkey 9.1.0 GA is released, you can use `valkey/valkey-bundle:latest`.
:::


## APIs

- `ValkeyEmbeddingStore`

### Creating a ValkeyEmbeddingStore

```java
import dev.langchain4j.community.store.embedding.valkey.ValkeyEmbeddingStore;
import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;

// 1. Create a GlideClient connection
GlideClientConfiguration config = GlideClientConfiguration.builder()
        .address(NodeAddress.builder().host("localhost").port(6379).build())
        .build();

GlideClient client = GlideClient.createClient(config).get();

// 2. Build the embedding store
ValkeyEmbeddingStore embeddingStore = ValkeyEmbeddingStore.builder()
        .client(client)
        .dimension(384)          // Must match your embedding model's output dimension
        .indexName("my-index")   // Optional, defaults to "embedding-index"
        .prefix("docs:")         // Optional, defaults to "embedding:"
        .build();
```

On `build()`, the store checks if the index exists in Valkey. If not, it creates one with HNSW indexing and COSINE distance metric (the defaults).

### Configuration Options

| Parameter | Description | Default |
|-----------|-------------|---------|
| `client` | `GlideClient` instance (required) | — |
| `dimension` | Embedding vector dimension (required if index does not exist) | — |
| `indexName` | Name of the Valkey search index | `"embedding-index"` |
| `prefix` | Key prefix for stored embeddings (should end with `:`) | `"embedding:"` |
| `metadataKeys` | Collection of metadata keys to persist as Tag fields | — |
| `metadataConfig` | Map of metadata key to `FieldInfo` for custom field types | — |
| `operationTimeoutSeconds` | Timeout in seconds for each Valkey operation | `60` |

### Distance Metrics

Valkey supports three distance metrics via the `MetricType` enum:

- `COSINE` — Cosine similarity (default)
- `IP` — Inner product
- `L2` — Euclidean distance

### Storing and Searching Embeddings

```java
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

import java.util.List;

EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

// Batch ingest
List<TextSegment> docs = List.of(
        TextSegment.from("Valkey is a high-performance in-memory data store."),
        TextSegment.from("Vector search finds similar items by embedding distance."),
        TextSegment.from("HNSW is an algorithm for approximate nearest neighbors.")
);
List<Embedding> embeddings = embeddingModel.embedAll(docs).content();
List<String> ids = embeddingStore.addAll(embeddings, docs);

// Search
Embedding queryEmbedding = embeddingModel.embed("How does similarity search work?").content();
EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
        EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.5)
                .build()
);

for (EmbeddingMatch<TextSegment> match : results.matches()) {
    System.out.printf("%.3f: %s%n", match.score(), match.embedded().text());
}
```

### Metadata Filtering

Valkey supports the following filter types on metadata:

- **Numeric fields**: `eq`, `neq`, `gt`, `gte`, `lt`, `lte`
- **Tag/Text fields**: `eq`, `neq`, `in`, `notIn`

To enable metadata filtering, configure metadata fields when building the store. For simple tag-based filtering, use `metadataKeys`:

```java
ValkeyEmbeddingStore store = ValkeyEmbeddingStore.builder()
        .client(client)
        .dimension(384)
        .metadataKeys(List.of("category", "author"))
        .build();
```

For typed fields (e.g., numeric vs. tag), use `metadataConfig`:

```java
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTCreateOptions.NumericField;
import glide.api.models.commands.FT.FTCreateOptions.TagField;

Map<String, FieldInfo> metadataConfig = Map.of(
        "category", new FieldInfo("$.category", "category", new TagField(',', true)),
        "year", new FieldInfo("$.year", "year", new NumericField())
);

ValkeyEmbeddingStore store = ValkeyEmbeddingStore.builder()
        .client(client)
        .dimension(384)
        .indexName("filtered-docs")
        .prefix("filtered:")
        .metadataConfig(metadataConfig)
        .build();
```

Then use filters in search requests:

```java
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

// TAG filter
Filter securityFilter = metadataKey("category").isEqualTo("security");

// NUMERIC filter
Filter recentFilter = metadataKey("year").isGreaterThanOrEqualTo(2025);

// Combined AND filter
Filter combined = metadataKey("category").isEqualTo("security")
        .and(metadataKey("year").isGreaterThanOrEqualTo(2025));

// OR filter
Filter either = metadataKey("category").isEqualTo("security")
        .or(metadataKey("category").isEqualTo("performance"));

EmbeddingSearchResult<TextSegment> results = store.search(
        EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .filter(combined)
                .build()
);
```


## Examples

- [ValkeyEmbeddingStoreIT](https://github.com/langchain4j/langchain4j-community/blob/main/embedding-stores/langchain4j-community-valkey/src/test/java/dev/langchain4j/community/store/embedding/valkey/ValkeyEmbeddingStoreIT.java)
