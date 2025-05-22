---
sidebar_position: 20
---

# Pinecone

https://www.pinecone.io/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pinecone</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

## Known Issues

- https://github.com/langchain4j/langchain4j/issues/1948
Pinecone stores all numbers as [floating-point values](https://docs.pinecone.io/guides/data/filter-with-metadata#supported-metadata-types),
which means `Integer` and `Long` values (e.g., 1746714878034235396) stored in `Metadata`
may be corrupted and returned as incorrect numbers!
Possible workaround: convert integer/double values to `String` before storing them in `Metadata`.
Please note that in this case metadata filtering might not work properly!

## APIs

- `PineconeEmbeddingStore`


## Examples

- [PineconeEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/pinecone-example/src/main/java/PineconeEmbeddingStoreExample.java)
