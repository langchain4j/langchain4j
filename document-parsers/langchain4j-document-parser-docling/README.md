# Docling Document Parser for LangChain4j

A LangChain4j document parser integration that uses IBM Research's [Docling](https://github.com/DS4SD/docling) for advanced document processing.

## Features

- **Advanced Document Processing**: Leverages Docling's OCR, table extraction, and layout analysis capabilities
- **Multiple Format Support**: PDF, DOCX, PPTX, HTML, and more
- **Metadata Extraction**: Captures processing time, document size, and error information
- **Simple Integration**: Implements LangChain4j's `DocumentParser` interface
- **Configurable Timeout**: Customize timeout settings for different workloads

## Architecture

```
Java Application → DoclingDocumentParser → docling-java client → HTTP → docling-serve → Docling Engine
```

The parser communicates with a `docling-serve` instance via REST API, sending documents as Base64-encoded content and receiving parsed markdown output.

## Prerequisites

- Java 8 or higher
- A running `docling-serve` instance (see [Installation](#docling-serve-installation))

## Installation

Add this dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-docling</artifactId>
    <version>1.12.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Basic Usage

```java
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.docling.DoclingDocumentParser;
import java.io.FileInputStream;
import java.io.InputStream;

// Use default localhost:5001
DoclingDocumentParser parser = new DoclingDocumentParser();

// Parse a document
try (InputStream inputStream = new FileInputStream("document.pdf")) {
    Document document = parser.parse(inputStream);
    String text = document.text();
    System.out.println("Parsed text: " + text);
}
```

### Custom Server URL

```java
// Connect to a remote docling-serve instance
DoclingDocumentParser parser = new DoclingDocumentParser("http://docling-server:5001");

Document document = parser.parse(inputStream);
```

### Custom Timeout

```java
// Set custom timeout (in seconds)
DoclingDocumentParser parser = new DoclingDocumentParser("http://localhost:5001", 120);

Document document = parser.parse(inputStream);
```

### Accessing Metadata

```java
Document document = parser.parse(inputStream);

// Get processing time
String processingTime = document.metadata().getString("docling_processing_time_ms");

// Get document size
String size = document.metadata().getString("document_size_bytes");

// Check timeout setting
String timeout = document.metadata().getString("timeout_seconds");

// Check if there were any errors
String errorCount = document.metadata().getString("docling_error_count");
```

### Integration with LangChain4j RAG

```java
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;

// Load and parse documents
DoclingDocumentParser parser = new DoclingDocumentParser();
List<Document> documents = FileSystemDocumentLoader.loadDocuments("/path/to/docs", parser);

// Split and embed for RAG
EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
    .documentSplitter(DocumentSplitters.recursive(300, 50))
    .embeddingModel(embeddingModel)
    .embeddingStore(embeddingStore)
    .build();

ingestor.ingest(documents);
```

## Docling-Serve Installation

### Using Python (Recommended)

```bash
# Install docling-serve
pip install docling-serve

# Start the server
docling-serve dev
```

The server will start on `http://localhost:5001` by default.

### Using Docker

```bash
# Pull and run the official image
docker run -p 5001:5001 docling/docling-serve
```

### Using Testcontainers (for Integration Tests)

```java
// Automatically starts docling-serve in a container
@Testcontainers
class DoclingIntegrationTest {
    @Container
    static DoclingServeContainer docling = new DoclingServeContainer();

    @Test
    void testParsing() {
        DoclingDocumentParser parser = new DoclingDocumentParser(docling.getUrl());
        // ...
    }
}
```

## Troubleshooting

### Connection Refused Error

**Problem**: `Failed to connect to http://localhost:5001`

**Solution**: Ensure docling-serve is running:

```bash
# Check if server is running
curl http://localhost:5001/health

# If not running, start it
docling-serve dev
```

### Empty Response from Docling

**Problem**: `Docling returned an empty response`

**Solution**:

- Check that your document is valid and not corrupted
- Verify the docling-serve instance is functioning: `curl http://localhost:5001/health`
- Check docling-serve logs for errors

### Out of Memory Errors

**Problem**: Large documents cause OOM errors

**Solution**:

- Increase JVM heap size: `-Xmx4g`
- Process documents in smaller batches
- Consider upgrading your docling-serve instance resources

### Slow Processing

**Problem**: Document parsing is taking too long

**Solution**:

- Increase timeout setting when creating parser
- Use a more powerful server for docling-serve
- Process documents in parallel with multiple parser instances
- Disable OCR if not needed (feature coming soon)

### Timeout Errors

**Problem**: Documents timing out during processing

**Solution**:

```java
// Increase timeout for large documents
DoclingDocumentParser parser = new DoclingDocumentParser("http://localhost:5001", 300);
```

## Supported Document Formats

- PDF (including scanned documents with OCR)
- Microsoft Word (DOCX, DOC)
- Microsoft PowerPoint (PPTX, PPT)
- HTML
- Markdown
- Images (PNG, JPEG, TIFF) with OCR

## Contributing

Contributions are welcome! Please see the main [LangChain4j Contributing Guide](../../CONTRIBUTING.md).

For testing information, see [TESTING.md](TESTING.md).

## License

This module is part of LangChain4j and is licensed under the Apache License 2.0.

## Related Links

- [Docling GitHub](https://github.com/DS4SD/docling)
- [Docling Documentation](https://docling.readthedocs.io/)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [GitHub Issue #4257](https://github.com/langchain4j/langchain4j/issues/4257)
