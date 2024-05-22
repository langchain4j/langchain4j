package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.internal.Json;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

@Getter
@SuperBuilder
public abstract class AbstractSharedBedrockChatModel {
    // Claude requires you to enclose the prompt as follows:
    // String enclosedPrompt = "Human: " + prompt + "\n\nAssistant:";
    protected static final String HUMAN_PROMPT = "Human:";
    protected static final String ASSISTANT_PROMPT = "Assistant:";
    protected static final String DEFAULT_ANTHROPIC_VERSION = "bedrock-2023-05-31";

    @Builder.Default
    protected final String humanPrompt = HUMAN_PROMPT;
    @Builder.Default
    protected final String assistantPrompt = ASSISTANT_PROMPT;
    @Builder.Default
    protected final Integer maxRetries = 5;
    @Builder.Default
    protected final Region region = Region.US_EAST_1;
    @Builder.Default
    protected final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder().build();
    @Builder.Default
    protected final int maxTokens = 300;
    @Builder.Default
    protected final double temperature = 1;
    @Builder.Default
    protected final float topP = 0.999f;
    @Builder.Default
    protected final String[] stopSequences = new String[]{};
    @Builder.Default
    protected final int topK = 250;
    @Builder.Default
    protected final String anthropicVersion = DEFAULT_ANTHROPIC_VERSION;


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

    protected String convertMessagesToAwsBody(List<ChatMessage> messages) {
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
        return body;
    }

    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(7);

        parameters.put("prompt", prompt);
        parameters.put("max_tokens_to_sample", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("top_k", topK);
        parameters.put("top_p", getTopP());
        parameters.put("stop_sequences", getStopSequences());
        parameters.put("anthropic_version", anthropicVersion);

        return parameters;
    }

    /**
     * Get model id
     *
     * @return model id
     */
    protected abstract String getModelId();

}
