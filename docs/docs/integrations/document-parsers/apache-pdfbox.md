---
sidebar_position: 4
---

# Apache PDFBox


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
    <version>1.10.0-beta18</version>
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

By default, the parser extracts at most 10,000,000 characters of text from a document,
and reads at most 100,000 characters from a single page:

- The limit per document counts the characters of the returned text,
  including the spaces and line breaks that PDFBox adds between words, lines and pages.
  A single page from which PDFBox reads more characters than this limit also exceeds it.
- The limit per page counts the characters of the page's text as PDFBox reads it, before it adds spaces and line breaks.
  Every character drawn on the page counts, also when it is drawn more than once at the same position.

If a document or a page contains more characters, `parse` throws a `RuntimeException` instead of returning a `Document`.
The exception message names the limit that was passed.

The limit per page exists because PDFBox keeps every character of a page in memory, with its position and font,
until the whole page is processed.
A page of dense text usually contains a few thousand characters.

To set different limits, use `maxCharacters` and `maxCharactersPerPage`. To remove a limit, use `-1`:

```java
DocumentParser parser = ApachePdfBoxDocumentParser.builder()
        .maxCharacters(-1)
        .maxCharactersPerPage(-1)
        .build();
```

The limits bound the amount of extracted text. They do not bound all the memory that PDFBox needs to read a document:
some small, highly compressed PDF files can need a lot of memory before these limits apply.
An `OutOfMemoryError` in the JVM of your application can affect the whole application, even with a heap limit (`-Xmx`).
So if you parse documents from sources you do not trust, parse them in a separate process with its own heap limit.


## Examples

- [ApachePdfBoxDocumentParserTest](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-apache-pdfbox/src/test/java/dev/langchain4j/data/document/parser/apache/pdfbox/ApachePdfBoxDocumentParserTest.java)
