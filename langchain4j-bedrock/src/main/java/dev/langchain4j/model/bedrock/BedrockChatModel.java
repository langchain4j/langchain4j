package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.AMAZON_BEDROCK;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.List;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;

/**
 * BedrockChatModel uses the Bedrock ConverseAPI.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html">https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html</a>
 */
public class BedrockChatModel extends AbstractBedrockChatModel implements ChatModel {

    private final BedrockRuntimeClient client;

    public BedrockChatModel(String modelId) {
        this(builder().modelId(modelId));
    }

    private BedrockChatModel(Builder builder) {
        super(builder);
        this.client = isNull(builder.client)
                ? createClient(getOrDefault(builder.logRequests, false), getOrDefault(builder.logResponses, false))
                : builder.client;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        ConverseRequest convRequest = buildConverseRequest(
                request.messages(), request.parameters().toolSpecifications(), request.parameters());
        ConverseResponse response = withRetryMappingExceptions(() -> client.converse(convRequest), this.maxRetries);

        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(response))
                .metadata(ChatResponseMetadata.builder()
                        .id(response.responseMetadata().requestId())
                        .finishReason(finishReasonFrom(response.stopReason()))
                        .tokenUsage(tokenUsageFrom(response.usage()))
                        .modelName(convRequest.modelId())
                        .build())
                .build();
    }

    @Override
    public BedrockChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
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
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return AMAZON_BEDROCK;
    }

    public static Builder builder() {
        return new Builder();
    }

    private BedrockRuntimeClient createClient(boolean logRequests, boolean logResponses) {
        return BedrockRuntimeClient.builder()
                .region(this.region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(config -> {
                    config.apiCallTimeout(this.timeout);
                    if (logRequests || logResponses)
                        config.addExecutionInterceptor(new AwsLoggingInterceptor(logRequests, logResponses));
                })
                .build();
    }

    public static class Builder extends AbstractBuilder<Builder> {
        private BedrockRuntimeClient client;

        public Builder client(BedrockRuntimeClient client) {
            this.client = client;
            return this;
        }

        public BedrockChatModel build() {
            return new BedrockChatModel(this);
        }
    }
}
