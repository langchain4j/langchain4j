package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * A {@link DocumentParser} that extracts the content of scanned PDFs and images with Mistral Document AI
 * (also known as Mistral OCR), so that documents no text extractor can handle become usable in an
 * ingestion pipeline:
 *
 * <pre>{@code
 * DocumentParser parser = MistralAiOcrDocumentParser.builder()
 *         .ocrModel(MistralAiOcrModel.builder()
 *                 .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
 *                 .modelName("mistral-ocr-latest")
 *                 .build())
 *         .build();
 *
 * Document document = FileSystemDocumentLoader.loadDocument("/home/me/scan.pdf", parser);
 * }</pre>
 *
 * <p>Unlike a local parser this sends the content to Mistral, and it is billed per page.</p>
 *
 * @since 1.20.0
 */
public class MistralAiOcrDocumentParser implements DocumentParser {

    /**
     * Metadata key holding the size of the parsed input, matching what the other remote parsers report.
     */
    public static final String DOCUMENT_SIZE_BYTES = "document_size_bytes";

    private static final String DEFAULT_MIME_TYPE = "application/pdf";

    private final MistralAiOcrModel ocrModel;
    private final String mimeType;

    private MistralAiOcrDocumentParser(Builder builder) {
        this.ocrModel = ensureNotNull(builder.ocrModel, "ocrModel");
        this.mimeType = ensureNotBlank(getOrDefault(builder.mimeType, DEFAULT_MIME_TYPE), "mimeType");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Document parse(InputStream inputStream) {
        ensureNotNull(inputStream, "inputStream");

        byte[] content;
        try {
            content = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (content.length == 0) {
            throw new BlankDocumentException();
        }

        Document document = ocrModel.parse(content, mimeType);
        document.metadata().put(DOCUMENT_SIZE_BYTES, content.length);
        return document;
    }

    public static class Builder {

        private MistralAiOcrModel ocrModel;
        private String mimeType;

        /**
         * The model used to extract the content. Required.
         */
        public Builder ocrModel(MistralAiOcrModel ocrModel) {
            this.ocrModel = ocrModel;
            return this;
        }

        /**
         * The mime type the parsed streams carry, {@code application/pdf} by default.
         *
         * <p>{@link DocumentParser} receives a bare {@link InputStream} with no indication of its type,
         * so the mime type has to be stated up front. Use one parser instance per type when ingesting
         * both PDFs and images.</p>
         */
        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public MistralAiOcrDocumentParser build() {
            return new MistralAiOcrDocumentParser(this);
        }
    }
}
