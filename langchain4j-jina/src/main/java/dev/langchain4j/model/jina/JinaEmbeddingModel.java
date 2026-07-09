package dev.langchain4j.model.jina;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.jina.internal.api.JinaEmbeddingRequest;
import dev.langchain4j.model.jina.internal.api.JinaEmbeddingResponse;
import dev.langchain4j.model.jina.internal.api.JinaMultimodalEmbeddingRequest;
import dev.langchain4j.model.jina.internal.api.JinaMultimodalEmbeddingRequest.JinaMultimodalInput;
import dev.langchain4j.model.jina.internal.client.JinaClient;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of an {@link EmbeddingModel} that uses
 * <a href="https://jina.ai/embeddings">Jina Embeddings API</a>.
 */
public class JinaEmbeddingModel extends DimensionAwareEmbeddingModel {

    private static final String DEFAULT_BASE_URL = "https://api.jina.ai/";

    private final JinaClient client;
    private final String modelName;
    private final Integer maxRetries;
    private final Boolean lateChunking;
    private final List<EmbeddingModelListener> listeners;

    @Deprecated(forRemoval = true, since = "1.4.0")
    public JinaEmbeddingModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Duration timeout,
            Integer maxRetries,
            Boolean lateChunking,
            Boolean logRequests,
            Boolean logResponses) {
        this.client = JinaClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.maxRetries = getOrDefault(maxRetries, 2);
        this.lateChunking = getOrDefault(lateChunking, false);
        this.listeners = copy((List<EmbeddingModelListener>) null);
    }

    public JinaEmbeddingModel(JinaEmbeddingModelBuilder builder) {
        this.client = JinaClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(builder.apiKey)
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.lateChunking = getOrDefault(builder.lateChunking, false);
        this.listeners = copy(builder.listeners);
    }

    @Override
    public List<EmbeddingModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.JINA;
    }

    public static JinaEmbeddingModelBuilder builder() {
        return new JinaEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        JinaEmbeddingRequest request = JinaEmbeddingRequest.builder()
                .model(modelName)
                .lateChunking(lateChunking)
                .input(textSegments.stream().map(TextSegment::text).collect(toList()))
                .build();

        JinaEmbeddingResponse response = withRetryMappingExceptions(() -> client.embed(request), maxRetries);

        List<Embedding> embeddings = response.data == null
        	    ? List.of()
        	    : response.data.stream()
        	        .map(jinaEmbedding -> Embedding.from(jinaEmbedding.embedding))
        	        .collect(toList());


        TokenUsage tokenUsage = new TokenUsage(response.usage.promptTokens, 0, response.usage.totalTokens);
        return Response.from(embeddings, tokenUsage);
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    @Override
    public Set<ContentType> supportedContentTypes() {
        return isMultimodalModel(modelName)
                ? Set.of(ContentType.TEXT, ContentType.IMAGE)
                : Set.of(ContentType.TEXT);
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {

        List<Embedding> embeddings;
        JinaEmbeddingResponse response;

        if (isMultimodalModel(modelName)) {
            JinaMultimodalEmbeddingRequest wireRequest = buildMultimodalRequest(request);
            response = withRetryMappingExceptions(() -> client.embedMultimodal(wireRequest), maxRetries);
        } else {
            JinaEmbeddingRequest wireRequest = JinaEmbeddingRequest.builder()
                    .model(modelName)
                    .lateChunking(lateChunking)
                    .input(request.inputs().stream().map(EmbeddingInput::text).collect(toList()))
                    .build();
            response = withRetryMappingExceptions(() -> client.embed(wireRequest), maxRetries);
        }

        embeddings = response.data == null
                ? List.of()
                : response.data.stream()
                        .map(jinaEmbedding -> Embedding.from(jinaEmbedding.embedding))
                        .collect(toList());

        TokenUsage tokenUsage = response.usage == null
                ? null
                : new TokenUsage(response.usage.promptTokens, 0, response.usage.totalTokens);

        return EmbeddingResponse.builder()
                .embeddings(embeddings)
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName(modelName)
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }

    JinaMultimodalEmbeddingRequest buildMultimodalRequest(EmbeddingRequest request) {
        return new JinaMultimodalEmbeddingRequest(
                modelName, request.inputs().stream().map(this::toMultimodalInput).collect(toList()));
    }

    private JinaMultimodalInput toMultimodalInput(EmbeddingInput input) {
        String imageValue = null;
        boolean hasText = false;
        for (Content content : input.contents()) {
            if (content instanceof ImageContent imageContent) {
                if (imageValue != null) {
                    throw new UnsupportedFeatureException("Jina embeds one image per input");
                }
                var image = imageContent.image();
                if (image.url() != null) {
                    imageValue = image.url().toString();
                } else if (image.base64Data() != null) {
                    imageValue = "data:" + getOrDefault(image.mimeType(), "image/png") + ";base64," + image.base64Data();
                } else {
                    throw new UnsupportedFeatureException("ImageContent must have either a URL or base64 data");
                }
            } else {
                hasText = true;
            }
        }
        if (imageValue != null) {
            if (hasText) {
                // Jina embeds a single text OR a single image per input, not both fused.
                throw new UnsupportedFeatureException(
                        "Jina embeds a single text or image per input; interleaved text+image is not supported");
            }
            return JinaMultimodalInput.image(imageValue);
        }
        return JinaMultimodalInput.text(input.text());
    }

    private static boolean isMultimodalModel(String modelName) {
        return modelName != null && (modelName.contains("clip") || modelName.contains("embeddings-v4"));
    }

    public static class JinaEmbeddingModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean lateChunking;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private List<EmbeddingModelListener> listeners;

        JinaEmbeddingModelBuilder() {
        }

        public JinaEmbeddingModelBuilder listeners(List<EmbeddingModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public JinaEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public JinaEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public JinaEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public JinaEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public JinaEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public JinaEmbeddingModelBuilder lateChunking(Boolean lateChunking) {
            this.lateChunking = lateChunking;
            return this;
        }

        public JinaEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public JinaEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public JinaEmbeddingModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public JinaEmbeddingModel build() {
            return new JinaEmbeddingModel(this);
        }

        public String toString() {
            return "JinaEmbeddingModel.JinaEmbeddingModelBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + (this.apiKey == null ? null : "********") + ", modelName=" + this.modelName + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries + ", lateChunking=" + this.lateChunking + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
