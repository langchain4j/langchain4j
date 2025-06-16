package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.bedrock.AwsDocumentConverter.convertAdditionalModelRequestFields;
import static dev.langchain4j.model.bedrock.AwsDocumentConverter.convertJsonObjectSchemaToDocument;
import static dev.langchain4j.model.bedrock.AwsDocumentConverter.documentFromJson;
import static dev.langchain4j.model.bedrock.AwsDocumentConverter.documentToJson;
import static dev.langchain4j.model.bedrock.Utils.extractAndValidateFormat;
import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static software.amazon.awssdk.core.SdkBytes.fromByteArray;

import dev.langchain4j.Internal;
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
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.model.AnyToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
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

@Internal
abstract class AbstractBedrockChatModel {

    protected final Region region;
    protected final Integer maxRetries;
    protected final Duration timeout;
    protected final BedrockChatRequestParameters defaultRequestParameters;
    protected final List<ChatModelListener> listeners;

    protected AbstractBedrockChatModel(AbstractBuilder<?> builder) {
        this.region = getOrDefault(builder.region, Region.US_EAST_1);
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.timeout = getOrDefault(builder.timeout, Duration.ofMinutes(1));
        this.listeners = copy(builder.listeners);

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        BedrockChatRequestParameters bedrockParameters = builder.defaultRequestParameters instanceof BedrockChatRequestParameters bedrockChatRequestParameters ?
                bedrockChatRequestParameters :
                BedrockChatRequestParameters.EMPTY;

        this.defaultRequestParameters = BedrockChatRequestParameters.builder()
                // common parameters
                .modelName(getOrDefault(builder.modelId, commonParameters.modelName()))
                .temperature(commonParameters.temperature())
                .topP(commonParameters.topP())
                .maxOutputTokens(commonParameters.maxOutputTokens())
                .stopSequences(commonParameters.stopSequences())
                .toolSpecifications(commonParameters.toolSpecifications())
                .toolChoice(commonParameters.toolChoice())
                // Bedrock-specific parameters
                .additionalModelRequestFields(bedrockParameters.additionalModelRequestFields())
                .build();
    }

    protected List<SystemContentBlock> extractSystemMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.type() == ChatMessageType.SYSTEM)
                .map(message -> SystemContentBlock.builder()
                        .text(((SystemMessage) message).text())
                        .build())
                .toList();
    }

    protected List<Message> extractRegularMessages(List<ChatMessage> messages) {
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

    protected void handleToolResult(
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

    protected ContentBlock createToolResultBlock(ToolExecutionResultMessage toolResult) {
        return ContentBlock.builder()
                .toolResult(ToolResultBlock.builder()
                        .toolUseId(toolResult.id())
                        .content(ToolResultContentBlock.builder()
                                .text(toolResult.text())
                                .build())
                        .build())
                .build();
    }

    protected Message convertToBedRockMessage(ChatMessage message) {
        if (message instanceof UserMessage userMsg) {
            return createUserMessage(userMsg);
        } else if (message instanceof AiMessage aiMsg) {
            return createAiMessage(aiMsg);
        }
        throw new IllegalArgumentException("Unsupported message type: " + message.getClass());
    }

    protected Message createUserMessage(UserMessage message) {
        return Message.builder()
                .role(ConversationRole.USER)
                .content(convertContents(message.contents()))
                .build();
    }

    protected Message createAiMessage(AiMessage message) {
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

    protected List<ContentBlock> convertToolRequests(List<ToolExecutionRequest> requests) {
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

    protected List<ContentBlock> convertContents(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return emptyList();
        }

        return contents.stream().map(this::convertContent).toList();
    }

    protected ContentBlock convertContent(Content content) {
        if (content instanceof TextContent text) {
            return ContentBlock.builder().text(text.text()).build();
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

    protected ContentBlock createImageBlock(ImageContent imageContent) {
        final SdkBytes bytes = fromByteArray(
                nonNull(imageContent.image().base64Data())
                        ? Base64.getDecoder().decode(imageContent.image().base64Data())
                        : readBytes(String.valueOf(imageContent.image().url())));
        final String imgFormat = extractAndValidateFormat(imageContent.image());
        return ContentBlock.builder()
                .image(ImageBlock.builder()
                        .format(imgFormat)
                        .source(ImageSource.builder().bytes(bytes).build())
                        .build())
                .build();
    }

    protected ToolConfiguration extractToolConfigurationFrom(ChatRequest chatRequest) {
        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
        ChatRequestParameters parameters = chatRequest.parameters();

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

    protected AiMessage aiMessageFrom(ConverseResponse converseResponse) {
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
            } else if (cBlock.type() == ContentBlock.Type.REASONING_CONTENT) {
                // TODO Implement full support of reasoning
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

    protected TokenUsage tokenUsageFrom(software.amazon.awssdk.services.bedrockruntime.model.TokenUsage tokenUsage) {
        return Optional.ofNullable(tokenUsage)
                .map(usage -> new TokenUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens()))
                .orElseGet(TokenUsage::new);
    }

    protected FinishReason finishReasonFrom(StopReason stopReason) {
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

    protected InferenceConfiguration inferenceConfigFrom(ChatRequestParameters parameters) {
        return InferenceConfiguration.builder()
                .maxTokens(parameters.maxOutputTokens())
                .temperature(dblToFloat(parameters.temperature()))
                .topP(dblToFloat(parameters.topP()))
                .stopSequences(parameters.stopSequences())
                .build();
    }

    protected Document additionalRequestModelFieldsFrom(ChatRequestParameters chatRequestParameters) {
        Map<String, Object> additionalModelRequestFieldsMap =
                new HashMap<>(this.defaultRequestParameters.additionalModelRequestFields());

        if ((chatRequestParameters instanceof BedrockChatRequestParameters bedrockChatRequestParameters)
                && (nonNull(bedrockChatRequestParameters.additionalModelRequestFields()))) {

            additionalModelRequestFieldsMap.putAll(bedrockChatRequestParameters.additionalModelRequestFields());
        }
        if (isNullOrEmpty(additionalModelRequestFieldsMap)) {
            return null;
        } else {
            return convertAdditionalModelRequestFields(additionalModelRequestFieldsMap);
        }
    }

    protected static void validate(ChatRequestParameters parameters) {
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

    protected static Float dblToFloat(Double d) {
        if (Objects.isNull(d)) {
            return null;
        } else return d.floatValue();
    }

    protected static String extractFilenameWithoutExtensionFromUri(URI uri) {
        String extractedCleanFileName = Utils.extractCleanFileName(uri);
        if (isNullOrEmpty(extractedCleanFileName)) {
            extractedCleanFileName = UUID.randomUUID().toString();
        }
        return extractedCleanFileName;
    }

    // Abstract builder class
    public abstract static class AbstractBuilder<T extends AbstractBuilder<T>> {

        protected Region region;
        protected String modelId;
        protected Integer maxRetries;
        protected Duration timeout;
        protected ChatRequestParameters defaultRequestParameters;
        protected Boolean logRequests;
        protected Boolean logResponses;
        protected List<ChatModelListener> listeners;

        @SuppressWarnings("unchecked")
        public T self() {
            return (T) this;
        }

        public T region(Region region) {
            this.region = region;
            return self();
        }

        public T modelId(String modelId) {
            this.modelId = modelId;
            return self();
        }

        public T timeout(Duration timeout) {
            this.timeout = timeout;
            return self();
        }

        public T defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return self();
        }

        public T logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return self();
        }

        public T logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return self();
        }

        public T listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return self();
        }
    }
}
