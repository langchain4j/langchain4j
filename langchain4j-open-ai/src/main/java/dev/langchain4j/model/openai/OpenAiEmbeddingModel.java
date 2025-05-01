package dev.langchain4j.model.openai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import dev.langchain4j.model.openai.spi.OpenAiEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * Represents an OpenAI embedding model, such as text-embedding-ada-002.
 */
public class OpenAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Integer dimensions;
    private final String user;
    private final Integer maxRetries;
    private final Integer maxSegmentsPerBatch;

    public OpenAiEmbeddingModel(OpenAiEmbeddingModelBuilder builder) {

        this.client = OpenAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_OPENAI_URL))
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .projectId(builder.projectId)
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(15)))
                .readTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(builder.customHeaders)
                .build();
        this.modelName = builder.modelName;
        this.dimensions = builder.dimensions;
        this.user = builder.user;
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.maxSegmentsPerBatch = getOrDefault(builder.maxSegmentsPerBatch, 2048);
        ensureGreaterThanZero(this.maxSegmentsPerBatch, "maxSegmentsPerBatch");
    }

    @Override
    protected Integer knownDimension() {
        if (dimensions != null) {
            return dimensions;
        }

        return OpenAiEmbeddingModelName.knownDimension(modelName());
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream().map(TextSegment::text).toList();

        List<List<String>> textBatches = partition(texts, maxSegmentsPerBatch);

        return embedBatchedTexts(textBatches);
    }

    private List<List<String>> partition(List<String> inputList, int size) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i += size) {
            int fromIndex = i;
            int toIndex = Math.min(i + size, inputList.size());
            result.add(inputList.subList(fromIndex, toIndex));
        }
        return result;
    }

    private Response<List<Embedding>> embedBatchedTexts(List<List<String>> textBatches) {
        List<Response<List<Embedding>>> responses = new ArrayList<>();
        for (List<String> batch : textBatches) {
            Response<List<Embedding>> response = embedTexts(batch);
            responses.add(response);
        }
        return Response.from(
                responses.stream()
                        .flatMap(response -> response.content().stream())
                        .toList(),
                responses.stream()
                        .map(Response::tokenUsage)
                        .filter(Objects::nonNull)
                        .reduce(TokenUsage::add)
                        .orElse(null));
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(texts)
                .model(modelName)
                .dimensions(dimensions)
                .user(user)
                .build();

        EmbeddingResponse response = withRetryMappingExceptions(() -> client.embedding(request).execute(), maxRetries);

        List<Embedding> embeddings = response.data().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                .toList();

        return Response.from(embeddings, tokenUsageFrom(response.usage()));
    }

    public static OpenAiEmbeddingModelBuilder builder() {
        for (OpenAiEmbeddingModelBuilderFactory factory : loadFactories(OpenAiEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiEmbeddingModelBuilder();
    }

    public static class OpenAiEmbeddingModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;

        private String modelName;
        private Integer dimensions;
        private String user;
        private Duration timeout;
        private Integer maxRetries;
        private Integer maxSegmentsPerBatch;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public OpenAiEmbeddingModelBuilder() {
            // This is public so it can be extended
        }

        public OpenAiEmbeddingModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OpenAiEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiEmbeddingModelBuilder modelName(OpenAiEmbeddingModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiEmbeddingModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiEmbeddingModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public OpenAiEmbeddingModelBuilder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public OpenAiEmbeddingModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public OpenAiEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OpenAiEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OpenAiEmbeddingModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiEmbeddingModelBuilder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public OpenAiEmbeddingModel build() {
            return new OpenAiEmbeddingModel(this);
        }
    }
}
