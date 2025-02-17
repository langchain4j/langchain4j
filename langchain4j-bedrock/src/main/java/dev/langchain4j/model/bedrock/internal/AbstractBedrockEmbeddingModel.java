package dev.langchain4j.model.bedrock.internal;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * Abstract bedrock embedding model
 */
@SuperBuilder
@Getter
public abstract class AbstractBedrockEmbeddingModel<T extends BedrockEmbeddingResponse> implements EmbeddingModel {

    private volatile BedrockRuntimeClient client;

    @Builder.Default
    private final Region region = Region.US_EAST_1;
    @Builder.Default
    private final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder().build();
    @Builder.Default
    private final Integer maxRetries = 5;

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        final List<Map<String, Object>> requestParameters = getRequestParameters(textSegments);
        final List<T> responses = requestParameters.stream()
                .map(Json::toJson)
                .map(body -> withRetry(() -> invoke(body), maxRetries))
                .map(invokeModelResponse -> invokeModelResponse.body().asUtf8String())
                .map(response -> Json.fromJson(response, getResponseClassType()))
                .collect(Collectors.toList());

        int totalInputToken = 0;
        final List<Embedding> embeddings = new ArrayList<>();
        for (T response : responses) {
            embeddings.add(response.toEmbedding());
            totalInputToken += response.getInputTextTokenCount();
        }

        return Response.from(
                embeddings,
                new TokenUsage(totalInputToken));
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

        InvokeModelRequest invokeModelRequest = InvokeModelRequest
                .builder()
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
}
