package dev.langchain4j.model.ovhai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingRequest;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingResponse;
import dev.langchain4j.model.ovhai.internal.client.DefaultOvhAiClient;

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

    /**
     * Constructs an instance of an {@code OvhAiEmbeddingModel} with the specified parameters.
     *
     * @param baseUrl      The base URL of the OVHcloud API.
     * @param apiKey       The API key for authentication with the OVHcloud API.
     * @param timeout      The timeout for API requests. Default: 60 seconds
     * @param maxRetries   The maximum number of retries for API requests. Default: 2
     * @param logRequests  Whether to log the content of API requests using SLF4J. Default: false
     * @param logResponses Whether to log the content of API responses using SLF4J. Default: false
     */
    private OvhAiEmbeddingModel(
            String baseUrl,
            String apiKey,
            Duration timeout,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses) {
        this.client =
                DefaultOvhAiClient
                        .builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                        .logRequests(getOrDefault(logRequests, false))
                        .logResponses(getOrDefault(logResponses, false))
                        .build();
        this.maxRetries = getOrDefault(maxRetries, 2);
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

        public OvhAiEmbeddingModel build() {
            return new OvhAiEmbeddingModel(this.baseUrl, this.apiKey, this.timeout, this.maxRetries, this.logRequests, this.logResponses);
        }

        public String toString() {
            return "OvhAiEmbeddingModel.OvhAiEmbeddingModelBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
