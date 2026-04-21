package dev.langchain4j.data.document.parser.docling;

import ai.docling.api.serve.DoclingServeApi;
import ai.docling.api.serve.convert.request.ConvertDocumentRequest;
import ai.docling.api.serve.convert.request.source.FileSource;
import ai.docling.api.serve.convert.response.ConvertDocumentResponse;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * A document parser that integrates IBM Research's Docling parser into LangChain4j.
 *
 * <p>This parser uses the Docling document processing engine to extract text and structure
 * from various document formats including PDF, DOCX, PPTX, and more.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * DoclingServeApi api = DoclingServeClientBuilderFactory.newBuilder()
 *         .baseUrl("http://localhost:5001")
 *         .build();
 * DoclingDocumentParser parser = new DoclingDocumentParser(api);
 *
 * Document document = parser.parse(inputStream);
 * }</pre>
 *
 * @see dev.langchain4j.data.document.DocumentParser
 */
public class DoclingDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DoclingDocumentParser.class);

    private final DoclingServeApi doclingClient;

    /**
     * Creates a new DoclingDocumentParser with a pre-built {@link DoclingServeApi} instance.
     *
     * @param doclingClient a configured {@link DoclingServeApi} instance. Must not be null.
     * @throws IllegalArgumentException if doclingClient is null
     */
    public DoclingDocumentParser(DoclingServeApi doclingClient) {
        if (doclingClient == null) {
            throw new IllegalArgumentException("DoclingServeApi instance cannot be null.");
        }
        this.doclingClient = doclingClient;
    }

    /**
     * Parses a document from the provided input stream using the Docling parser.
     *
     * <p>The document is read into memory, encoded as Base64, and sent to the
     * docling-serve instance for processing. The returned markdown content is wrapped
     * in a LangChain4j {@link Document} with the document size in metadata.</p>
     *
     * @param inputStream the input stream containing the document to parse. Must not be null or empty.
     * @return a {@link Document} containing the parsed text and metadata
     * @throws IllegalArgumentException if the input stream is null or empty
     * @throws RuntimeException         if the document cannot be read, the Docling server is unreachable,
     *                                  or parsing fails
     */
    public Document parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }

        try {
            byte[] documentBytes = inputStream.readAllBytes();

            if (documentBytes.length == 0) {
                throw new IllegalArgumentException("Input stream is empty. Please provide a document with content.");
            }

            String base64Content = Base64.getEncoder().encodeToString(documentBytes);

            ConvertDocumentRequest request = ConvertDocumentRequest.builder()
                    .source(FileSource.builder().base64String(base64Content).build())
                    .build();

            ConvertDocumentResponse response = doclingClient.convertSource(request);

            if (response == null || response.getDocument() == null) {
                throw new RuntimeException("Docling returned an empty response");
            }

            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                log.warn("Docling reported {} error(s). First: [{}] {}",
                        response.getErrors().size(),
                        response.getErrors().get(0).getComponentType(),
                        response.getErrors().get(0).getErrorMessage());
            }

            String parsedText = response.getDocument().getMarkdownContent();
            if (parsedText == null || parsedText.isEmpty()) {
                throw new RuntimeException("Docling returned no text content");
            }

            Metadata metadata = new Metadata();
            metadata.put("document_size_bytes", String.valueOf(documentBytes.length));

            return Document.from(parsedText, metadata);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Docling failed to parse document: " + e.getMessage(), e);
        }
    }
}
