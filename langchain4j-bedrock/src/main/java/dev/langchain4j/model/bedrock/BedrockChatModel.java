package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ListenersUtil;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
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
        this.client = isNull(builder.client)
                ? createClient(
                        getOrDefault(builder.getLogRequests(), false), getOrDefault(builder.getLogResponses(), false))
                : builder.client;
    }

    @Override
    public Response<AiMessage> generate(final List<ChatMessage> messages) {
        return generate(messages, emptyList());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, List.of(toolSpecification));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();

        final ChatResponse chatResponse = this.chat(chatRequest);
        return Response.from(
                chatResponse.aiMessage(),
                chatResponse.tokenUsage(),
                chatResponse.finishReason(),
                Map.of("id", chatResponse.metadata().id()));
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ConverseRequest convRequest = buildConverseRequest(
                request.messages(), request.parameters().toolSpecifications(), request.parameters());
        try {
            ListenersUtil.onRequest(request, attributes, listeners);
            ConverseResponse response = withRetry(() -> client.converse(convRequest), this.maxRetries);

            final ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(aiMessageFrom(response))
                    .metadata(ChatResponseMetadata.builder()
                            .id(response.responseMetadata().requestId())
                            .finishReason(finishReasonFrom(response.stopReason()))
                            .tokenUsage(tokenUsageFrom(response.usage()))
                            .modelName(convRequest.modelId())
                            .build())
                    .build();

            ListenersUtil.onResponse(chatResponse, request, attributes, listeners);
            return chatResponse;

        } catch (Exception e) {
            ListenersUtil.onError(e, request, attributes, listeners);
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
                .build();
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
