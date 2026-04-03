package dev.langchain4j.data.document.parser.docling;

import ai.docling.api.serve.DoclingServeApi;
import ai.docling.api.serve.convert.request.ConvertDocumentRequest;
import ai.docling.api.serve.convert.request.source.FileSource;
import ai.docling.api.serve.convert.response.ConvertDocumentResponse;
import ai.docling.client.serve.DoclingServeClientBuilderFactory;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * A document parser that integrates IBM Research's Docling parser into
 * LangChain4j.
 *
 * <p>
 * This parser uses the Docling document processing engine to extract text and
 * structure
 * from various document formats including PDF, DOCX, PPTX, and more. It
 * provides advanced
 * capabilities such as OCR, table extraction, and layout analysis.
 * </p>
 *
 * <p>
 * The parser communicates with a docling-serve instance via REST API, sending
 * documents
 * as Base64-encoded content and receiving parsed markdown output.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>{@code
 * // Default configuration (localhost:5001, 60 second timeout)
 * DoclingDocumentParser parser = new DoclingDocumentParser();
 *
 * // Custom server URL
 * DoclingDocumentParser parser = new DoclingDocumentParser("http://my-server:5001");
 *
 * // Custom server URL and timeout
 * DoclingDocumentParser parser = new DoclingDocumentParser("http://my-server:5001", 120);
 *
 * Document document = parser.parse(inputStream);
 * String text = document.text();
 * }</pre>
 *
 * @see dev.langchain4j.data.document.DocumentParser
 * @since 1.12.0
 */
public class DoclingDocumentParser implements DocumentParser {

    private final DoclingServeApi doclingClient;
    private final int timeoutSeconds;

    /**
     * Creates a new DoclingDocumentParser with the default server URL and timeout.
     *
     * <p>
     * The default URL is {@code http://localhost:5001}, which assumes a local
     * docling-serve instance is running on the default port. Default timeout is 60
     * seconds.
     * </p>
     */
    public DoclingDocumentParser() {
        this("http://localhost:5001", 60);
    }

    /**
     * Creates a new DoclingDocumentParser with a custom server URL and default
     * timeout.
     *
     * @param doclingServerUrl the URL of the docling-serve instance (e.g.,
     *                         "http://localhost:5001").
     *                         Must not be null or empty.
     * @throws IllegalArgumentException if the server URL is null or empty
     */
    public DoclingDocumentParser(String doclingServerUrl) {
        this(doclingServerUrl, 60);
    }

    /**
     * Creates a new DoclingDocumentParser with a custom server URL and timeout.
     *
     * @param doclingServerUrl the URL of the docling-serve instance (e.g.,
     *                         "http://localhost:5001").
     *                         Must not be null or empty.
     * @param timeoutSeconds   the timeout in seconds for API requests. Must be
     *                         positive.
     * @throws IllegalArgumentException if the server URL is null/empty or timeout
     *                                  is not positive
     */
    public DoclingDocumentParser(String doclingServerUrl, int timeoutSeconds) {
        if (doclingServerUrl == null || doclingServerUrl.isBlank()) {
            throw new IllegalArgumentException("You must provide a server URL!");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.timeoutSeconds = timeoutSeconds;
        this.doclingClient = DoclingServeClientBuilderFactory.newBuilder()
                .baseUrl(doclingServerUrl).build();
    }

    /**
     * Parses a document from the provided input stream using the Docling parser.
     *
     * <p>
     * The document is read into memory, encoded as Base64, and sent to the
     * docling-serve
     * instance for processing. The returned markdown content is wrapped in a
     * LangChain4j
     * {@link Document} object along with metadata about the parsing process.
     * </p>
     *
     * <p>
     * Metadata included:
     * </p>
     * <ul>
     * <li>{@code docling_processing_time_ms}: Time taken by Docling to process the
     * document</li>
     * <li>{@code document_size_bytes}: Original size of the input document</li>
     * <li>{@code docling_error_count}: Number of non-fatal errors encountered
     * during parsing</li>
     * <li>{@code parser}: Always set to "Docling"</li>
     * <li>{@code timeout_seconds}: Configured timeout value</li>
     * </ul>
     *
     * @param inputStream the input stream containing the document to parse. Must
     *                    not be null or empty.
     * @return a {@link Document} containing the parsed text and metadata
     * @throws IllegalArgumentException if the input stream is null or empty
     * @throws RuntimeException         if the document cannot be read, the Docling
     *                                  server is unreachable,
     *                                  or parsing fails
     */
    @Override
    /**
     * Returns the configured timeout in seconds.
     * 
     * @return timeout in seconds
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Document parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }

        try {
            // Step 1: Read the entire document into memory
            byte[] documentBytes = inputStream.readAllBytes();

            if (documentBytes.length == 0) {
                throw new IllegalArgumentException("Input stream is empty");
            }

            // Step 2: Encode document as Base64 for transmission to Docling
            String base64Content = Base64.getEncoder().encodeToString(documentBytes);

            // Step 3: Build the API request with the encoded document
            ConvertDocumentRequest request = ConvertDocumentRequest.builder()
                    .source(FileSource.builder().base64String(base64Content).build())
                    .build();

            // Step 4: Send request to Docling and get response
            ConvertDocumentResponse response = doclingClient.convertSource(request);

            // Step 5: Validate response
            if (response == null || response.getDocument() == null) {
                throw new RuntimeException("Docling returned an empty response");
            }

            // Step 6: Check for errors (non-fatal, we still return the document)
            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                var firstError = response.getErrors().get(0);
                System.err.println("Warning: Docling reported " + response.getErrors().size() +
                        " error(s). First: [" + firstError.getComponentType() + "] " +
                        firstError.getErrorMessage());
            }

            // Step 7: Extract the parsed text content
            String parsedText = response.getDocument().getMarkdownContent();
            if (parsedText == null || parsedText.isEmpty()) {
                throw new RuntimeException("Docling returned no text content");
            }

            // Step 8: Build metadata about the parsing process
            Metadata metadata = new Metadata();

            // Add processing time (how long Docling took to parse)
            if (response.getProcessingTime() != null) {
                metadata.put("docling_processing_time_ms", response.getProcessingTime().toString());
            }

            // Add document size (original file size in bytes)
            metadata.put("document_size_bytes", String.valueOf(documentBytes.length));

            // Add error information if there were any issues
            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                metadata.put("docling_error_count", String.valueOf(response.getErrors().size()));
            }

            // Add source type (indicates this was parsed by Docling)
            metadata.put("parser", "Docling");

            // Add timeout configuration
            metadata.put("timeout_seconds", String.valueOf(timeoutSeconds));

            // Step 9: Return everything wrapped in a Document object
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
