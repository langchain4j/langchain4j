package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;

import dev.langchain4j.Internal;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Internal
abstract class AbstractBedrockEmbeddingModel<T extends BedrockEmbeddingResponse> extends DimensionAwareEmbeddingModel {

    private static final Region DEFAULT_REGION = Region.US_EAST_1;
    private static final AwsCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER =
            DefaultCredentialsProvider.builder().build();
    private static final Integer DEFAULT_MAX_RETRIES = 2;

    private volatile BedrockRuntimeClient client;

    private final Region region;
    private final AwsCredentialsProvider credentialsProvider;
    private final Integer maxRetries;

    protected AbstractBedrockEmbeddingModel(AbstractBedrockEmbeddingModelBuilder<T, ?, ?> builder) {
        this.client = builder.client;

        if (builder.isRegionSet) {
            this.region = builder.region;
        } else {
            this.region = DEFAULT_REGION;
        }

        if (builder.isCredentialsProviderSet) {
            this.credentialsProvider = builder.credentialsProvider;
        } else {
            this.credentialsProvider = DEFAULT_CREDENTIALS_PROVIDER;
        }

        if (builder.isMaxRetriesSet) {
            this.maxRetries = builder.maxRetries;
        } else {
            this.maxRetries = DEFAULT_MAX_RETRIES;
        }
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        final List<Map<String, Object>> requestParameters = getRequestParameters(textSegments);
        final List<T> responses = requestParameters.stream()
                .map(Json::toJson)
                .map(body -> withRetryMappingExceptions(() -> invoke(body), maxRetries, BedrockExceptionMapper.INSTANCE))
                .map(invokeModelResponse -> invokeModelResponse.body().asUtf8String())
                .map(response -> Json.fromJson(response, getResponseClassType()))
                .collect(Collectors.toList());

        int totalInputToken = 0;
        final List<Embedding> embeddings = new ArrayList<>();
        for (T response : responses) {
            embeddings.add(response.toEmbedding());
            totalInputToken += response.getInputTextTokenCount();
        }

        return Response.from(embeddings, new TokenUsage(totalInputToken));
    }

    /**
     * Get request body
     *
     * @param textSegments Input texts to convert to embedding
     * @return request body
     */
    protected abstract List<Map<String, Object>> getRequestParameters(final List<TextSegment> textSegments);

    public BedrockRuntimeClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = initClient();
                }
            }
        }
        return client;
    }

    /**
     * Get model id
     *
     * @return model id
     */
    protected abstract String getModelId();

    /**
     * Get response class type
     *
     * @return response class type
     */
    protected abstract Class<T> getResponseClassType();

    /**
     * Invoke model
     *
     * @param body body
     * @return invoke model response
     */
    protected InvokeModelResponse invoke(final String body) {

        InvokeModelRequest invokeModelRequest = InvokeModelRequest.builder()
                .modelId(getModelId())
                .body(SdkBytes.fromString(body, Charset.defaultCharset()))
                .build();
        return getClient().invokeModel(invokeModelRequest);
    }

    /**
     * Create map with single entry
     *
     * @param key   key
     * @param value value
     * @return map
     */
    protected static Map<String, Object> of(final String key, final Object value) {
        final Map<String, Object> map = new HashMap<>(1);
        map.put(key, value);

        return map;
    }

    /**
     * Initialize bedrock client
     *
     * @return bedrock client
     */
    private BedrockRuntimeClient initClient() {
        return BedrockRuntimeClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    public Region getRegion() {
        return region;
    }

    public AwsCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public abstract static class AbstractBedrockEmbeddingModelBuilder<
            T extends BedrockEmbeddingResponse,
            C extends AbstractBedrockEmbeddingModel<T>,
            B extends AbstractBedrockEmbeddingModelBuilder<T, C, B>> {
        private BedrockRuntimeClient client;
        private Region region;
        private boolean isRegionSet;
        private AwsCredentialsProvider credentialsProvider;
        private boolean isCredentialsProviderSet;
        private Integer maxRetries;
        private boolean isMaxRetriesSet;

        public B client(BedrockRuntimeClient client) {
            this.client = client;
            return self();
        }

        public B region(Region region) {
            this.region = region;
            this.isRegionSet = true;
            return self();
        }

        public B credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            this.isCredentialsProviderSet = true;
            return self();
        }

        public B maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            this.isMaxRetriesSet = true;
            return self();
        }

        protected abstract B self();

        public abstract C build();
    }
}
