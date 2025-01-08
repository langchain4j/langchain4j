package dev.langchain4j.model.bedrock.converse;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.bedrock.converse.AwsDocumentConverter.convertJsonObjectSchemaToDocument;
import static dev.langchain4j.model.bedrock.converse.AwsDocumentConverter.documentFromJson;
import static dev.langchain4j.model.bedrock.converse.AwsDocumentConverter.documentToJson;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static software.amazon.awssdk.core.SdkBytes.fromByteArray;

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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
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

public class BedrockChatModel implements ChatLanguageModel {

    private final Region region;
    private final AwsCredentialsProvider credentialsProvider;
    private final String modelId;
    private final InferenceConfiguration inferenceConfiguration;
    private final Integer maxRetries;
    private final Duration timeout;
    private final BedrockRuntimeClient client;

    public BedrockChatModel(String modelId) {
        this(
                Region.US_EAST_1,
                DefaultCredentialsProvider.create(),
                modelId,
                InferenceConfiguration.builder().build(),
                5,
                Duration.ofMinutes(1),
                null);
    }

    public BedrockChatModel(
            Region region,
            AwsCredentialsProvider credentialsProvider,
            String modelId,
            InferenceConfiguration inferenceConfiguration,
            Integer maxRetries,
            Duration timeout,
            BedrockRuntimeClient client) {
        this.region = getOrDefault(region, Region.US_EAST_1);
        this.credentialsProvider = getOrDefault(credentialsProvider, DefaultCredentialsProvider.create());
        this.modelId = getOrDefault(modelId, "us.amazon.nova-micro-v1:0");
        this.inferenceConfiguration = getOrDefault(
                inferenceConfiguration, InferenceConfiguration.builder().build());
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.timeout = getOrDefault(timeout, Duration.ofMinutes(1));
        this.client = isNull(client) ? createClient() : client;
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
        ConverseRequest request = buildConverseRequest(messages, toolSpecifications, null);
        ConverseResponse response = withRetry(() -> client.converse(request), this.maxRetries);

        return Response.from(
                aiMessageFrom(response), tokenUsageFrom(response.usage()), finishReasonFrom(response.stopReason()));
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        ConverseRequest convRequest = buildConverseRequest(
                request.messages(), request.parameters().toolSpecifications(), request.parameters());
        ConverseResponse response = withRetry(() -> client.converse(convRequest), this.maxRetries);

        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(response))
                .metadata(ChatResponseMetadata.builder()
                        .finishReason(finishReasonFrom(response.stopReason()))
                        .tokenUsage(tokenUsageFrom(response.usage()))
                        .modelName(convRequest.modelId())
                        .build())
                .build();
    }

    private ConverseRequest buildConverseRequest(
            List<ChatMessage> messages, List<ToolSpecification> toolSpecs, ChatRequestParameters parameters) {
        final String modelName =
                isNull(parameters) || isNull(parameters.modelName()) ? this.modelId : parameters.modelName();

        return ConverseRequest.builder()
                .modelId(modelName)
                .inferenceConfig(inferenceConfigurationFrom(parameters))
                .system(extractSystemMessages(messages))
                .messages(extractRegularMessages(messages))
                .toolConfig(extractToolConfigurationFrom(toolSpecs))
                .build();
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

    private static String extractFilenameWithoutExtensionFromUri(URI uri) {
        // The name can only contain the following characters:
        // Alphanumeric characters, Whitespace characters (no more than one in a row), Hyphens, Parentheses, Square,
        // brackets
        try {
            final String filename = Paths.get(uri).getFileName().toString();
            int dotIndex = filename.lastIndexOf('.');
            String filenameWithoutExtension = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
            return filenameWithoutExtension.replace(".", "-");
        } catch (Exception e) {
            return "document";
        }
    }

    private ContentBlock createImageBlock(ImageContent imageContent) {
        return ContentBlock.builder()
                .image(ImageBlock.builder()
                        .format(extractImageFormat(imageContent.image().mimeType()))
                        .source(ImageSource.builder()
                                .bytes(fromByteArray(Base64.getDecoder()
                                        .decode(imageContent.image().base64Data())))
                                .build())
                        .build())
                .build();
    }

    private String extractImageFormat(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return "jpeg"; // default format
        }
        String[] parts = mimeType.split("/");
        return parts.length > 1 ? parts[1] : "jpeg";
    }

    private ToolConfiguration extractToolConfigurationFrom(List<ToolSpecification> toolSpecifications) {
        final List<Tool> allTools = new ArrayList<>();
        final ToolConfiguration.Builder toolConfigurationBuilder = ToolConfiguration.builder();

        if (Objects.nonNull(toolSpecifications) && !toolSpecifications.isEmpty()) {
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

        return toolConfigurationBuilder.build();
    }

    private AiMessage aiMessageFrom(ConverseResponse converseResponse) {
        ArrayList<ToolExecutionRequest> toolExecRequests = new ArrayList<>();
        String textAnswer = "";
        for (ContentBlock cBlock : converseResponse.output().message().content()) {
            if (cBlock.type() == ContentBlock.Type.TOOL_USE) {
                toolExecRequests.add(ToolExecutionRequest.builder()
                        .name(cBlock.toolUse().name())
                        .id(cBlock.toolUse().toolUseId())
                        .arguments(documentToJson(cBlock.toolUse().input())
                                .replace("\r", "")
                                .replace("\n", ""))
                        .build());
            } else if (cBlock.type() == ContentBlock.Type.TEXT) {
                textAnswer = cBlock.text();
            } else {
                throw new IllegalArgumentException(
                        "Unsupported content in LLM response. Content type: " + cBlock.type());
            }
        }

        return !toolExecRequests.isEmpty()
                ? AiMessage.aiMessage(textAnswer, toolExecRequests)
                : AiMessage.aiMessage(textAnswer);
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

    public static BedrockChatModelBuilder builder() {
        return new BedrockChatModelBuilder();
    }

    private BedrockRuntimeClient createClient() {
        return BedrockRuntimeClient.builder()
                .region(this.region)
                .credentialsProvider(this.credentialsProvider)
                .overrideConfiguration(config -> config.apiCallTimeout(this.timeout))
                .build();
    }

    private InferenceConfiguration inferenceConfigurationFrom(ChatRequestParameters chatRequestParameters) {
        if (Objects.nonNull(chatRequestParameters)) {
            return InferenceConfiguration.builder()
                    .maxTokens(getOrDefault(
                            chatRequestParameters.maxOutputTokens(), this.inferenceConfiguration.maxTokens()))
                    .temperature(getOrDefault(
                            dblToFloat(chatRequestParameters.temperature()), this.inferenceConfiguration.temperature()))
                    .topP(getOrDefault(dblToFloat(chatRequestParameters.topP()), this.inferenceConfiguration.topP()))
                    .stopSequences(getOrDefault(
                            chatRequestParameters.stopSequences(), this.inferenceConfiguration.stopSequences()))
                    .build();
        } else {
            return this.inferenceConfiguration;
        }
    }

    private static Float dblToFloat(Double d) {
        if (Objects.isNull(d)) {
            return null;
        } else return d.floatValue();
    }

    public static class BedrockChatModelBuilder {

        private Region region;
        private AwsCredentialsProvider credentialsProvider;
        private String modelId;
        private Integer maxTokens;
        private Float temperature;
        private Float topP;
        private List<String> stopSequences;
        private Integer maxRetries;
        private Duration timeout;
        private BedrockRuntimeClient client;

        public BedrockChatModelBuilder region(Region region) {
            this.region = region;
            return this;
        }

        public BedrockChatModelBuilder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            return this;
        }

        public BedrockChatModelBuilder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public BedrockChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public BedrockChatModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public BedrockChatModelBuilder topP(Float topP) {
            this.topP = topP;
            return this;
        }

        public BedrockChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = new ArrayList<>(stopSequences);
            return this;
        }

        public BedrockChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public BedrockChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public BedrockChatModelBuilder client(BedrockRuntimeClient client) {
            this.client = client;
            return this;
        }

        public BedrockChatModel build() {
            final InferenceConfiguration inferenceConfiguration = InferenceConfiguration.builder()
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .topP(topP)
                    .stopSequences(stopSequences)
                    .build();
            return new BedrockChatModel(
                    region, credentialsProvider, modelId, inferenceConfiguration, maxRetries, timeout, client);
        }
    }
}
