package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static java.util.stream.Collectors.joining;

/**
 * Bedrock chat model
 */
@Getter
@SuperBuilder
public abstract class BedrockChatModel<T extends BedrockChatInstance> implements ChatLanguageModel {
    private static final String HUMAN_PROMPT = "Human:";
    private static final String ASSISTANT_PROMPT = "Assistant:";

    @Builder.Default
    private final String humanPrompt = HUMAN_PROMPT;
    @Builder.Default
    private final String assistantPrompt = ASSISTANT_PROMPT;
    @Builder.Default
    private final Integer maxRetries = 5;
    @Builder.Default
    private final Region region = Region.US_EAST_1;
    @Builder.Default
    private final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder().build();
    @Builder.Default
    private final int maxTokens = 300;
    @Builder.Default
    private final float temperature = 1;
    @Builder.Default
    private final float topP = 0.999f;
    @Builder.Default
    private final String[] stopSequences = new String[]{};
    @Getter(lazy = true)
    private final BedrockRuntimeClient client = initClient();

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        final String context = messages.stream()
                .filter(message -> message.type() == ChatMessageType.SYSTEM)
                .map(ChatMessage::text)
                .collect(joining("\n"));

        final String userMessages = messages.stream()
                .filter(message -> message.type() != ChatMessageType.SYSTEM)
                .map(this::chatMessageToString)
                .collect(joining("\n"));

        final String prompt = String.format("%s\n\n%s\n%s", context, userMessages, ASSISTANT_PROMPT);
        final Map<String, Object> requestParameters = getRequestParameters(prompt);
        final String body = Json.toJson(requestParameters);

        InvokeModelResponse invokeModelResponse = withRetry(() -> invoke(body), maxRetries);
        final String response = invokeModelResponse.body().asUtf8String();
        final T result = Json.fromJson(response, getResponseClassType());

        return new Response<>(new AiMessage(result.getOutputText()),
                result.getTokenUsage(),
                result.getFinishReason());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Tools are currently not supported for Bedrock models");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        throw new IllegalArgumentException("Tools are currently not supported for Bedrock models");
    }

    /**
     * Convert chat message to string
     *
     * @param message chat message
     * @return string
     */
    protected String chatMessageToString(ChatMessage message) {
        switch (message.type()) {
            case SYSTEM:
                return message.text();
            case USER:
                return humanPrompt + " " + message.text();
            case AI:
                return assistantPrompt + " " + message.text();
            case TOOL_EXECUTION_RESULT:
                throw new IllegalArgumentException("Tool execution results are not supported for Bedrock models");
        }

        throw new IllegalArgumentException("Unknown message type: " + message.type());
    }

    /**
     * Get request parameters
     *
     * @param prompt prompt
     * @return request body
     */
    protected abstract Map<String, Object> getRequestParameters(final String prompt);

    /**
     * Get model id
     *
     * @return model id
     */
    protected abstract String getModelId();


    /**
     * Get response class type
     *
     * @return response class type
     */
    protected abstract Class<T> getResponseClassType();

    /**
     * Invoke call to the API
     *
     * @param body body
     * @return invoke model response
     */
    protected InvokeModelResponse invoke(final String body) {
        // Invoke model
        InvokeModelRequest invokeModelRequest = InvokeModelRequest
                .builder()
                .modelId(getModelId())
                .body(SdkBytes.fromString(body, Charset.defaultCharset()))
                .build();
        return getClient().invokeModel(invokeModelRequest);
    }

    /**
     * Create map with single entry
     *
     * @param key   key
     * @param value value
     * @return map
     */
    protected static Map<String, Object> of(final String key, final Object value) {
        return new HashMap<String, Object>(1) {{
            put(key, value);
        }};
    }

    /**
     * Initialize bedrock client
     *
     * @return bedrock client
     */
    private BedrockRuntimeClient initClient() {
        return BedrockRuntimeClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
