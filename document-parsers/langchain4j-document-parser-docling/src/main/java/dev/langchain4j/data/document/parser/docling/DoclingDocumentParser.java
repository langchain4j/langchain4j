package dev.langchain4j.data.document.parser.docling;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.source.BytesSource;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.client.DoclingServeClientBuilderFactory;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * A document parser that integrates IBM Research's Docling parser into LangChain4j.
 * 
 * <p>This parser uses the Docling document processing engine to extract text and structure
 * from various document formats including PDF, DOCX, PPTX, and more. It provides advanced
 * capabilities such as OCR, table extraction, and layout analysis.</p>
 * 
 * <p>The parser communicates with a docling-serve instance via REST API, sending documents
 * as Base64-encoded content and receiving parsed markdown output.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * DoclingDocumentParser parser = new DoclingDocumentParser("http://localhost:5001");
 * Document document = parser.parse(inputStream);
 * String text = document.text();
 * }</pre>
 * 
 * @see dev.langchain4j.data.document.DocumentParser
 * @since 1.12.0
 */
public class DoclingDocumentParser implements DocumentParser {

    private final DoclingServeApi doclingClient;

    /**
     * Creates a new DoclingDocumentParser with the default server URL.
     * 
     * <p>The default URL is {@code http://localhost:5001}, which assumes a local
     * docling-serve instance is running on the default port.</p>
     */
    public DoclingDocumentParser() {
        this("http://localhost:5001");
    }

    /**
     * Creates a new DoclingDocumentParser with a custom server URL.
     * 
     * @param doclingServerUrl the URL of the docling-serve instance (e.g., "http://localhost:5001").
     *                         Must not be null or empty.
     * @throws IllegalArgumentException if the server URL is null or empty
     */
    public DoclingDocumentParser(String doclingServerUrl) {
        if (doclingServerUrl == null || doclingServerUrl.isEmpty()) {
            throw new IllegalArgumentException("You must provide a server URL!");
        }
        this.doclingClient = DoclingServeClientBuilderFactory.newBuilder()
                .baseUrl(doclingServerUrl).build();
    }

    /**
     * Parses a document from the provided input stream using the Docling parser.
     * 
     * <p>The document is read into memory, encoded as Base64, and sent to the docling-serve
     * instance for processing. The returned markdown content is wrapped in a LangChain4j
     * {@link Document} object along with metadata about the parsing process.</p>
     * 
     * <p>Metadata included:</p>
     * <ul>
     *   <li>{@code docling_processing_time_ms}: Time taken by Docling to process the document</li>
     *   <li>{@code document_size_bytes}: Original size of the input document</li>
     *   <li>{@code docling_error_count}: Number of non-fatal errors encountered during parsing</li>
     *   <li>{@code parser}: Always set to "Docling"</li>
     * </ul>
     * 
     * @param inputStream the input stream containing the document to parse. Must not be null or empty.
     * @return a {@link Document} containing the parsed text and metadata
     * @throws IllegalArgumentException if the input stream is null or empty
     * @throws RuntimeException if the document cannot be read, the Docling server is unreachable,
     *                          or parsing fails
     */
    @Override
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
                    .source(BytesSource.builder().content(base64Content).build())
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
                // Note: Individual errors available but not included to keep metadata clean
            }

            // Add source type (indicates this was parsed by Docling)
            metadata.put("parser", "Docling");

            // Step 9: Return everything wrapped in a Document object
            return new Document(parsedText, metadata);

        } catch (IOException e) {
            // If we can't read the file, throw an error with helpful message
            throw new RuntimeException("Failed to read input stream: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            // Re-throw validation errors as-is
            throw e;
        } catch (Exception e) {
            // Catch any other errors from Docling API and wrap them
            throw new RuntimeException("Docling failed to parse document: " + e.getMessage(), e);
        }
    }
}