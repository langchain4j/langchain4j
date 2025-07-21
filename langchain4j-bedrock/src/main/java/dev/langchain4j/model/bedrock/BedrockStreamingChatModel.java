package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.model.ModelProvider.AMAZON_BEDROCK;
import static java.util.Objects.isNull;

import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
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

    public BedrockStreamingChatModel(Builder builder) {
        super(builder);
        this.client = isNull(builder.client)
                ? createClient(getOrDefault(builder.logRequests, false), getOrDefault(builder.logResponses, false))
                : builder.client;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        validate(chatRequest.parameters());

        ConverseStreamRequest converseStreamRequest = buildConverseStreamRequest(chatRequest);

        ConverseResponseFromStreamBuilder responseBuilder = ConverseResponseFromStreamBuilder.builder();
        ToolCallBuilder toolCallBuilder = new ToolCallBuilder(-1);
        AtomicReference<ContentBlockDelta.Type> currentContentType = new AtomicReference<>();

        ConverseStreamResponseHandler converseStreamResponseHandler = ConverseStreamResponseHandler.builder()
                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                        .onContentBlockStart(event -> {
                            if (event.start().type() == ContentBlockStart.Type.TOOL_USE) {
                                toolCallBuilder.updateIndex(toolCallBuilder.index() + 1);
                                toolCallBuilder.updateId(event.start().toolUse().toolUseId());
                                toolCallBuilder.updateName(event.start().toolUse().name());
                            }
                            responseBuilder.append(event);
                        })
                        .onContentBlockDelta(chunk -> {
                            currentContentType.set(chunk.delta().type());
                            if (currentContentType.get() == ContentBlockDelta.Type.TEXT) {
                                try {
                                    handler.onPartialResponse(chunk.delta().text());
                                } catch (Exception e) {
                                    withLoggingExceptions(() -> handler.onError(e));
                                }
                            } else if (currentContentType.get() == ContentBlockDelta.Type.TOOL_USE) {
                                String input = chunk.delta().toolUse().input();
                                if (isNotNullOrEmpty(input)) {
                                    toolCallBuilder.appendArguments(input);
                                }
                            }
                            responseBuilder.append(chunk);
                        })
                        .onContentBlockStop(event -> {
                            if (currentContentType.get() == ContentBlockDelta.Type.TOOL_USE) {
                                onCompleteToolCall(handler, toolCallBuilder.buildAndReset());
                            }
                            responseBuilder.append(event);
                        })
                        .onMetadata(chunk -> {
                            responseBuilder.append(chunk);
                            ChatResponse response = responseFrom(responseBuilder.build(), converseStreamRequest.modelId());
                            try {
                                handler.onCompleteResponse(response);
                            } catch (Exception e) {
                                withLoggingExceptions(() -> handler.onError(e));
                            }
                        })
                        .onMessageStart(responseBuilder::append)
                        .onMessageStop(responseBuilder::append)
                        .build())
                .build();

        try {
            this.client.converseStream(converseStreamRequest, converseStreamResponseHandler).get();
        } catch (Exception e) {
            RuntimeException mappedError = BedrockExceptionMapper.INSTANCE.mapException(e);
            withLoggingExceptions(() -> handler.onError(mappedError));
        }
    }

    @Override
    public BedrockChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    private ConverseStreamRequest buildConverseStreamRequest(ChatRequest chatRequest) {
        return ConverseStreamRequest.builder()
                .modelId(chatRequest.modelName())
                .inferenceConfig(inferenceConfigFrom(chatRequest.parameters()))
                .system(extractSystemMessages(chatRequest.messages()))
                .messages(extractRegularMessages(chatRequest.messages()))
                .toolConfig(extractToolConfigurationFrom(chatRequest))
                .additionalModelRequestFields(additionalRequestModelFieldsFrom(chatRequest.parameters()))
                .build();
    }

    private ChatResponse responseFrom(ConverseResponse converseResponse, String modelId) {
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
