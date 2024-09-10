---
sidebar_position: 6
---

# Chroma

https://www.trychroma.com/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-chroma</artifactId>
    <version>0.34.0</version>
</dependency>
```

## APIs

- `ChromaEmbeddingStore`


## Examples

- [ChromaEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/chroma-example/src/main/java/ChromaEmbeddingStoreExample.java)

## Actual Limitations

- Chroma cannot filter by greater and less than of alphanumeric metadata, only int and float are supported
- Chroma filters by *not* as following: if you filter by "key" not equals "a", then in fact all items with "key" != "a" value are returned, but no items without "key" metadata!
