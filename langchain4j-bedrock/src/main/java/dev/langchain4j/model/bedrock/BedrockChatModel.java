package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.AMAZON_BEDROCK;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ListenersUtil;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;

/**
 * BedrockChatModel uses the Bedrock ConverseAPI.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html">https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html</a>
 */
public class BedrockChatModel extends AbstractBedrockChatModel implements ChatLanguageModel {

    private final BedrockRuntimeClient client;

    public BedrockChatModel(String modelId) {
        this(builder().modelId(modelId));
    }

    private BedrockChatModel(Builder builder) {
        super(builder);
        if (isNull(builder.client)) {
            BedrockRuntimeClientFactory factory = ServiceLoader.load(BedrockRuntimeClientFactory.class)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No BedrockRuntimeClientFactory implementation found"));
            this.client = factory.createClient(
                    this.region,
                    builder.awsCredentialsProvider,
                    this.timeout,
                    getOrDefault(builder.logRequests, false),
                    getOrDefault(builder.logResponses, false));
        } else this.client = builder.client;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ConverseRequest convRequest = buildConverseRequest(
                request.messages(), request.parameters().toolSpecifications(), request.parameters());
        try {
            ListenersUtil.onRequest(request, this.provider(), attributes, listeners);
            ConverseResponse response = withRetryMappingExceptions(() -> client.converse(convRequest), this.maxRetries);

            final ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(aiMessageFrom(response))
                    .metadata(ChatResponseMetadata.builder()
                            .id(response.responseMetadata().requestId())
                            .finishReason(finishReasonFrom(response.stopReason()))
                            .tokenUsage(tokenUsageFrom(response.usage()))
                            .modelName(convRequest.modelId())
                            .build())
                    .build();

            ListenersUtil.onResponse(chatResponse, request, this.provider(), attributes, listeners);
            return chatResponse;

        } catch (Exception e) {
            ListenersUtil.onError(e, request, this.provider(), attributes, listeners);
            throw e;
        }
    }

    private ConverseRequest buildConverseRequest(
            List<ChatMessage> messages, List<ToolSpecification> toolSpecs, ChatRequestParameters parameters) {
        final String model =
                isNull(parameters) || isNull(parameters.modelName()) ? this.modelId : parameters.modelName();

        if (nonNull(parameters)) validate(parameters);

        return ConverseRequest.builder()
                .modelId(model)
                .inferenceConfig(inferenceConfigurationFrom(parameters))
                .system(extractSystemMessages(messages))
                .messages(extractRegularMessages(messages))
                .toolConfig(extractToolConfigurationFrom(toolSpecs, parameters))
                .additionalModelRequestFields(additionalRequestModelFieldsFrom(parameters))
                .build();
    }

    @Override
    public ModelProvider provider() {
        return AMAZON_BEDROCK;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Builder> {
        private BedrockRuntimeClient client;

        public Builder client(BedrockRuntimeClient client) {
            this.client = client;
            return this;
        }

        public BedrockChatModel build() {
            if (nonNull(this.client)
                    && (nonNull(this.region)
                            || nonNull(this.awsCredentialsProvider)
                            || nonNull(this.logRequests)
                            || nonNull(this.logResponses))) {
                throw new IllegalArgumentException(
                        "You must provide either a BedrockRuntimeClient or a combination of region, awsCredentialsProvider, logRequests, and logResponses — not both. Providing both may lead to inconsistent behavior.");
            }
            return new BedrockChatModel(this);
        }
    }
}
