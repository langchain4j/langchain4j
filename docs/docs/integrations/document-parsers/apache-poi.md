---
sidebar_position: 3
---

# Apache POI


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-poi</artifactId>
    <version>1.24.0-beta34</version>
</dependency>
```


## Highly compressed files

Apache POI rejects highly compressed files with a check of the compression ratio.
When Apache Tika's OOXML parser is loaded in the same JVM (for example through `langchain4j-document-parser-apache-tika` or Easy RAG),
Tika turns this check off for the whole JVM, which also affects `ApachePoiDocumentParser`.
To parse documents from untrusted sources, parse them in a separate process or in a JVM with a bounded heap.


## APIs

- `ApachePoiDocumentParser`


## Examples

- [ApachePoiDocumentParserTest](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-apache-poi/src/test/java/dev/langchain4j/data/document/parser/apache/poi/ApachePoiDocumentParserTest.java)
