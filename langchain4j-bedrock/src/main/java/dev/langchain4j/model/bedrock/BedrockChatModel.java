package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptionsAsync;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.AMAZON_BEDROCK;
import static java.util.Objects.isNull;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.CacheTTL;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;

/**
 * BedrockChatModel uses the Bedrock ConverseAPI.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html">https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html</a>
 */
public class BedrockChatModel extends AbstractBedrockChatModel implements ChatModel {

    private final BedrockRuntimeClient client;
    private final Integer maxRetries;

    private final BedrockRuntimeAsyncClient injectedAsyncClient;
    private final boolean logRequests;
    private final boolean logResponses;
    private final Logger logger;
    private final AtomicReference<BedrockRuntimeAsyncClient> asyncClientRef = new AtomicReference<>();

    public BedrockChatModel(String modelId) {
        this(builder().modelId(modelId));
    }

    public BedrockChatModel(Builder builder) {
        super(builder);
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
        this.logger = builder.logger;
        this.client = isNull(builder.client) ? createClient(logRequests, logResponses, logger) : builder.client;
        this.injectedAsyncClient = builder.asyncClient;
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        validate(request.parameters());

        ConverseRequest converseRequest = buildConverseRequest(request);

        ConverseResponse converseResponse = withRetryMappingExceptions(
                () -> client.converse(converseRequest), maxRetries, BedrockExceptionMapper.INSTANCE);

        return toChatResponse(converseRequest, converseResponse);
    }

    @Override
    public CompletableFuture<ChatResponse> doChatAsync(ChatRequest request) {
        validate(request.parameters());

        ConverseRequest converseRequest = buildConverseRequest(request);

        CompletableFuture<ConverseResponse> future = withRetryMappingExceptionsAsync(
                () -> asyncClient().converse(converseRequest), maxRetries, BedrockExceptionMapper.INSTANCE);

        CompletableFuture<ChatResponse> result = future.thenApply(response -> toChatResponse(converseRequest, response));

        propagateCancellation(result, future);
        return result;
    }

    private ChatResponse toChatResponse(ConverseRequest converseRequest, ConverseResponse converseResponse) {
        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(converseResponse))
                .metadata(BedrockChatResponseMetadata.builder()
                        .id(converseResponse.responseMetadata().requestId())
                        .finishReason(finishReasonFrom(converseResponse.stopReason()))
                        .tokenUsage(tokenUsageFrom(converseResponse.usage()))
                        .modelName(converseRequest.modelId())
                        .guardrailAssessmentSummary(guardrailAssessmentSummaryFrom(converseResponse.trace()))
                        .build())
                .build();
    }

    @Override
    public BedrockChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    private ConverseRequest buildConverseRequest(ChatRequest chatRequest) {
        BedrockChatRequestParameters parameters = (BedrockChatRequestParameters) chatRequest.parameters();

        BedrockCachePointPlacement cachePointPlacement = parameters.cachePointPlacement();
        CacheTTL cacheTtl = parameters.cacheTtl();
        BedrockGuardrailConfiguration bedrockGuardrailConfiguration = parameters.bedrockGuardrailConfiguration();
        BedrockServiceTier bedrockServiceTier = parameters.serviceTier();

        // Validate total cache points don't exceed AWS limit
        boolean hasTools = chatRequest.toolSpecifications() != null
                && !chatRequest.toolSpecifications().isEmpty();
        validateTotalCachePoints(chatRequest.messages(), cachePointPlacement, hasTools);

        return ConverseRequest.builder()
                .modelId(chatRequest.modelName())
                .inferenceConfig(inferenceConfigFrom(chatRequest.parameters()))
                .system(extractSystemMessages(chatRequest.messages(), cachePointPlacement, cacheTtl))
                .messages(extractRegularMessages(chatRequest.messages(), cachePointPlacement, cacheTtl))
                .toolConfig(extractToolConfigurationFrom(chatRequest, cachePointPlacement, cacheTtl))
                .additionalModelRequestFields(additionalRequestModelFieldsFrom(chatRequest.parameters()))
                .guardrailConfig(guardrailConfigFrom(bedrockGuardrailConfiguration))
                .outputConfig(outputConfigFrom(chatRequest.responseFormat()))
                .serviceTier(serviceTierFor(bedrockServiceTier))
                .build();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public ModelProvider provider() {
        return AMAZON_BEDROCK;
    }

    public static Builder builder() {
        return new Builder();
    }

    private BedrockRuntimeClient createClient(boolean logRequests, boolean logResponses, Logger logger) {
        return BedrockRuntimeClient.builder()
                .region(this.region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(config -> {
                    config.apiCallTimeout(this.timeout);
                    if (logRequests || logResponses)
                        config.addExecutionInterceptor(new AwsLoggingInterceptor(logRequests, logResponses, logger));
                    if (customHeadersSupplier != null)
                        config.addExecutionInterceptor(new BedrockCustomHeadersInterceptor(customHeadersSupplier));
                })
                .build();
    }

    private BedrockRuntimeAsyncClient asyncClient() {
        BedrockRuntimeAsyncClient existing = asyncClientRef.get();
        if (existing != null) {
            return existing;
        }
        BedrockRuntimeAsyncClient created =
                injectedAsyncClient != null ? injectedAsyncClient : createAsyncClient(logRequests, logResponses, logger);
        return asyncClientRef.compareAndSet(null, created) ? created : asyncClientRef.get();
    }

    private BedrockRuntimeAsyncClient createAsyncClient(boolean logRequests, boolean logResponses, Logger logger) {
        return BedrockRuntimeAsyncClient.builder()
                .region(this.region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(config -> {
                    config.apiCallTimeout(this.timeout);
                    if (logRequests || logResponses)
                        config.addExecutionInterceptor(new AwsLoggingInterceptor(logRequests, logResponses, logger));
                    if (customHeadersSupplier != null)
                        config.addExecutionInterceptor(new BedrockCustomHeadersInterceptor(customHeadersSupplier));
                })
                .build();
    }

    public static class Builder extends AbstractBuilder<Builder> {

        private BedrockRuntimeClient client;
        private BedrockRuntimeAsyncClient asyncClient;
        private Integer maxRetries;

        public Builder client(BedrockRuntimeClient client) {
            this.client = client;
            return this;
        }

        /**
         * Sets the async Bedrock client used by {@code chatAsync}. If not set, one is created lazily on first use.
         */
        public Builder asyncClient(BedrockRuntimeAsyncClient asyncClient) {
            this.asyncClient = asyncClient;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public BedrockChatModel build() {
            return new BedrockChatModel(this);
        }
    }
}
