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
    <version>1.7.1-beta14</version>
</dependency>
```

## APIs

- `ChromaEmbeddingStore`


## Examples

- [ChromaEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/chroma-example/src/main/java/ChromaEmbeddingStoreExample.java)

## Supported API Versions
Chroma has multiple REST API versions:
- until version 0.5.16: only API V1 is supported
- versions 0.5.16 to 0.6.3: API V1 and V2 are supported (there are some bugs in V1 API introduced around 0.6.2)
- versions after 0.7.0: only API V2 is supported, so you need to select the proper version
when configuring the `ChromaEmbeddingStore`:
```java
ChromaEmbeddingStore.builder()
    .apiVersion(ChromaApiVersion.V2)
    .baseUrl(...)
    .tenantName(...)
    .databaseName(...)
    .collectionName(...)
    .build();
```

## Current Limitations

- Chroma cannot filter by greater and less than of alphanumeric metadata, only int and float are supported
- Chroma filters by *not* as following: if you filter by "key" not equals "a",
  then in fact all items with "key" != "a" value are returned, but no items without "key" metadata!
