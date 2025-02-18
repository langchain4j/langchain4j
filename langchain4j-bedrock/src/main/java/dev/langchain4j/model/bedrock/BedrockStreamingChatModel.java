package dev.langchain4j.model.bedrock;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.TextFileContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ListenersUtil;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.AnyToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentFormat;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentSource;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.bedrock.AwsDocumentConverter.convertJsonObjectSchemaToDocument;
import static dev.langchain4j.model.bedrock.AwsDocumentConverter.documentFromJson;
import static dev.langchain4j.model.bedrock.AwsDocumentConverter.documentToJson;
import static dev.langchain4j.model.bedrock.Utils.extractAndValidateFormat;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static software.amazon.awssdk.core.SdkBytes.fromByteArray;

/**
 * BedrockChatModel uses the Bedrock ConverseAPI.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html">https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html</a>
 */
public class BedrockStreamingChatModel implements StreamingChatLanguageModel {

    private final Region region;
    private final String modelId;
    private final Integer maxRetries;
    private final Duration timeout;
    private final BedrockRuntimeAsyncClient client;
    private final ChatRequestParameters defaultRequestParameters;
    private final List<ChatModelListener> listeners;

    public BedrockStreamingChatModel(String modelId) {
        this(builder().modelId(modelId));
    }

    public BedrockStreamingChatModel(Builder builder) {
        this.region = getOrDefault(builder.region, Region.US_EAST_1);
        this.modelId = ensureNotBlank(
                getOrDefault(
                        builder.modelId,
                        nonNull(builder.defaultRequestParameters)
                                ? builder.defaultRequestParameters.modelName()
                                : null),
                "modelId");
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
        this.timeout = getOrDefault(builder.timeout, Duration.ofMinutes(1));
        this.client = isNull(builder.client)
                ? createClient(getOrDefault(builder.logRequests, false), getOrDefault(builder.logResponses, false))
                : builder.client;
        this.listeners = getOrDefault(builder.listeners, List.of());
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            this.defaultRequestParameters = ChatRequestParameters.builder()
                    .overrideWith(builder.defaultRequestParameters)
                    .modelName(this.modelId)
                    .build();
        } else {
            this.defaultRequestParameters = ChatRequestParameters.builder()
                    .modelName(this.modelId)
                    .build();
        }
    }

    @Override
    public void chat(final ChatRequest chatRequest, final StreamingChatResponseHandler handler) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        final ConverseStreamRequest converseStreamRequest = buildConverseStreamRequest(chatRequest.messages(), chatRequest.parameters().toolSpecifications(), chatRequest.parameters());
        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(this.defaultRequestParameters.overrideWith(chatRequest.parameters()))
                .build();

        try {
            ConverseResponseFromStreamBuilder converseResponseBuilder = ConverseResponseFromStreamBuilder.builder();
            final ConverseStreamResponseHandler built = ConverseStreamResponseHandler.builder()
                    .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                            .onContentBlockStart(chunk -> {
                                System.out.println("onContentBlockStart: " + chunk.start().toString());
                                converseResponseBuilder.append(chunk);
                            })
                            .onContentBlockDelta(chunk -> {
                                if (chunk.delta().type().equals(ContentBlockDelta.Type.TEXT)) {
                                    //System.out.print(chunk.delta().text());
                                    handler.onPartialResponse(chunk.delta().text());
                                }
                                System.out.println("contentBlockDelta: " + chunk.delta().toString());
                                converseResponseBuilder.append(chunk);
                            })
                            .onContentBlockStop(chunk -> {
                                System.out.println("onContentBlockStop: " + chunk.contentBlockIndex().toString());
                                converseResponseBuilder.append(chunk);
                            })
                            .onMetadata(chunk -> {
                                System.out.println("onMetadata: " + chunk.usage().toString());
                                converseResponseBuilder.append(chunk);
                                final ChatResponse completeResponse = chatResponseFrom(converseResponseBuilder.build(), converseStreamRequest.modelId());
                                ListenersUtil.onResponse(completeResponse, finalChatRequest, attributes, listeners);
                                handler.onCompleteResponse(completeResponse);
                            })
                            .onMessageStart(chunk -> {
                                System.out.println("onMessageStart: " + chunk.roleAsString());
                                converseResponseBuilder.append(chunk);
                            })
                            .onMessageStop(messageStop -> {
                                System.out.println("onMessageStop: " + messageStop.stopReasonAsString());
                                converseResponseBuilder.append(messageStop);
                            })
                            .build()
                    )
                    .onError(error -> {
                        handler.onError(error);
                        ListenersUtil.onError(error, finalChatRequest, attributes, listeners);
                    })
                    .build();

            ListenersUtil.onRequest(finalChatRequest, attributes, listeners);
            this.client.converseStream(converseStreamRequest, built).get();

        } catch (Exception e) {
            handler.onError(e);
            ListenersUtil.onError(e, finalChatRequest, attributes, listeners);
        }
    }

    public static Response<AiMessage> convertResponse(ChatResponse chatResponse) {
        return Response.from(
                chatResponse.aiMessage(),
                chatResponse.metadata().tokenUsage(),
                chatResponse.metadata().finishReason());
    }

    static StreamingChatResponseHandler convertHandler(StreamingResponseHandler<AiMessage> handler) {
        return new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onNext(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                handler.onComplete(convertResponse(completeResponse));
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();
        chat(chatRequest, convertHandler(handler));
    }

    @Override
    public void generate(List<ChatMessage> messages,
                         List<ToolSpecification> toolSpecifications,
                         StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
        chat(chatRequest, convertHandler(handler));
    }

    @Override
    public void generate(List<ChatMessage> messages,
                         ToolSpecification toolSpecification,
                         StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecification)
                        .toolChoice(REQUIRED)
                        .build())
                .build();
        chat(chatRequest, convertHandler(handler));
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
                .build();
    }

    static void validate(ChatRequestParameters parameters) {
        String errorTemplate = "%s is not supported yet by this model provider";

        if (parameters.topK() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'topK' parameter"));
        }
        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'frequencyPenalty' parameter"));
        }
        if (parameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'presencePenalty' parameter"));
        }
        if (nonNull(parameters.responseFormat())
                && parameters.responseFormat().type().equals(ResponseFormatType.JSON)) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "JSON response format"));
        }
    }

    private List<SystemContentBlock> extractSystemMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.type() == ChatMessageType.SYSTEM)
                .map(message -> SystemContentBlock.builder()
                        .text(((SystemMessage) message).text())
                        .build())
                .toList();
    }

    private List<Message> extractRegularMessages(List<ChatMessage> messages) {
        List<Message> bedrockMessages = new ArrayList<>();
        List<ContentBlock> currentBlocks = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ToolExecutionResultMessage toolResult) {
                handleToolResult(toolResult, currentBlocks, bedrockMessages, i, messages);
            } else if (!(msg instanceof SystemMessage)) {
                bedrockMessages.add(convertToBedRockMessage(msg));
            }
        }

        return bedrockMessages;
    }

    private void handleToolResult(
            ToolExecutionResultMessage toolResult,
            List<ContentBlock> blocks,
            List<Message> bedrockMessages,
            int currentIndex,
            List<ChatMessage> allMessages) {
        blocks.add(createToolResultBlock(toolResult));

        boolean isLastOrNextIsNotToolResult = currentIndex + 1 >= allMessages.size()
                || !(allMessages.get(currentIndex + 1) instanceof ToolExecutionResultMessage);

        if (isLastOrNextIsNotToolResult) {
            bedrockMessages.add(Message.builder()
                    .role(ConversationRole.USER)
                    .content(blocks)
                    .build());
            blocks.clear();
        }
    }

    private ContentBlock createToolResultBlock(ToolExecutionResultMessage toolResult) {
        return ContentBlock.builder()
                .toolResult(ToolResultBlock.builder()
                        .toolUseId(toolResult.id())
                        .content(ToolResultContentBlock.builder()
                                .text(toolResult.text())
                                .build())
                        .build())
                .build();
    }

    private Message convertToBedRockMessage(ChatMessage message) {
        if (message instanceof UserMessage userMsg) {
            return createUserMessage(userMsg);
        } else if (message instanceof AiMessage aiMsg) {
            return createAiMessage(aiMsg);
        }
        throw new IllegalArgumentException("Unsupported message type: " + message.getClass());
    }

    private Message createUserMessage(UserMessage message) {
        return Message.builder()
                .role(ConversationRole.USER)
                .content(convertContents(message.contents()))
                .build();
    }

    private Message createAiMessage(AiMessage message) {
        List<ContentBlock> blocks = new ArrayList<>();

        if (message.text() != null) {
            blocks.add(ContentBlock.builder().text(message.text()).build());
        }

        if (message.hasToolExecutionRequests()) {
            blocks.addAll(convertToolRequests(message.toolExecutionRequests()));
        }

        return Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(blocks)
                .build();
    }

    private List<ContentBlock> convertToolRequests(List<ToolExecutionRequest> requests) {
        return requests.stream()
                .map(req -> ContentBlock.builder()
                        .toolUse(ToolUseBlock.builder()
                                .name(req.name())
                                .toolUseId(req.id())
                                .input(documentFromJson(req.arguments()))
                                .build())
                        .build())
                .toList();
    }

    private List<ContentBlock> convertContents(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return emptyList();
        }

        return contents.stream().map(this::convertContent).toList();
    }

    private ContentBlock convertContent(Content content) {
        if (content instanceof TextContent text) {
            return ContentBlock.builder().text(text.text()).build();
        } else if (content instanceof TextFileContent textFileContent) {
            final SdkBytes bytes = fromByteArray(
                    nonNull(textFileContent.textFile().base64Data())
                            ? Base64.getDecoder()
                            .decode(textFileContent.textFile().base64Data())
                            : readBytes(
                            String.valueOf(textFileContent.textFile().url())));
            return ContentBlock.builder()
                    .document(DocumentBlock.builder()
                            .format(DocumentFormat.TXT)
                            .source(DocumentSource.builder().bytes(bytes).build())
                            .name(extractFilenameWithoutExtensionFromUri(
                                    textFileContent.textFile().url()))
                            .build())
                    .build();
        } else if (content instanceof PdfFileContent pdfFileContent) {
            final SdkBytes bytes = fromByteArray(
                    nonNull(pdfFileContent.pdfFile().base64Data())
                            ? Base64.getDecoder()
                            .decode(pdfFileContent.pdfFile().base64Data())
                            : readBytes(String.valueOf(pdfFileContent.pdfFile().url())));
            return ContentBlock.builder()
                    .document(DocumentBlock.builder()
                            .format(DocumentFormat.PDF)
                            .source(DocumentSource.builder().bytes(bytes).build())
                            .name(extractFilenameWithoutExtensionFromUri(
                                    pdfFileContent.pdfFile().url()))
                            .build())
                    .build();
        } else if (content instanceof ImageContent image) {
            return createImageBlock(image);
        }
        throw new IllegalArgumentException("Unsupported content type: " + content.getClass());
    }

    // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_DocumentBlock.html
    // DocumentBlock must have a non duplicated name. So if we can't extract one from filename we set a random one
    private static String extractFilenameWithoutExtensionFromUri(URI uri) {
        String extractedCleanFileName = Utils.extractCleanFileName(uri);
        if (isNullOrEmpty(extractedCleanFileName)) {
            extractedCleanFileName = UUID.randomUUID().toString();
        }
        return extractedCleanFileName;
    }

    private ContentBlock createImageBlock(ImageContent imageContent) {
        final SdkBytes bytes = fromByteArray(
                nonNull(imageContent.image().base64Data())
                        ? Base64.getDecoder().decode(imageContent.image().base64Data())
                        : readBytes(String.valueOf(imageContent.image().url())));
        // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ImageBlock.html
        // imgFormat valid values are : png | jpeg | gif | webp
        final String imgFormat = extractAndValidateFormat(imageContent.image());
        return ContentBlock.builder()
                .image(ImageBlock.builder()
                        .format(imgFormat)
                        .source(ImageSource.builder().bytes(bytes).build())
                        .build())
                .build();
    }

    private ToolConfiguration extractToolConfigurationFrom(
            List<ToolSpecification> toolSpecifications, ChatRequestParameters parameters) {
        final List<Tool> allTools = new ArrayList<>();
        final ToolConfiguration.Builder toolConfigurationBuilder = ToolConfiguration.builder();

        if (nonNull(toolSpecifications) && !toolSpecifications.isEmpty()) {
            final List<Tool> tools = toolSpecifications.stream()
                    .map(toolSpecification -> {
                        ToolInputSchema toolInputSchema = ToolInputSchema.builder()
                                .json(convertJsonObjectSchemaToDocument(toolSpecification))
                                .build();
                        return software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification.builder()
                                .name(toolSpecification.name())
                                .description(toolSpecification.description())
                                .inputSchema(toolInputSchema)
                                .build();
                    })
                    .map(toolSpecification ->
                            Tool.builder().toolSpec(toolSpecification).build())
                    .toList();

            allTools.addAll(tools);
        }

        if (allTools.isEmpty()) {
            return null;
        } else toolConfigurationBuilder.tools(allTools);

        if (nonNull(parameters) && ToolChoice.REQUIRED.equals(parameters.toolChoice())) {
            toolConfigurationBuilder.toolChoice(software.amazon.awssdk.services.bedrockruntime.model.ToolChoice.fromAny(
                    AnyToolChoice.builder().build()));
        }

        return toolConfigurationBuilder.build();
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

    private AiMessage aiMessageFrom(ConverseResponse converseResponse) {
        ArrayList<ToolExecutionRequest> toolExecRequests = new ArrayList<>();
        String textAnswer = "";
        for (ContentBlock cBlock : converseResponse.output().message().content()) {
            if (cBlock.type() == ContentBlock.Type.TOOL_USE) {
                toolExecRequests.add(ToolExecutionRequest.builder()
                        .name(cBlock.toolUse().name())
                        .id(cBlock.toolUse().toolUseId())
                        .arguments(documentToJson(cBlock.toolUse().input()))
                        .build());
            } else if (cBlock.type() == ContentBlock.Type.TEXT) {
                textAnswer = cBlock.text();
            } else {
                throw new IllegalArgumentException(
                        "Unsupported content in LLM response. Content type: " + cBlock.type());
            }
        }
        if (!toolExecRequests.isEmpty()) {
            if (isNullOrEmpty(textAnswer)) return AiMessage.aiMessage(toolExecRequests);
            else return AiMessage.aiMessage(textAnswer, toolExecRequests);
        }
        return AiMessage.aiMessage(textAnswer);
    }

    private TokenUsage tokenUsageFrom(software.amazon.awssdk.services.bedrockruntime.model.TokenUsage tokenUsage) {
        return Optional.ofNullable(tokenUsage)
                .map(usage -> new TokenUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens()))
                .orElseGet(TokenUsage::new);
    }

    private FinishReason finishReasonFrom(StopReason stopReason) {
        if (stopReason == StopReason.END_TURN || stopReason == StopReason.STOP_SEQUENCE) {
            return FinishReason.STOP;
        }

        if (stopReason == StopReason.MAX_TOKENS) {
            return FinishReason.LENGTH;
        }

        if (stopReason == StopReason.TOOL_USE) {
            return FinishReason.TOOL_EXECUTION;
        }

        throw new IllegalArgumentException("Unknown stop reason: " + stopReason);
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

    private InferenceConfiguration inferenceConfigurationFrom(ChatRequestParameters chatRequestParameters) {
        if (nonNull(chatRequestParameters)) {
            return InferenceConfiguration.builder()
                    .maxTokens(getOrDefault(
                            chatRequestParameters.maxOutputTokens(), this.defaultRequestParameters.maxOutputTokens()))
                    .temperature(dblToFloat(getOrDefault(
                            chatRequestParameters.temperature(), this.defaultRequestParameters.temperature())))
                    .topP(dblToFloat(getOrDefault(chatRequestParameters.topP(), this.defaultRequestParameters.topP())))
                    .stopSequences(getOrDefault(
                            chatRequestParameters.stopSequences(), this.defaultRequestParameters.stopSequences()))
                    .build();
        } else {
            return InferenceConfiguration.builder()
                    .maxTokens(this.defaultRequestParameters.maxOutputTokens())
                    .temperature(dblToFloat(this.defaultRequestParameters.temperature()))
                    .topP(dblToFloat(this.defaultRequestParameters.topP()))
                    .stopSequences(this.defaultRequestParameters.stopSequences())
                    .build();
        }
    }

    public static Float dblToFloat(Double d) {
        if (Objects.isNull(d)) {
            return null;
        } else return d.floatValue();
    }

    public static class Builder {
        private Region region;
        private String modelId;
        private Integer maxRetries;
        private Duration timeout;
        private BedrockRuntimeAsyncClient client;
        private ChatRequestParameters defaultRequestParameters;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;

        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder client(BedrockRuntimeAsyncClient client) {
            this.client = client;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }


        public BedrockStreamingChatModel build() {
            return new BedrockStreamingChatModel(this);
        }
    }
}
