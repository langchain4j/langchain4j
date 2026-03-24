package dev.langchain4j.data.document.parser.docling;

// Import the Docling Java library classes
import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.source.BytesSource;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.client.DoclingServeClientBuilderFactory;

// Import the LangChain4j classes we need to implement
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;

// Import Java utilities
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
    
    // This is our connection to the Docling server
    private final DoclingServeApi doclingClient;
    
    /**
     * Constructor with no arguments - connects to localhost by default
     * Use this when you're running Docling on your own computer
     */
    public DoclingDocumentParser() {
        this("http://localhost:5001");  // Default server location
    }
    
    /**
     * Constructor that lets you specify where the Docling server is
     * 
     * @param doclingServerUrl - where the Docling server is running
     *                           Example: "http://localhost:5001"
     */
    public DoclingDocumentParser(String doclingServerUrl) {
        // Make sure they gave us a valid URL
        if (doclingServerUrl == null || doclingServerUrl.isEmpty()) {
            throw new IllegalArgumentException("You must provide a server URL!");
        }
        
        // Create the client that talks to the Docling server
        this.doclingClient = DoclingServeClientBuilderFactory.newBuilder()
                .baseUrl(doclingServerUrl)
                .build();
    }
    
    /**
     * This is the main method that does the work!
     * It takes a document file as input and returns the parsed text.
     * 
     * @param inputStream - the document file (as a stream of bytes)
     * @return Document - the parsed text wrapped in a Document object
     */
    @Override
    public Document parse(InputStream inputStream) {
        // Validate input
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }
        
        try {
            // Step 1: Read all the bytes from the input file
            byte[] documentBytes = inputStream.readAllBytes();
            
            // Validate we actually got some data
            if (documentBytes.length == 0) {
                throw new IllegalArgumentException("Input stream is empty - no document to parse");
            }
            
            // Step 2: Convert those bytes to base64 format
            // (This is how we send binary data over HTTP)
            String base64Content = Base64.getEncoder().encodeToString(documentBytes);
            
            // Step 3: Create a request to send to Docling
            // We're telling it "here's my document as base64 bytes"
            ConvertDocumentRequest request = ConvertDocumentRequest.builder()
                    .source(
                        BytesSource.builder()
                                .content(base64Content)
                                .build()
                    )
                    .build();
            
            // Step 4: Send the request to Docling and get the response
            ConvertDocumentResponse response = doclingClient.convertSource(request);
            
            // Step 5: Check if Docling actually returned a document
            if (response == null || response.getDocument() == null) {
                throw new RuntimeException("Docling returned an empty response - no document was parsed");
            }
            
            // Step 5a: Check for errors even if we got a document
            // (Docling can return partial results with errors)
            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                // Log the first error for debugging
                var firstError = response.getErrors().get(0);
                String errorMsg = String.format("Docling reported %d error(s). First error: [%s] %s", 
                    response.getErrors().size(),
                    firstError.getComponentType(),
                    firstError.getErrorMessage());
                
                // If there are critical errors, we might want to throw
                // For now, we'll continue with partial results
                System.err.println("Warning: " + errorMsg);
            }
            
            // Step 6: Extract the text from the response
            // Docling gives us nice markdown-formatted text
            String parsedText = response.getDocument().getMarkdownContent();
            
            // Validate we got actual text content
            if (parsedText == null || parsedText.isEmpty()) {
                throw new RuntimeException("Docling returned a document but with no text content");
            }
            
            // Step 7: Extract metadata from the Docling response
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
            
            // Step 8: Return everything wrapped in a Document object
            return new Document(parsedText, metadata);
            
        } catch (IOException e) {
            // If we can't read the file, throw an error with helpful message
            throw new RuntimeException("Failed to read input stream: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            // Re-throw validation errors as-is
            throw e;
        } catch (Exception e) {
            // If anything else goes wrong with Docling, give a helpful error
            throw new RuntimeException("Docling failed to parse the document: " + e.getMessage(), e);
        }
    }
}