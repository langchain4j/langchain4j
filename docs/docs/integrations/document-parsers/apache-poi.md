---
sidebar_position: 3
---

# Apache POI


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-poi</artifactId>
    <version>1.23.0-beta33</version>
</dependency>
```


## APIs

- `ApachePoiDocumentParser`


## Highly Compressed Files

Apache POI rejects highly compressed ZIP-based documents (DOCX, PPTX, XLSX) with a check of their compression ratio.
As soon as Apache Tika parses a document in the same JVM, or creates its parsers there (for example with
`new AutoDetectParser()`, also when that parser is then passed to Tika's `ForkParser`),
Tika turns this check off for the whole JVM.
It also raises POI's limit on the number of files inside such a document from 1,000 to 10,000 for the whole JVM.
This also affects `ApachePoiDocumentParser`, for example when `langchain4j-document-parser-apache-tika`
or Easy RAG is used in the same application.

Without this check, some small, highly compressed documents can need a lot of memory.
An `OutOfMemoryError` in the JVM of your application can affect the whole application, even with a heap limit (`-Xmx`).
So if you parse documents from sources you do not trust, parse them in a separate process with its own heap limit.


## Examples

- [ApachePoiDocumentParserTest](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-apache-poi/src/test/java/dev/langchain4j/data/document/parser/apache/poi/ApachePoiDocumentParserTest.java)
