package dev.langchain4j.model.cohere;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

/**
 * An implementation of an {@link EmbeddingModel} that uses
 * <a href="https://docs.cohere.com/docs/embed">Cohere Embed API</a>.
 */
public class CohereEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final String DEFAULT_BASE_URL = "https://api.cohere.ai/v1/";
    private static final int DEFAULT_MAX_SEGMENTS_PER_BATCH = 96;

    private final CohereClient client;
    private final CohereClient v2Client;
    private final String modelName;
    private final String inputType;
    private final int maxSegmentsPerBatch;

    @Deprecated(forRemoval = true, since = "1.4.0")
    public CohereEmbeddingModel(
            String baseUrl,
            String apiKey,
            String modelName,
            String inputType,
            Duration timeout,
            Boolean logRequests,
            Boolean logResponses,
            Integer maxSegmentsPerBatch) {
        String resolvedBaseUrl = getOrDefault(baseUrl, DEFAULT_BASE_URL);
        this.client = CohereClient.builder()
                .baseUrl(resolvedBaseUrl)
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.v2Client = CohereClient.builder()
                .baseUrl(toV2BaseUrl(resolvedBaseUrl))
                .apiKey(ensureNotBlank(apiKey, "apiKey"))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = modelName;
        this.inputType = inputType;
        this.maxSegmentsPerBatch = getOrDefault(maxSegmentsPerBatch, DEFAULT_MAX_SEGMENTS_PER_BATCH);
    }

    public CohereEmbeddingModel(CohereEmbeddingModelBuilder builder) {
        String baseUrl = getOrDefault(builder.baseUrl, DEFAULT_BASE_URL);
        this.client = CohereClient.builder()
                .baseUrl(baseUrl)
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.v2Client = CohereClient.builder()
                .baseUrl(getOrDefault(builder.v2BaseUrl, toV2BaseUrl(baseUrl)))
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.modelName = builder.modelName;
        this.inputType = builder.inputType;
        this.maxSegmentsPerBatch = getOrDefault(builder.maxSegmentsPerBatch, DEFAULT_MAX_SEGMENTS_PER_BATCH);
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the model name and,
     * if necessary, other parameters.
     */
    @Deprecated(forRemoval = true)
    public static CohereEmbeddingModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static CohereEmbeddingModelBuilder builder() {
        return new CohereEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream().map(TextSegment::text).collect(toList());

        return embedTexts(texts);
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    @Override
    public Set<ContentType> supportedContentTypes() {
        return Set.of(ContentType.TEXT, ContentType.IMAGE);
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters() {
        return Set.of(EmbeddingRequestParameters.INPUT_TYPE);
    }

    /**
     * Embeds via Cohere's v2 embed endpoint ({@code /v2/embed}), which handles both text and images
     * (Embed v4). Each {@link EmbeddingInput} may interleave text and image parts into a single embedding.
     */
    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {

        String effectiveInputType = getOrDefault(toCohereInputType(request.inputType()), inputType);

        List<Embedding> embeddings = new ArrayList<>();
        int inputTokens = 0;

        List<EmbeddingInput> inputs = request.inputs();
        for (int i = 0; i < inputs.size(); i += maxSegmentsPerBatch) {
            List<EmbeddingInput> batch = inputs.subList(i, Math.min(i + maxSegmentsPerBatch, inputs.size()));

            EmbedV2Response response = v2Client.embedV2(buildV2Request(batch, effectiveInputType));

            if (response.getEmbeddings() != null && response.getEmbeddings().getFloatEmbeddings() != null) {
                embeddings.addAll(response.getEmbeddings().getFloatEmbeddings().stream()
                        .map(Embedding::from)
                        .collect(toList()));
            }
            inputTokens += v2TokenUsage(response);
        }

        return EmbeddingResponse.builder()
                .embeddings(embeddings)
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName(modelName)
                        .tokenUsage(new TokenUsage(inputTokens, 0))
                        .build())
                .build();
    }

    EmbedV2Request buildV2Request(List<EmbeddingInput> inputs, String resolvedInputType) {
        return EmbedV2Request.builder()
                .model(modelName)
                .inputType(resolvedInputType)
                .embeddingTypes(List.of("float"))
                .inputs(inputs.stream().map(this::toV2Input).collect(toList()))
                .build();
    }

    private EmbedV2Request.V2Input toV2Input(EmbeddingInput input) {
        return new EmbedV2Request.V2Input(
                input.contents().stream().map(this::toV2Content).collect(toList()));
    }

    private EmbedV2Request.V2Content toV2Content(Content content) {
        if (content instanceof TextContent textContent) {
            return EmbedV2Request.V2Content.text(textContent.text());
        }
        if (content instanceof ImageContent imageContent) {
            var image = imageContent.image();
            if (image.url() != null) {
                return EmbedV2Request.V2Content.imageUrl(image.url().toString());
            }
            if (image.base64Data() != null) {
                String dataUrl = "data:" + getOrDefault(image.mimeType(), "image/png") + ";base64," + image.base64Data();
                return EmbedV2Request.V2Content.imageUrl(dataUrl);
            }
            throw new UnsupportedFeatureException("ImageContent must have either a URL or base64 data");
        }
        throw new UnsupportedFeatureException("Unsupported content type: " + content.type());
    }

    static String toCohereInputType(EmbeddingInputType inputType) {
        if (inputType == null) {
            return null;
        }
        return switch (inputType) {
            case QUERY -> "search_query";
            case DOCUMENT -> "search_document";
        };
    }

    private static String toV2BaseUrl(String v1BaseUrl) {
        return v1BaseUrl.contains("/v1") ? v1BaseUrl.replace("/v1", "/v2") : v1BaseUrl;
    }

    private static int v2TokenUsage(EmbedV2Response response) {
        if (response.getMeta() != null
                && response.getMeta().getBilledUnits() != null
                && response.getMeta().getBilledUnits().getInputTokens() != null) {
            return response.getMeta().getBilledUnits().getInputTokens();
        }
        return 0;
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        List<Embedding> embeddings = new ArrayList<>();
        Integer totalTokenUsage = 0;

        for (int i = 0; i < texts.size(); i += maxSegmentsPerBatch) {

            List<String> batch = texts.subList(i, Math.min(i + maxSegmentsPerBatch, texts.size()));

            EmbedRequest request = EmbedRequest.builder()
                    .texts(batch)
                    .inputType(inputType)
                    .model(modelName)
                    .build();

            EmbedResponse response = this.client.embed(request);

            embeddings.addAll(getEmbeddings(response));
            totalTokenUsage += getTokenUsage(response);
        }

        return Response.from(embeddings, new TokenUsage(totalTokenUsage, 0));
    }

    private static List<Embedding> getEmbeddings(EmbedResponse response) {
        return stream(response.getEmbeddings()).map(Embedding::from).collect(toList());
    }

    private static Integer getTokenUsage(EmbedResponse response) {
        if (response.getMeta() != null
                && response.getMeta().getBilledUnits() != null
                && response.getMeta().getBilledUnits().getInputTokens() != null) {
            return response.getMeta().getBilledUnits().getInputTokens();
        }
        return 0;
    }

    public static class CohereEmbeddingModelBuilder {
        private String baseUrl;
        private String v2BaseUrl;
        private String apiKey;
        private String modelName;
        private String inputType;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Integer maxSegmentsPerBatch;

        CohereEmbeddingModelBuilder() {}

        public CohereEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Base URL of Cohere's v2 embed endpoint, used by {@link #embed(EmbeddingRequest)} (the multimodal
         * Embed v4 path). Defaults to {@link #baseUrl(String)} with {@code /v1} replaced by {@code /v2}.
         */
        public CohereEmbeddingModelBuilder v2BaseUrl(String v2BaseUrl) {
            this.v2BaseUrl = v2BaseUrl;
            return this;
        }

        public CohereEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public CohereEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public CohereEmbeddingModelBuilder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        public CohereEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public CohereEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public CohereEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public CohereEmbeddingModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public CohereEmbeddingModelBuilder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public CohereEmbeddingModel build() {
            return new CohereEmbeddingModel(this);
        }

        public String toString() {
            return "CohereEmbeddingModel.CohereEmbeddingModelBuilder(baseUrl=" + this.baseUrl + ", apiKey="
                    + (this.apiKey == null ? null : "********") + ", modelName=" + this.modelName + ", inputType="
                    + this.inputType + ", timeout=" + this.timeout + ", logRequests=" + this.logRequests
                    + ", logResponses=" + this.logResponses + ", maxSegmentsPerBatch=" + this.maxSegmentsPerBatch
                    + ")";
        }
    }
}
