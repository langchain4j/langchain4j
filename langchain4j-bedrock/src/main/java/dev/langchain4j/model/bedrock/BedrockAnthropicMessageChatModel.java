package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import dev.langchain4j.model.output.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Getter
@SuperBuilder
public class BedrockAnthropicMessageChatModel extends AbstractBedrockChatModel<BedrockAnthropicMessageChatModelResponse> {
    
    private static final String DEFAULT_ANTHROPIC_VERSION = "bedrock-2023-05-31";
    
    @Builder.Default
    private final int topK = 250;
    @Builder.Default
    private final String anthropicVersion = DEFAULT_ANTHROPIC_VERSION;
    @Builder.Default
    private final String model = Types.AnthropicClaude3SonnetV1.getValue();
    
    @Override
    protected String getModelId() {
        return model;
    }
    
    @Override
    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(9);
        parameters.put("max_tokens", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("top_k", topK);
        parameters.put("top_p", getTopP());
        parameters.put("stop_sequences", getStopSequences());
        parameters.put("anthropic_version", anthropicVersion);
        return parameters;
    }
    
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        final String system = getAnthropicSystemPrompt(messages);
        
        List<BedrockAnthropicMessage> formattedMessages = getAnthropicMessages(messages);
        
        Map<String, Object> parameters = getRequestParameters(null);
        parameters.put("messages", formattedMessages);
        parameters.put("system", system);
        
        final String body = Json.toJson(parameters);
        
        InvokeModelResponse invokeModelResponse = withRetry(() -> invoke(body), getMaxRetries());
        final String response = invokeModelResponse.body().asUtf8String();
        BedrockAnthropicMessageChatModelResponse result = Json.fromJson(response, getResponseClassType());
        
        return new Response<>(new AiMessage(result.getOutputText()),
            result.getTokenUsage(),
            result.getFinishReason());
    }
    
    private List<BedrockAnthropicMessage> getAnthropicMessages(List<ChatMessage> messages) {
        return messages.stream()
            .filter(message -> message.type() != ChatMessageType.SYSTEM)
            .map(message -> new BedrockAnthropicMessage(getAnthropicRole(message), getAnthropicContent(message)))
            .collect(Collectors.toList());
    }
    
    private List<BedrockAnthropicContent> getAnthropicContent(ChatMessage message) {
        if (message instanceof AiMessage) {
            return Collections.singletonList(new BedrockAnthropicContent("text", ((AiMessage) message).text()));
        } else if (message instanceof UserMessage) {
            return ((UserMessage) message).contents().stream()
                .map(BedrockAnthropicMessageChatModel::mapContentToAnthropic)
                .collect(Collectors.toList());
        } else {
            throw new IllegalArgumentException("Unknown message type: " + message.type());
        }
    }
    
    private static BedrockAnthropicContent mapContentToAnthropic(Content content) {
        if (content instanceof TextContent) {
            return new BedrockAnthropicContent("text", ((TextContent) content).text());
        } else if (content instanceof ImageContent) {
            ImageContent imageContent = (ImageContent) content;
            if (imageContent.image().url() != null) {
                throw new IllegalArgumentException("Anthropic does not support images as URLs, only as Base64-encoded strings");
            }
            BedrockAnthropicImageSource imageSource = new BedrockAnthropicImageSource(
                "base64",
                ensureNotBlank(imageContent.image().mimeType(), "mimeType"),
                ensureNotBlank(imageContent.image().base64Data(), "base64Data")
            );
            return new BedrockAnthropicContent("image", imageSource);
        } else {
            throw new IllegalArgumentException("Unknown content type: " + content);
        }
    }
    
    private String getAnthropicSystemPrompt(List<ChatMessage> messages) {
        return messages.stream()
            .filter(message -> message.type() == ChatMessageType.SYSTEM)
            .map(ChatMessage::text)
            .collect(joining("\n"));
    }
    
    private String getAnthropicRole(ChatMessage message) {
        return message.type() == ChatMessageType.AI ? "assistant" : "user";
    }
    
    @Override
    public Class<BedrockAnthropicMessageChatModelResponse> getResponseClassType() {
        return BedrockAnthropicMessageChatModelResponse.class;
    }

    /**
     * Bedrock Anthropic model ids
     */
    @Getter
    public enum Types {
        AnthropicClaudeInstantV1("anthropic.claude-instant-v1"),
        AnthropicClaudeV2("anthropic.claude-v2"),
        AnthropicClaudeV2_1("anthropic.claude-v2:1"),
        AnthropicClaude3SonnetV1("anthropic.claude-3-sonnet-20240229-v1:0"),
        AnthropicClaude3HaikuV1("anthropic.claude-3-haiku-20240307-v1:0");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }
}
