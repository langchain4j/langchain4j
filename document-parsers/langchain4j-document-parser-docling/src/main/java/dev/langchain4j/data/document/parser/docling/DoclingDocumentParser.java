package dev.langchain4j.data.document.parser.docling;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;

import java.io.InputStream;

/**
 * Parses documents using the Docling document processing service.
 * Docling provides advanced PDF understanding including layout analysis,
 * table structure recognition, OCR, and formula extraction.
 * 
 * @see <a href="https://github.com/docling-project/docling-java">Docling
 *      Java</a>
 */
public class DoclingDocumentParser implements DocumentParser {

    private final String doclingServerUrl;

    /**
     * Creates a parser that connects to Docling server at localhost:5001
     */
    public DoclingDocumentParser() {
        this("http://localhost:5001");
    }

    /**
     * Creates a parser with a custom Docling server URL
     * 
     * @param doclingServerUrl URL of the Docling server (e.g.,
     *                         "http://localhost:5001")
     */
    public DoclingDocumentParser(String doclingServerUrl) {
        if (doclingServerUrl == null || doclingServerUrl.isEmpty()) {
            throw new IllegalArgumentException("Docling server URL cannot be null or empty");
        }
        this.doclingServerUrl = doclingServerUrl;
    }

    @Override
    public Document parse(InputStream inputStream) {
        // TODO: Implement Docling integration in next PR
        throw new UnsupportedOperationException("Docling integration not yet implemented");
    }
}