---
sidebar_position: 7
---

# Docling

[Docling](https://github.com/DS4SD/docling) is an IBM Research document processing engine that extracts text and structure from various document formats including PDF, DOCX, PPTX, and more. It provides advanced capabilities such as OCR, table extraction, and layout analysis.

This integration communicates with a running [docling-serve](https://github.com/DS4SD/docling-serve) instance via REST API.


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-docling</artifactId>
    <version>1.14.0-beta24</version>
</dependency>
```

You will also need a client implementation for `DoclingServeApi`, such as:

```xml
<dependency>
    <groupId>ai.docling</groupId>
    <artifactId>docling-serve-client</artifactId>
    <version>0.1.5</version>
</dependency>
```


## Usage

Start a `docling-serve` instance (see [docling-serve docs](https://ds4sd.github.io/docling-serve/)), then build a `DoclingServeApi` client and pass it to the parser:

```java
DoclingServeApi api = DoclingServeClientBuilderFactory.newBuilder()
        .baseUrl("http://localhost:5001")
        .build();

DoclingDocumentParser parser = new DoclingDocumentParser(api);

Document document = parser.parse(inputStream);
String text = document.text();
```


## APIs

- `DoclingDocumentParser`


## Examples

- [DoclingDocumentParserTest](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-docling/src/test/java/dev/langchain4j/data/document/parser/docling/DoclingDocumentParserTest.java)
- [DoclingDocumentParserIT](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-docling/src/test/java/dev/langchain4j/data/document/parser/docling/DoclingDocumentParserIT.java)
