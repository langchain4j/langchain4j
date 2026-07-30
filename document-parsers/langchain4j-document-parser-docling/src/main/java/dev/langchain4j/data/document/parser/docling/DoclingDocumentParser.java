package dev.langchain4j.data.document.parser.docling;

import static dev.langchain4j.internal.Utils.getOrDefault;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
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
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoclingDocumentParser implements DocumentParser {
    private static final Logger log = LoggerFactory.getLogger(DoclingDocumentParser.class);

    private static final Function<InBodyConvertDocumentResponse, String> DEFAULT_DOCUMENT_TEXT_EXTRACTOR =
            response -> response.getDocument().getMarkdownContent();

    private final DoclingServeApi doclingClient;
    private final ConvertDocumentOptions options;
    private final Function<InBodyConvertDocumentResponse, String> documentTextExtractor;

    @Deprecated(forRemoval = true)
    public DoclingDocumentParser(DoclingServeApi doclingClient) {
        this(doclingClient, null);
    }

    @Deprecated(forRemoval = true)
    public DoclingDocumentParser(DoclingServeApi doclingClient, ConvertDocumentOptions options) {
        this(builder().doclingClient(doclingClient).options(options));
    }

    private DoclingDocumentParser(Builder builder) {
        this.doclingClient = ValidationUtils.ensureNotNull(builder.doclingClient, "doclingClient");
        this.options = builder.options;
        this.documentTextExtractor = getOrDefault(builder.documentTextExtractor, DEFAULT_DOCUMENT_TEXT_EXTRACTOR);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Document parse(InputStream inputStream) {
        ValidationUtils.ensureNotNull(inputStream, "inputStream");

        try {
            byte[] documentBytes = inputStream.readAllBytes();

            var metadata = new Metadata();
            metadata.put("document_size_bytes", String.valueOf(documentBytes.length));

            if (documentBytes.length == 0) {
                throw new BlankDocumentException();
            }

            var base64Content = Base64.getEncoder().encodeToString(documentBytes);

            var requestBuilder = ConvertDocumentRequest.builder()
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

            var inBodyResponse = (InBodyConvertDocumentResponse) response;

            if (!inBodyResponse.getErrors().isEmpty()) {
                var first = inBodyResponse.getErrors().get(0);
                log.warn(
                        "Docling reported {} error(s). First: [{}] {}",
                        inBodyResponse.getErrors().size(),
                        first.getComponentType(),
                        first.getErrorMessage());
            }

            var parsedText = documentTextExtractor.apply(inBodyResponse);
            if ((parsedText == null) || parsedText.isBlank()) {
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

    public static final class Builder {
        private DoclingServeApi doclingClient;
        private ConvertDocumentOptions options;
        private Function<InBodyConvertDocumentResponse, String> documentTextExtractor;

        private Builder() {}

        public Builder doclingClient(DoclingServeApi doclingClient) {
            this.doclingClient = doclingClient;
            return this;
        }

        public Builder options(ConvertDocumentOptions options) {
            this.options = options;
            return this;
        }

        /**
         * Sets a custom function to extract text content from the Docling conversion response.
         * <p>
         * The function receives the full {@link InBodyConvertDocumentResponse}, giving access to
         * the converted document (in various formats: markdown, HTML, text, doctags, JSON),
         * conversion errors, processing time, and status information.
         * <p>
         * If not set, defaults to extracting markdown content:
         * {@code response -> response.getDocument().getMarkdownContent()}.
         *
         * @param documentTextExtractor the function to extract document text from the response
         * @return this builder
         */
        public Builder documentTextExtractor(Function<InBodyConvertDocumentResponse, String> documentTextExtractor) {
            this.documentTextExtractor = documentTextExtractor;
            return this;
        }

        public DoclingDocumentParser build() {
            return new DoclingDocumentParser(this);
        }
    }
}
