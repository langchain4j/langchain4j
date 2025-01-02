package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.embedding.EmbeddingRequest;
import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.TokenCountEstimator;
import dev.langchain4j.model.openai.spi.OpenAiEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_DEMO_API_KEY;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_DEMO_URL;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_URL;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.tokenUsageFrom;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_EMBEDDING_ADA_002;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * Represents an OpenAI embedding model, such as text-embedding-ada-002.
 */
public class OpenAiEmbeddingModel extends DimensionAwareEmbeddingModel implements TokenCountEstimator {

    private final OpenAiClient client;
    private final String modelName;
    private final Integer dimensions;
    private final String user;
    private final Integer maxRetries;
    private final Integer maxSegmentsPerBatch;
    private final Tokenizer tokenizer;

    public OpenAiEmbeddingModel(String baseUrl,
                                String apiKey,
                                String organizationId,
                                String modelName,
                                Integer dimensions,
                                String user,
                                Duration timeout,
                                Integer maxRetries,
                                Integer maxSegmentsPerBatch,
                                Proxy proxy,
                                Boolean logRequests,
                                Boolean logResponses,
                                Tokenizer tokenizer,
                                Map<String, String> customHeaders) {

        baseUrl = getOrDefault(baseUrl, OPENAI_URL);
        if (OPENAI_DEMO_API_KEY.equals(apiKey)) {
            baseUrl = OPENAI_DEMO_URL;
        }

        timeout = getOrDefault(timeout, ofSeconds(60));

        this.client = OpenAiClient.builder()
                .openAiApiKey(apiKey)
                .baseUrl(baseUrl)
                .organizationId(organizationId)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(customHeaders)
                .build();
        this.modelName = getOrDefault(modelName, TEXT_EMBEDDING_ADA_002);
        this.dimensions = dimensions;
        this.user = user;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.maxSegmentsPerBatch = getOrDefault(maxSegmentsPerBatch, 2048);
        ensureGreaterThanZero(this.maxSegmentsPerBatch, "maxSegmentsPerBatch");
        this.tokenizer = getOrDefault(tokenizer, OpenAiTokenizer::new);
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

        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .toList();

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
                        .reduce(TokenUsage::add)
                        .orElse(null)
        );
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(texts)
                .model(modelName)
                .dimensions(dimensions)
                .user(user)
                .build();

        EmbeddingResponse response = withRetry(() -> client.embedding(request).execute(), maxRetries);

        List<Embedding> embeddings = response.data().stream()
                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
                .toList();

        return Response.from(
                embeddings,
                tokenUsageFrom(response.usage())
        );
    }

    @Override
    public int estimateTokenCount(String text) {
        return tokenizer.estimateTokenCountInText(text);
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the model name and,
     * if necessary, other parameters.
     * <b>The default value for the model name will be removed in future releases!</b>
     */
    @Deprecated(forRemoval = true)
    public static OpenAiEmbeddingModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static OpenAiEmbeddingModelBuilder builder() {
        for (OpenAiEmbeddingModelBuilderFactory factory : loadFactories(OpenAiEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiEmbeddingModelBuilder();
    }

    public static class OpenAiEmbeddingModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String modelName;
        private Integer dimensions;
        private String user;
        private Duration timeout;
        private Integer maxRetries;
        private Integer maxSegmentsPerBatch;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Tokenizer tokenizer;
        private Map<String, String> customHeaders;

        public OpenAiEmbeddingModelBuilder() {
            // This is public so it can be extended
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

        public OpenAiEmbeddingModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
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

        public OpenAiEmbeddingModelBuilder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
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
            return new OpenAiEmbeddingModel(
                    this.baseUrl,
                    this.apiKey,
                    this.organizationId,
                    this.modelName,
                    this.dimensions,
                    this.user,
                    this.timeout,
                    this.maxRetries,
                    this.maxSegmentsPerBatch,
                    this.proxy,
                    this.logRequests,
                    this.logResponses,
                    this.tokenizer,
                    this.customHeaders
            );
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", OpenAiEmbeddingModelBuilder.class.getSimpleName() + "[", "]")
                    .add("baseUrl='" + baseUrl + "'")
                    .add("organizationId='" + organizationId + "'")
                    .add("modelName='" + modelName + "'")
                    .add("dimensions=" + dimensions)
                    .add("user='" + user + "'")
                    .add("timeout=" + timeout)
                    .add("maxRetries=" + maxRetries)
                    .add("proxy=" + proxy)
                    .add("logRequests=" + logRequests)
                    .add("logResponses=" + logResponses)
                    .add("tokenizer=" + tokenizer)
                    .add("customHeaders=" + customHeaders)
                    .toString();
        }
    }
}
