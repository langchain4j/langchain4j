---
sidebar_position: 4
---

# Apache PDFBox


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
    <version>1.24.0-beta34</version>
</dependency>
```


## APIs

- `ApachePdfBoxDocumentParser`


## Usage

`ApachePdfBoxDocumentParser` extracts the text of PDF files:

```java
DocumentParser parser = new ApachePdfBoxDocumentParser();
Document document = parser.parse(inputStream);
```

To configure the parser, use its builder:

```java
DocumentParser parser = ApachePdfBoxDocumentParser.builder()
        .includeMetadata(true) // adds the metadata of the PDF file (for example its author) to the Document
        .maxCharacters(20_000_000)
        .maxCharactersPerPage(200_000)
        .build();
```


## Maximum Number of Extracted Characters

By default, the parser extracts at most 10,000,000 characters from a document,
and at most 100,000 characters from a single page. Every character (glyph) drawn on a page counts.
If a document or a page contains more characters, `parse` throws a `RuntimeException` instead of returning a `Document`.
The exception message names the limit that was passed.

The limit per page exists because PDFBox keeps every character of a page in memory, with its position and font,
until the whole page is processed: a small PDF file can draw millions of characters on one page.
A page of dense text usually contains a few thousand characters.

To set different limits, use `maxCharacters` and `maxCharactersPerPage`. To remove a limit, use `-1`:

```java
DocumentParser parser = ApachePdfBoxDocumentParser.builder()
        .maxCharacters(-1)
        .maxCharactersPerPage(-1)
        .build();
```

The limits bound the amount of extracted text. They do not bound all the memory that PDFBox needs to read a document
(for example for large images or fonts). If you parse documents from sources you do not trust,
also run the application, or the parsing, in a JVM with a bounded heap (`-Xmx`).


## Examples

- [ApachePdfBoxDocumentParserTest](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-apache-pdfbox/src/test/java/dev/langchain4j/data/document/parser/apache/pdfbox/ApachePdfBoxDocumentParserTest.java)
