---
sidebar_position: 1
---

# In-memory

LangChain4j provides a simple in-memory implementation of an `EmbeddingStore` interface:
`InMemoryEmbeddingStore`.
It is useful for fast prototyping and simple use cases.
It keeps `Embedding`s and associated `TextSegment`s in memory.
Search is also performed in memory.
It can also be persisted and restored to/from a JSON string or a file.

### Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.23.0</version>
</dependency>
```

## APIs

- `InMemoryEmbeddingStore` 

When adding embeddings with explicit IDs, the original contents are optional:

```java
embeddingStore.addAll(ids, embeddings, null);
```

The IDs and embeddings must have the same size. If contents are provided, they must also have the same size.
An empty batch leaves the store unchanged. A `null` list of IDs or embeddings counts as an empty list.

## Persisting

`InMemoryEmbeddingStore` can be serialized to a json string or a file:
```java
InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
embeddingStore.addAll(embeddings, embedded);

String serializedStore = embeddingStore.serializeToJson();
InMemoryEmbeddingStore<TextSegment> deserializedStore = InMemoryEmbeddingStore.fromJson(serializedStore);

String filePath = "/home/me/store.json";
embeddingStore.serializeToFile(filePath);
InMemoryEmbeddingStore<TextSegment> deserializedStore = InMemoryEmbeddingStore.fromFile(filePath);
```

## Examples

- [InMemoryEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/embedding/store/InMemoryEmbeddingStoreExample.java)
