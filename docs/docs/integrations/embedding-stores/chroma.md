---
sidebar_position: 7
---

# Chroma

https://www.trychroma.com/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-chroma</artifactId>
    <version>1.6.0-beta12</version>
</dependency>
```

## APIs

- `ChromaEmbeddingStore`


## Examples

- [ChromaEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/chroma-example/src/main/java/ChromaEmbeddingStoreExample.java)

## Actual Limitations

- Chroma cannot filter by greater and less than of alphanumeric metadata, only int and float are supported
- Chroma filters by *not* as following: if you filter by "key" not equals "a", then in fact all items with "key" != "a" value are returned, but no items without "key" metadata!
- Chroma has changed its API version: until Version 0.5.16, only V1 APIs are supported, Versions >=0.5.16 to <=0.6.3 API v1 and v2 are supported, there are some bugs in V1 APIs introduced around 0.6.2 and Versions >=0.7.0 API v2 is the only supported API so you  need to select the proper version when configuring the ChromaEmbeddingStore
