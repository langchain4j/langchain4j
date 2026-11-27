---
sidebar_position: 2
---

# Apache Tika


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-tika</artifactId>
    <version>1.24.1-beta34</version>
</dependency>
```


## APIs

- `ApacheTikaDocumentParser`


## Usage

`ApacheTikaDocumentParser` detects the format of a document (PDF, DOC, DOCX, PPT, PPTX, XLS, XLSX and
[many others](https://tika.apache.org/3.0.0/formats.html)) and extracts its text:

```java
DocumentParser parser = new ApacheTikaDocumentParser();
Document document = parser.parse(inputStream);
```

To configure the parser, use its builder:

```java
DocumentParser parser = ApacheTikaDocumentParser.builder()
        .includeMetadata(true) // adds the metadata that Tika extracts (for example the content type) to the Document
        .maxCharacters(20_000_000)
        .build();
```

The builder also accepts suppliers for the Tika components (`parserSupplier`, `contentHandlerSupplier`,
`metadataSupplier` and `parseContextSupplier`), which are called once for every parsed document.


## Maximum Number of Extracted Characters

By default, the parser extracts at most 10,000,000 characters of text from a document.
The count includes the line breaks and other whitespace that Tika adds to the text, for example after each paragraph.
If a document contains more text, `parse` throws a `RuntimeException` instead of returning a `Document`.
The exception message names the limit, and its cause is Tika's `WriteLimitReachedException`.

To set a different limit, use `maxCharacters`. To extract all the text, use `-1`:

```java
DocumentParser parser = ApacheTikaDocumentParser.builder()
        .maxCharacters(-1)
        .build();
```

The limit applies to the default content handler. If you provide your own content handler with
`contentHandlerSupplier`, that handler decides how much text is collected (for example `new BodyContentHandler(limit)`),
and `maxCharacters` cannot be set at the same time.

`ApacheTikaDocumentParser` is also the parser that `FileSystemDocumentLoader` and Easy RAG use when no parser is given.
When they load a directory, documents with more text than the limit are skipped, with a warning in the log.
To change the limit there, pass the parser yourself, for example without a limit:

```java
List<Document> documents = FileSystemDocumentLoader.loadDocuments(
        "/home/langchain4j/documentation",
        ApacheTikaDocumentParser.builder().maxCharacters(-1).build());
```


## Documents From Untrusted Sources

The limit bounds the amount of extracted text. It does not bound all the memory that Tika needs to read a document:
some small, highly compressed documents can need a lot of memory before any limit applies.
An `OutOfMemoryError` in the JVM of your application can affect the whole application, even with a heap limit (`-Xmx`).
Only a separate process with its own heap limit contains it.

So if you parse documents from sources you do not trust, parse them in a separate process with its own heap limit.
With Tika 3.x, Tika's `ForkParser` can do this: pass it to the builder with `parserSupplier(...)`.
The separate process sends the extracted text back to your application, so give it a heap limit that is small
compared to the heap of your application. `ForkParser` starts a new Java process, so it needs a Java runtime
and does not work in an application compiled to a native image.

As soon as Tika parses a document in a JVM, or creates its parsers there (for example with `new AutoDetectParser()`,
also when that parser is then passed to a `ForkParser`), Tika turns off Apache POI's check of the compression ratio
of ZIP-based documents (DOCX, PPTX, XLSX) for that whole JVM. It also raises POI's limit on the number of files inside
such a document from 1,000 to 10,000 for that whole JVM. This also affects `ApachePoiDocumentParser` in the same JVM.


## Examples

- [ApacheTikaDocumentParserTest](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-apache-tika/src/test/java/dev/langchain4j/data/document/parser/apache/tika/ApacheTikaDocumentParserTest.java)
