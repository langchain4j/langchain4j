# Google AlloyDB for PostgreSQL

[AlloyDB](https://cloud.google.com/alloydb) is a fully managed relational database service that offers high performance, seamless integration, and impressive scalability. AlloyDB is 100% compatible with PostgreSQL. Extend your database application to build AI-powered experiences leveraging AlloyDB's Langchain integrations.

This module implements `EmbeddingStore` backed by an AlloyDB for PostgreSQL database.

### Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artificatId>langchain4j-community-alloydb-pg</artificatId>
    <version>${latest version here}</version>
</dependency>
```

## AlloyDBEmbeddingStore Usage

Use a vector store to store text embedded data and perform vector search. Instances of `AlloyDBEmbeddingStore` can be created by configuring provided `Builder`, it requires the following:

- `AlloyDBEngine` instance
- table name
- schema name (optional, default: "public")
- content column (optional, default: "content")
- embedding column (optional, default: "embedding")
- id column (optional, default: "langchain_id")
- metadata column names (optional)
- additional metadata json column (optional, default: "langchain_metadata")
- ignored metadata column names (optional)
- distance strategy (optional, default:DistanceStrategy.COSINE_DISTANCE)
- query options (optional)

example usage:
```java
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.engine.EmbeddingStoreConfig;
import dev.langchain4j.engine.AlloyDBEngine;
import dev.langchain4j.engine.MetadataColumn;
import dev.langchain4j.store.embedding.alloydb.AlloyDBEmbeddingStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

AlloyDBEngine engine = new AlloyDBEngine.Builder()
    .projectId("")
    .region("")
    .cluster("")
    .instance("")
    .database("")
    .build();

AlloyDBEmbeddingStore store = new AlloyDBEmbeddingStore.Builder(engine, TABLE_NAME)
    .build();

List<String> testTexts = Arrays.asList("cat", "dog", "car", "truck");
List<Embedding> embeddings = new ArrayList<>();
List<TextSegment> textSegments = new ArrayList<>();
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

for (String text : testTexts) {
    Map<String, Object> metaMap = new HashMap<>();
    metaMap.put("my_metadata", "string");
    Metadata metadata = new Metadata(metaMap);
    textSegments.add(new TextSegment(text, metadata));
    embeddings.add(MyEmbeddingModel.embed(text).content());
}
List<String> ids = store.addAll(embeddings, textSegments);
// search for "cat"
EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(embeddings.get(0))
        .maxResults(10)
        .minScore(0.9)
        .build();
List<EmbeddingMatch<TextSegment>> result = store.search(request).matches();
// remove "cat"
store.removeAll(singletonList(result.get(0).embeddingId()));
```
