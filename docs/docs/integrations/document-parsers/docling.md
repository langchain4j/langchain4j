---
sidebar_position: 7
---

# Docling

[Docling](https://docling.ai) is an IBM Research document processing engine that extracts text and structure from various document formats including PDF, DOCX, PPTX, and more. It provides advanced capabilities such as OCR, table extraction, and layout analysis.

This integration communicates with a running [docling-serve](https://github.com/docling-project/docling-serve) instance via REST API and is built using the [official Docling Java library](https://docling-project.github.io/docling-java/current/).


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-docling</artifactId>
    <version>1.15.0-beta25</version>
</dependency>
```

This module depends on `docling-serve-api` (the interface) and includes `docling-serve-client` (the reference HTTP client) as an optional runtime dependency.

**If you are not using Spring Boot or Quarkus** (which may provide their own `DoclingServeApi` implementation), you must also add the reference client explicitly:

```xml
<dependency>
    <groupId>ai.docling</groupId>
    <artifactId>docling-serve-client</artifactId>
    <version>0.5.1</version>
</dependency>
```

Frameworks such as [Quarkus](https://quarkus.io) or [Spring Boot](https://spring.io/projects/spring-boot) provide their own integrations with Docling. See the [Docling Java Documentation](https://docling-project.github.io/docling-java/dev/docling-serve/serve-client/#when-to-use-this-module) for how to wire in those specific implementations.


## Usage

Start a `docling-serve` instance (see [docling-serve docs](https://github.com/docling-project/docling-serve)), then build a `DoclingServeApi` client and pass it to the parser:

```java
DoclingServeApi api = DoclingServeApi.builder()
        .baseUrl("http://localhost:5001")
        .build();

DoclingDocumentParser parser = new DoclingDocumentParser(api);
Document document = parser.parse(inputStream);
String text = document.text();
```

To customize Docling processing, use the constructor that also accepts a [`ConvertDocumentOptions`](https://docling-project.github.io/docling-java/dev/docling-serve/serve-api/#requests-convertdocumentrequest):

```java
ConvertDocumentOptions options = ConvertDocumentOptions.builder()
        // configure options here
        .build();

DoclingDocumentParser parser = new DoclingDocumentParser(api, options);
```

## APIs

- `DoclingDocumentParser`


## Examples

- [DoclingDocumentParserTest](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-docling/src/test/java/dev/langchain4j/data/document/parser/docling/DoclingDocumentParserTest.java)
- [DoclingDocumentParserIT](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-docling/src/test/java/dev/langchain4j/data/document/parser/docling/DoclingDocumentParserIT.java)
