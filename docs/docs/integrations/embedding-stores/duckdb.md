---
sidebar_position: 11
---

# DuckDB

https://duckdb.org/

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-duckdb</artifactId>
    <version>0.37.0</version>
</dependency>
```

## APIs

- `DuckDBEmbeddingStore`

## Examples

```java
// Init Model and Store
var embeddingStore = DuckDBEmbeddingStore.inMemory();
var embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

//Create embeddings
Stream.of(
            "DuckDB is an amazing database engine!",
            "Python really lack of typing :D")
    .forEach(text -> {
        var segment = TextSegment.from(text);
        var embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);
    });


// Search request
var queryEmbedding = embeddingModel.embed("What is the best database engine").content();
var request = EmbeddingSearchRequest.builder()
               .queryEmbedding(queryEmbedding)
               .maxResults(1)
               .build();

var relevant = embeddingStore.search(request);
EmbeddingMatch<TextSegment> embeddingMatch = relevant.matches().get(0);

// Show results
System.out.println(embeddingMatch.score()); // 0.8416415629618381
System.out.println(embeddingMatch.embedded().text()); //DuckDB is an amazing database engine!
```
