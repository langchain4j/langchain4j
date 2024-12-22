package dev.langchain4j.model.bedrock.converse;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;

public class BedrockChatModel implements ChatLanguageModel {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private Region region;
    private AwsCredentialsProvider credentialsProvider;
    private String modelId;
    private InferenceConfiguration inferenceConfiguration;
    private Integer maxRetries;
    private Duration timeout;
    private BedrockRuntimeClient client;

    public BedrockChatModel(String modelId) {
        this(
                Region.US_EAST_1,
                DefaultCredentialsProvider.builder().build(),
                modelId,
                InferenceConfiguration.builder().build(),
                5,
                Duration.ofMinutes(1L),
                null
        );
    }

    public BedrockChatModel(
            String modelId,
            InferenceConfiguration inferenceConfiguration,
            Integer maxRetries,
            BedrockRuntimeClient client)
    {
        this(
                null,
                null,
                modelId,
                inferenceConfiguration,
                maxRetries,
                null,
                client
        );
    }

    public BedrockChatModel(
            Region region,
            AwsCredentialsProvider credentialsProvider,
            String modelId,
            InferenceConfiguration inferenceConfiguration,
            Integer maxRetries,
            Duration timeout,
            BedrockRuntimeClient client)
    {
        this.region = region;
        this.credentialsProvider = credentialsProvider;
        this.modelId = modelId;
        this.inferenceConfiguration = inferenceConfiguration;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.client = Objects.isNull(client) ? createClient() : client;
    }

    @Override
    public Response<AiMessage> generate(final List<ChatMessage> messages) {
        return generate(messages, emptyList());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, toolSpecification, singletonList(toolSpecification));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, null, toolSpecifications);
    }

    private Response<AiMessage> generate(
            List<ChatMessage> messages,
            ToolSpecification toolChoiceSpecification,
            List<ToolSpecification> toolSpecifications) {
        List<SystemContentBlock> systemMessages = extractSystemMessagesFrom(messages);

        List<Message> otherMessages = extractOtherMessagesFrom(messages);

        ToolConfiguration toolConfig = extractToolConfigurationFrom(toolChoiceSpecification, toolSpecifications);

        ConverseResponse converseResponse =
                withRetry(() -> sendConverse(systemMessages, otherMessages, toolConfig), this.maxRetries);

        return Response.from(
                aiMessageFrom(converseResponse),
                tokenUsageFrom(converseResponse.usage()),
                finishReasonFrom(converseResponse.stopReason()));
    }

    private List<SystemContentBlock> extractSystemMessagesFrom(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.type() == ChatMessageType.SYSTEM)
                .map(message ->
                        SystemContentBlock.builder().text(((SystemMessage)message).text()).build())
                .collect(toList());
    }

    private List<Message> extractOtherMessagesFrom(List<ChatMessage> messages) {
        List<ChatMessage> otherMessages = messages.stream()
                .filter(message -> message.type() != ChatMessageType.SYSTEM)
                .collect(toList());

        return otherMessages.stream().map(this::messageFrom).collect(toList());
    }

    private Message messageFrom(ChatMessage message) {
        if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return Message.builder()
                    .role(ConversationRole.USER)
                    .content(ContentBlock.builder()
                            .toolResult(ToolResultBlock.builder()
                                    .toolUseId(toolExecutionResultMessage.id())
                                    .status(ToolResultStatus.SUCCESS)
                                    .content(ToolResultContentBlock.builder()
                                            .text(toolExecutionResultMessage.text())
                                            .build())
                                    .build())
                            .build())
                    .build();
        }

        if (message instanceof UserMessage userMessage) {
            return Message.builder()
                    .role(ConversationRole.USER)
                    .content(ContentBlock.builder().text(userMessage.singleText()).build())
                    .build();
        }

        if (message instanceof AiMessage aiMessage) {
            return Message.builder()
                    .role(ConversationRole.ASSISTANT)
                    .content(ContentBlock.builder().text(aiMessage.text()).build())
                    .build();
        }

        throw new IllegalArgumentException(
                "Unknown message type: " + message.getClass().getName());
    }

    private ToolConfiguration extractToolConfigurationFrom(
            ToolSpecification toolChoiceSpecification, List<ToolSpecification> toolSpecifications) {
        final List<Tool> allTools = new ArrayList<>();
        final ToolConfiguration.Builder toolConfigurationBuilder = ToolConfiguration.builder();
        toolConfigurationBuilder.tools(allTools);

        if (Objects.nonNull(toolChoiceSpecification)) {
            final software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolChoice =
                    software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification.builder()
                            .name(toolChoiceSpecification.name())
                            .description(toolChoiceSpecification.description())
                            .inputSchema(ToolInputSchema.builder()
                                    .json(this.mapToDocument(toolChoiceSpecification.parameters()))
                                    .build())
                            .build();

            allTools.add(Tool.builder().toolSpec(toolChoice).build());

            final ToolChoice specifiToolChoice = ToolChoice.builder()
                    .tool(SpecificToolChoice.builder()
                            .name(toolChoiceSpecification.name())
                            .build())
                    .build();

            toolConfigurationBuilder.toolChoice(specifiToolChoice);
        }

        if (Objects.nonNull(toolSpecifications) && !toolSpecifications.isEmpty()) {
            final List<Tool> tools = toolSpecifications.stream()
                    .map(toolSpecification ->
                            software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification.builder()
                                    .name(toolSpecification.name())
                                    .description(toolSpecification.description())
                                    .inputSchema(ToolInputSchema.builder()
                                            .json(this.mapToDocument(toolSpecification.parameters()))
                                            .build())
                                    .build())
                    .map(toolSpecification ->
                            Tool.builder().toolSpec(toolSpecification).build())
                    .collect(toList());

            allTools.addAll(tools);
        }

        if (allTools.isEmpty()) {
            return null;
        }

        return toolConfigurationBuilder.build();
    }

    private Document mapToDocument(JsonObjectSchema parameters) {
        try {
            return Document.fromString(objectMapper.writeValueAsString(parameters));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private ConverseResponse sendConverse(
            List<SystemContentBlock> systemMessages, List<Message> otherMessages, ToolConfiguration toolConfig) {
        final ConverseRequest.Builder requestBuilder =
                ConverseRequest.builder().modelId(this.modelId).inferenceConfig(this.inferenceConfiguration);

        if (Objects.nonNull(systemMessages) && !systemMessages.isEmpty()) {
            requestBuilder.system(systemMessages);
        }

        if (Objects.nonNull(otherMessages) && !otherMessages.isEmpty()) {
            requestBuilder.messages(otherMessages);
        }

        if (Objects.nonNull(toolConfig)) {
            requestBuilder.toolConfig(toolConfig);
        }

        return this.client.converse(requestBuilder.build());
    }

    private AiMessage aiMessageFrom(ConverseResponse converseResponse) {
        return AiMessage.from(
                converseResponse.output().message().content().get(0).text());
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

    public static class BedrockChatModelBuilder {

        private Region region;
        private AwsCredentialsProvider credentialsProvider;
        private String modelId;
        private InferenceConfiguration inferenceConfiguration;
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

        public BedrockChatModelBuilder inferenceConfiguration(InferenceConfiguration inferenceConfiguration) {
            this.inferenceConfiguration = inferenceConfiguration;
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
            return new BedrockChatModel(
                    region,
                    credentialsProvider,
                    modelId,
                    inferenceConfiguration,
                    maxRetries,
                    timeout,
                    client
            );
        }
    }
}
