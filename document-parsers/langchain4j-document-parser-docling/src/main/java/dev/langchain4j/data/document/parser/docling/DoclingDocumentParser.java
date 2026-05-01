package dev.langchain4j.data.document.parser.docling;

import ai.docling.api.serve.DoclingServeApi;
import ai.docling.api.serve.convert.request.ConvertDocumentRequest;
import ai.docling.api.serve.convert.request.options.ConvertDocumentOptions;
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

public class DoclingDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DoclingDocumentParser.class);

    private final DoclingServeApi doclingClient;
    private final ConvertDocumentOptions options;

    public DoclingDocumentParser(DoclingServeApi doclingClient) {
        this(doclingClient, null);
    }

    public DoclingDocumentParser(DoclingServeApi doclingClient, ConvertDocumentOptions options) {
        if (doclingClient == null) {
            throw new IllegalArgumentException("DoclingServeApi instance cannot be null.");
        }
        this.doclingClient = doclingClient;
        this.options = options;
    }

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

            ConvertDocumentRequest.Builder requestBuilder = ConvertDocumentRequest.builder()
                    .source(FileSource.builder().base64String(base64Content).build());
            if (this.options != null) {
                requestBuilder.options(this.options);
            }
            ConvertDocumentRequest request = requestBuilder.build();

            ConvertDocumentResponse response = doclingClient.convertSource(request);

            Metadata metadata = new Metadata();
            metadata.put("document_size_bytes", String.valueOf(documentBytes.length));

            if (response == null || response.getDocument() == null) {
                log.warn("Docling returned an empty response");
                return Document.from("", metadata);
            }

            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                log.warn("Docling reported {} error(s). First: [{}] {}",
                        response.getErrors().size(),
                        response.getErrors().get(0).getComponentType(),
                        response.getErrors().get(0).getErrorMessage());
            }

            String parsedText = response.getDocument().getMarkdownContent();
            if (parsedText == null || parsedText.isEmpty()) {
                log.warn("Docling returned no text content");
                return Document.from("", metadata);
            }

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
