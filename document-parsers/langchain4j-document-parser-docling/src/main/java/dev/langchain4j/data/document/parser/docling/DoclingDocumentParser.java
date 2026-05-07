package dev.langchain4j.data.document.parser.docling;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.ErrorItem;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import ai.docling.serve.api.convert.response.ResponseType;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.internal.ValidationUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoclingDocumentParser implements DocumentParser {
    private static final Logger log = LoggerFactory.getLogger(DoclingDocumentParser.class);

    private final DoclingServeApi doclingClient;
    private final ConvertDocumentOptions options;

    public DoclingDocumentParser(DoclingServeApi doclingClient) {
        this(doclingClient, null);
    }

    public DoclingDocumentParser(DoclingServeApi doclingClient, ConvertDocumentOptions options) {
        this.doclingClient = ValidationUtils.ensureNotNull(doclingClient, "doclingClient");
        this.options = options;
    }

    @Override
    public Document parse(InputStream inputStream) {
        ValidationUtils.ensureNotNull(inputStream, "inputStream");

        try {
            byte[] documentBytes = inputStream.readAllBytes();

            Metadata metadata = new Metadata();
            metadata.put("document_size_bytes", String.valueOf(documentBytes.length));

            if (documentBytes.length == 0) {
                throw new BlankDocumentException();
            }

            String base64Content = Base64.getEncoder().encodeToString(documentBytes);

            ConvertDocumentRequest.Builder requestBuilder = ConvertDocumentRequest.builder()
                    .source(FileSource.builder()
                            .base64String(base64Content)
                            .filename("document")
                            .build());

            if (this.options != null) {
                requestBuilder.options(this.options);
            }

            ConvertDocumentResponse response = doclingClient.convertSource(requestBuilder.build());

            if (response.getResponseType() != ResponseType.IN_BODY) {
                throw new IllegalStateException(
                        "Only %s response types expected. Docling returned unexpected response type: %s"
                                .formatted(ResponseType.IN_BODY, response.getResponseType()));
            }

            InBodyConvertDocumentResponse inBodyResponse = (InBodyConvertDocumentResponse) response;

            if (!inBodyResponse.getErrors().isEmpty()) {
                ErrorItem first = inBodyResponse.getErrors().get(0);
                log.warn(
                        "Docling reported {} error(s). First: [{}] {}",
                        inBodyResponse.getErrors().size(),
                        first.getComponentType(),
                        first.getErrorMessage());
            }

            String parsedText = inBodyResponse.getDocument().getMarkdownContent();
            if ((parsedText == null) || parsedText.strip().isEmpty()) {
                throw new BlankDocumentException();
            }

            return Document.from(parsedText, metadata);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream: " + e.getMessage(), e);
        } catch (BlankDocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Docling failed to parse document: " + e.getMessage(), e);
        }
    }
}
