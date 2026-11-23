---
sidebar_position: 2
---

# Apache Tika


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-tika</artifactId>
    <version>1.24.0-beta34</version>
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


## Documents From Untrusted Sources

The limit bounds the amount of extracted text. It does not bound the memory that Tika needs to read a document:
some small, highly compressed documents can need a lot of memory before any limit applies
(for example, a `.docx` file whose text is one very long run, or a PDF page with millions of characters).

If you parse documents from sources you do not trust, run the parsing in a separate process with a bounded heap.
Tika's `ForkParser` (included in `tika-core`) does this: it parses each document in a child JVM, so a document that needs
too much memory fails in the child JVM and does not affect your application:

```java
ForkParser forkParser = new ForkParser(ApacheTikaDocumentParser.class.getClassLoader(), new AutoDetectParser());
forkParser.setJavaCommand(List.of("java", "-Xmx512m"));

DocumentParser parser = ApacheTikaDocumentParser.builder()
        .parserSupplier(() -> forkParser)
        .build();
```

The `ForkParser` starts child JVMs and reuses them for the next documents. Close it when you no longer need it.
Running the whole application in a JVM with a bounded heap (`-Xmx`) also keeps one document from
taking all the memory of the machine.


## Examples

- [ApacheTikaDocumentParserTest](https://github.com/langchain4j/langchain4j/blob/main/document-parsers/langchain4j-document-parser-apache-tika/src/test/java/dev/langchain4j/data/document/parser/apache/tika/ApacheTikaDocumentParserTest.java)
