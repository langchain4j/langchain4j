package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.mistralai.internal.api.MistralAiOcrDocument;
import dev.langchain4j.model.mistralai.internal.api.MistralAiOcrPage;
import dev.langchain4j.model.mistralai.internal.api.MistralAiOcrRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiOcrResponse;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;

/**
 * Extracts the content of a PDF or an image as markdown, using Mistral Document AI (also known as
 * Mistral OCR).
 *
 * <p>The result is returned as a {@link Document} so that it feeds directly into an ingestion pipeline.
 * Use {@link #parsePages(byte[], String)} to keep the page structure, which lets a retrieved segment be
 * traced back to a page of the source document.</p>
 *
 * <p>Mistral Document AI is also offered through Azure, where the deployment lives on its own host,
 * accepts the key in an {@code api-key} header and can take an {@code api-version} query parameter. All
 * three are ordinary builder settings, so no Azure specific model is needed:</p>
 *
 * <pre>{@code
 * MistralAiOcrModel model = MistralAiOcrModel.builder()
 *         .baseUrl("https://my-resource.services.ai.azure.com/providers/mistral/azure")
 *         .apiKey(key)
 *         .customHeaders(Map.of("api-key", key))
 *         .customQueryParams(Map.of("api-version", "2024-05-01-preview"))
 *         .modelName("mistral-document-ai-2512")
 *         .build();
 * }</pre>
 *
 * @see MistralAiOcrDocumentParser
 * @since 1.20.0
 */
public class MistralAiOcrModel {

    /**
     * Metadata key holding the model that produced the result.
     */
    public static final String OCR_MODEL = "ocr_model";

    /**
     * Metadata key holding how many pages the service billed.
     */
    public static final String PAGES_PROCESSED = "pages_processed";

    /**
     * Metadata key holding the number of pages the result consists of.
     */
    public static final String PAGE_COUNT = "page_count";

    /**
     * Metadata key holding the zero-based index of a page in the source document. Only set by
     * {@link #parsePages(byte[], String)}.
     */
    public static final String PAGE_INDEX = "page_index";

    /**
     * Metadata key holding the header of a page, set by {@link #parsePages(byte[], String)} when header
     * extraction is enabled and the service recognized one.
     */
    public static final String PAGE_HEADER = "page_header";

    /**
     * Metadata key holding the footer of a page, set by {@link #parsePages(byte[], String)} when footer
     * extraction is enabled and the service recognized one.
     */
    public static final String PAGE_FOOTER = "page_footer";

    private static final String PAGE_SEPARATOR = "\n\n";

    private final MistralAiClient client;
    private final String modelName;
    private final Integer maxRetries;
    private final List<Integer> pages;
    private final Boolean extractHeader;
    private final Boolean extractFooter;
    private final MistralAiOcrTableFormat tableFormat;

    public MistralAiOcrModel(Builder builder) {
        this.client = MistralAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(builder.apiKey)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .customHeaders(builder.customHeadersSupplier)
                .customQueryParams(builder.customQueryParamsSupplier)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.pages = copy(builder.pages);
        this.extractHeader = builder.extractHeader;
        this.extractFooter = builder.extractFooter;
        this.tableFormat = builder.tableFormat;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ModelProvider provider() {
        return ModelProvider.MISTRAL_AI;
    }

    public String modelName() {
        return modelName;
    }

    /**
     * Extracts the content of the given bytes as a single {@link Document}.
     *
     * @param content the document or image to process
     * @param mimeType the mime type of the content, e.g. {@code application/pdf} or {@code image/png}.
     *        A mime type starting with {@code image/} is sent as an image, everything else as a document.
     * @throws BlankDocumentException when the service did not recognize any content
     */
    public Document parse(byte[] content, String mimeType) {
        return toDocument(ocr(toDataUriDocument(content, mimeType)));
    }

    /**
     * Extracts the content of a document the service downloads itself.
     *
     * @throws BlankDocumentException when the service did not recognize any content
     */
    public Document parseDocumentUrl(String documentUrl) {
        return toDocument(ocr(MistralAiOcrDocument.documentUrl(ensureNotBlank(documentUrl, "documentUrl"))));
    }

    /**
     * Extracts the content of an image the service downloads itself.
     *
     * @throws BlankDocumentException when the service did not recognize any content
     */
    public Document parseImageUrl(String imageUrl) {
        return toDocument(ocr(MistralAiOcrDocument.imageUrl(ensureNotBlank(imageUrl, "imageUrl"))));
    }

    /**
     * Extracts the content of the given bytes as one {@link Document} per page, each carrying its
     * {@link #PAGE_INDEX}. Pages without recognized content are skipped.
     *
     * @throws BlankDocumentException when no page contained any content
     */
    public List<Document> parsePages(byte[] content, String mimeType) {
        MistralAiOcrResponse response = ocr(toDataUriDocument(content, mimeType));

        List<Document> documents = new ArrayList<>();
        for (MistralAiOcrPage page : pagesOf(response)) {
            String text = textOf(page);
            if (text.isBlank()) {
                continue;
            }
            Metadata metadata = commonMetadata(response);
            if (page.index != null) {
                metadata.put(PAGE_INDEX, page.index);
            }
            if (!isNullOrBlank(page.header)) {
                metadata.put(PAGE_HEADER, page.header);
            }
            if (!isNullOrBlank(page.footer)) {
                metadata.put(PAGE_FOOTER, page.footer);
            }
            documents.add(Document.from(text, metadata));
        }

        if (documents.isEmpty()) {
            throw new BlankDocumentException();
        }
        return documents;
    }

    private MistralAiOcrResponse ocr(MistralAiOcrDocument document) {
        MistralAiOcrRequest request = new MistralAiOcrRequest();
        request.model = modelName;
        request.document = document;
        request.pages = pages.isEmpty() ? null : pages;
        request.extractHeader = extractHeader;
        request.extractFooter = extractFooter;
        request.tableFormat = tableFormat == null ? null : tableFormat.toString();

        return withRetryMappingExceptions(() -> client.ocr(request), maxRetries);
    }

    private Document toDocument(MistralAiOcrResponse response) {
        String markdown = pagesOf(response).stream()
                .map(MistralAiOcrModel::textOf)
                .filter(text -> !text.isBlank())
                .collect(joining(PAGE_SEPARATOR));

        if (markdown.isBlank()) {
            throw new BlankDocumentException();
        }

        return Document.from(markdown, commonMetadata(response));
    }

    /**
     * The full text of a page.
     *
     * <p>Enabling header or footer extraction moves that text out of {@code markdown} into its own field,
     * so both have to be folded back in. Otherwise turning the option on would silently drop content.</p>
     */
    private static String textOf(MistralAiOcrPage page) {
        return Stream.of(page.header, page.markdown, page.footer)
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .collect(joining(PAGE_SEPARATOR));
    }

    private Metadata commonMetadata(MistralAiOcrResponse response) {
        Metadata metadata = new Metadata();
        if (response.model != null) {
            metadata.put(OCR_MODEL, response.model);
        }
        if (response.usageInfo != null && response.usageInfo.pagesProcessed != null) {
            metadata.put(PAGES_PROCESSED, response.usageInfo.pagesProcessed);
        }
        metadata.put(PAGE_COUNT, pagesOf(response).size());
        return metadata;
    }

    private static List<MistralAiOcrPage> pagesOf(MistralAiOcrResponse response) {
        return response.pages == null ? List.of() : response.pages;
    }

    private static MistralAiOcrDocument toDataUriDocument(byte[] content, String mimeType) {
        ensureNotNull(content, "content");
        ensureNotBlank(mimeType, "mimeType");

        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(content);
        return mimeType.startsWith("image/")
                ? MistralAiOcrDocument.imageUrl(dataUri)
                : MistralAiOcrDocument.documentUrl(dataUri);
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private Supplier<Map<String, String>> customQueryParamsSupplier;
        private List<Integer> pages;
        private Boolean extractHeader;
        private Boolean extractFooter;
        private MistralAiOcrTableFormat tableFormat;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * The model to use, e.g. {@code mistral-ocr-latest}. On Azure this is the name of the deployment.
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        public Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Query parameters added to every request, needed for the {@code api-version} of Azure hosted
         * deployments.
         */
        public Builder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParamsSupplier = () -> customQueryParams;
            return this;
        }

        public Builder customQueryParams(Supplier<Map<String, String>> customQueryParamsSupplier) {
            this.customQueryParamsSupplier = customQueryParamsSupplier;
            return this;
        }

        /**
         * The zero-based indices of the pages to process. When unset, the whole document is processed.
         */
        public Builder pages(List<Integer> pages) {
            this.pages = pages;
            return this;
        }

        /**
         * Whether the header of a page is reported separately from its body.
         *
         * <p>By default the header is part of the recognized content. Enabling this moves it into its own
         * field, which {@link #parsePages(byte[], String)} exposes as {@link #PAGE_HEADER} metadata. The
         * text of the returned documents is unaffected either way, so a repeated header can be stripped
         * from the body without having to guess which line it was.</p>
         */
        public Builder extractHeader(Boolean extractHeader) {
            this.extractHeader = extractHeader;
            return this;
        }

        /**
         * Whether the footer of a page is reported separately from its body, exposed as
         * {@link #PAGE_FOOTER} metadata. See {@link #extractHeader(Boolean)}.
         */
        public Builder extractFooter(Boolean extractFooter) {
            this.extractFooter = extractFooter;
            return this;
        }

        /**
         * How tables are rendered in the returned markdown.
         */
        public Builder tableFormat(MistralAiOcrTableFormat tableFormat) {
            this.tableFormat = tableFormat;
            return this;
        }

        public MistralAiOcrModel build() {
            return new MistralAiOcrModel(this);
        }
    }
}
