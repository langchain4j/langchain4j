package dev.langchain4j.model.ovhai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingRequest;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingResponse;
import dev.langchain4j.model.ovhai.internal.client.DefaultOvhAiClient;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.stream.Collectors.toList;

/**
 * Represents an OVHcloud embedding model. See models documentation here:
 * https://endpoints.ai.cloud.ovh.net/
 */
public class OvhAiEmbeddingModel implements EmbeddingModel {

    private final DefaultOvhAiClient client;
    private final int maxRetries;

    private OvhAiEmbeddingModel(OvhAiEmbeddingModelBuilder builder) {
        this.client =
                DefaultOvhAiClient
                        .builder()
                        .baseUrl(builder.baseUrl)
                        .apiKey(builder.apiKey)
                        .timeout(getOrDefault(builder.timeout, Duration.ofSeconds(60)))
                        .logRequests(getOrDefault(builder.logRequests, false))
                        .logResponses(getOrDefault(builder.logResponses, false))
                        .logger(builder.logger)
                        .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the baseUrl and,
     * if necessary, other parameters.
     * <b>The default value for baseUrl will be removed in future releases!</b>
     */
    @Deprecated(forRemoval = true)
    public static OvhAiEmbeddingModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static OvhAiEmbeddingModelBuilder builder() {
        return new OvhAiEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        EmbeddingRequest request = EmbeddingRequest
                .builder()
                .input(textSegments.stream().map(TextSegment::text).collect(toList()))
                .build();

        EmbeddingResponse response = withRetryMappingExceptions(() -> client.embed((request)), maxRetries);

        List<Embedding> embeddings = response.getEmbeddings()
                .stream()
                .map(Embedding::from)
                .collect(toList());

        return Response.from(embeddings);
    }

    public static class OvhAiEmbeddingModelBuilder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        OvhAiEmbeddingModelBuilder() {
        }

        public OvhAiEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OvhAiEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OvhAiEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OvhAiEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OvhAiEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OvhAiEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public OvhAiEmbeddingModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public OvhAiEmbeddingModel build() {
            return new OvhAiEmbeddingModel(this);
        }

        public String toString() {
            return "OvhAiEmbeddingModel.OvhAiEmbeddingModelBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
