package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.AMAZON_BEDROCK;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;

/**
 * BedrockStreamingChatModel uses the Bedrock ConverseAPI.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html">https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html</a>
 */
public class BedrockStreamingChatModel extends AbstractBedrockChatModel implements StreamingChatModel {
    private static final Logger log = LoggerFactory.getLogger(BedrockStreamingChatModel.class);
    private final BedrockRuntimeAsyncClient client;

    public BedrockStreamingChatModel(String modelId) {
        this(builder().modelId(modelId));
    }

    private BedrockStreamingChatModel(Builder builder) {
        super(builder);
        this.client = isNull(builder.client)
                ? createClient(getOrDefault(builder.logRequests, false), getOrDefault(builder.logResponses, false))
                : builder.client;
    }

    @Override
    public void doChat(final ChatRequest chatRequest, final StreamingChatResponseHandler handler) {
        final ConverseStreamRequest converseStreamRequest = buildConverseStreamRequest(
                chatRequest.messages(), chatRequest.parameters().toolSpecifications(), chatRequest.parameters());

        ConverseResponseFromStreamBuilder converseResponseBuilder = ConverseResponseFromStreamBuilder.builder();
        final ConverseStreamResponseHandler built = ConverseStreamResponseHandler.builder()
                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                        .onContentBlockStart(converseResponseBuilder::append)
                        .onContentBlockDelta(chunk -> {
                            if (chunk.delta().type().equals(ContentBlockDelta.Type.TEXT)) {
                                handler.onPartialResponse(chunk.delta().text());
                            }
                            converseResponseBuilder.append(chunk);
                        })
                        .onContentBlockStop(converseResponseBuilder::append)
                        .onMetadata(chunk -> {
                            converseResponseBuilder.append(chunk);
                            final ChatResponse completeResponse =
                                    chatResponseFrom(converseResponseBuilder.build(), converseStreamRequest.modelId());
                            handler.onCompleteResponse(completeResponse);
                        })
                        .onMessageStart(converseResponseBuilder::append)
                        .onMessageStop(converseResponseBuilder::append)
                        .build())
                .onError(handler::onError)
                .build();

        try {
            this.client.converseStream(converseStreamRequest, built).get();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Can't invoke '{}': {}", modelId, e.getCause().getMessage());
        }
    }

    @Override
    public BedrockChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    private ConverseStreamRequest buildConverseStreamRequest(
            List<ChatMessage> messages, List<ToolSpecification> toolSpecs, ChatRequestParameters parameters) {
        final String model =
                isNull(parameters) || isNull(parameters.modelName()) ? this.modelId : parameters.modelName();

        if (nonNull(parameters)) validate(parameters);

        return ConverseStreamRequest.builder()
                .modelId(model)
                .inferenceConfig(inferenceConfigurationFrom(parameters))
                .system(extractSystemMessages(messages))
                .messages(extractRegularMessages(messages))
                .toolConfig(extractToolConfigurationFrom(toolSpecs, parameters))
                .additionalModelRequestFields(additionalRequestModelFieldsFrom(parameters))
                .build();
    }

    private ChatResponse chatResponseFrom(ConverseResponse converseResponse, String modelId) {
        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(converseResponse))
                .metadata(ChatResponseMetadata.builder()
                        .id(UUID.randomUUID().toString())
                        .finishReason(finishReasonFrom(converseResponse.stopReason()))
                        .tokenUsage(tokenUsageFrom(converseResponse.usage()))
                        .modelName(modelId)
                        .build())
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

    private BedrockRuntimeAsyncClient createClient(boolean logRequests, boolean logResponses) {
        return BedrockRuntimeAsyncClient.builder()
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
        private BedrockRuntimeAsyncClient client;

        public Builder client(BedrockRuntimeAsyncClient client) {
            this.client = client;
            return this;
        }

        public BedrockStreamingChatModel build() {
            return new BedrockStreamingChatModel(this);
        }
    }
}
