package dev.langchain4j.model.vertexai.anthropic.internal.mapper;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.vertexai.anthropic.internal.Constants;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicResponse;
import java.util.List;

public class AnthropicResponseMapper {

    private AnthropicResponseMapper() {}

    public static ChatResponse toChatResponse(AnthropicResponse anthropicResponse) {
        AiMessage aiMessage = toAiMessage(anthropicResponse);
        TokenUsage tokenUsage = toTokenUsage(anthropicResponse);
        FinishReason finishReason = toFinishReason(anthropicResponse.stopReason);

        // Normalize model name back to the format expected by users (- back to @)
        String normalizedModelName = normalizeModelName(anthropicResponse.model);

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .id(anthropicResponse.id)
                .modelName(normalizedModelName)
                .tokenUsage(tokenUsage)
                .finishReason(finishReason)
                .build();

        return ChatResponse.builder().aiMessage(aiMessage).metadata(metadata).build();
    }

    public static AiMessage toAiMessage(AnthropicResponse anthropicResponse) {
        if (anthropicResponse.content == null || anthropicResponse.content.isEmpty()) {
            return AiMessage.from("");
        }

        String text = anthropicResponse.content.stream()
                .filter(content -> content != null && Constants.TEXT_CONTENT_TYPE.equals(content.type))
                .map(content -> content.text)
                .filter(t -> t != null)
                .collect(joining("\n"));

        List<ToolExecutionRequest> toolExecutionRequests = anthropicResponse.content.stream()
                .filter(content -> content != null && Constants.TOOL_USE_CONTENT_TYPE.equals(content.type))
                .filter(content -> isNotNullOrBlank(content.name)) // Ensure tool name is not blank
                .map(content -> ToolExecutionRequest.builder()
                        .id(content.id)
                        .name(content.name)
                        .arguments(content.input != null ? Json.toJson(content.input) : "{}")
                        .build())
                .collect(toList());

        if (isNotNullOrBlank(text) && !isNullOrEmpty(toolExecutionRequests)) {
            return AiMessage.from(text, toolExecutionRequests);
        } else if (!isNullOrEmpty(toolExecutionRequests)) {
            return AiMessage.from(toolExecutionRequests);
        } else {
            return AiMessage.from(text);
        }
    }

    private static TokenUsage toTokenUsage(AnthropicResponse anthropicResponse) {
        if (anthropicResponse.usage == null) {
            return null;
        }

        // Calculate total input tokens including cache-related tokens
        int totalInputTokens = anthropicResponse.usage.inputTokens != null ? anthropicResponse.usage.inputTokens : 0;
        if (anthropicResponse.usage.cacheCreationInputTokens != null) {
            totalInputTokens += anthropicResponse.usage.cacheCreationInputTokens;
        }
        if (anthropicResponse.usage.cacheReadInputTokens != null) {
            totalInputTokens += anthropicResponse.usage.cacheReadInputTokens;
        }

        return new TokenUsage(totalInputTokens, anthropicResponse.usage.outputTokens);
    }

    private static FinishReason toFinishReason(String stopReason) {
        if (stopReason == null) {
            return FinishReason.OTHER;
        }

        return switch (stopReason) {
            case "end_turn" -> FinishReason.STOP;
            case "max_tokens" -> FinishReason.LENGTH;
            case "stop_sequence" -> FinishReason.STOP;
            case "tool_use" -> FinishReason.TOOL_EXECUTION;
            default -> FinishReason.OTHER;
        };
    }

    /**
     * Normalizes the model name returned by Vertex AI back to the format expected by users.
     * Vertex AI transforms '@' to '-' in model names in responses, so we convert it back.
     */
    private static String normalizeModelName(String vertexAiModelName) {
        if (vertexAiModelName == null) {
            return null;
        }

        // Convert claude-3-5-sonnet-v2-20241022 back to claude-3-5-sonnet-v2@20241022
        // This specifically handles the date format at the end
        if (vertexAiModelName.matches(".*-\\d{8}$")) {
            // Find the last occurrence of a dash followed by 8 digits (date format)
            int lastDashIndex = vertexAiModelName.lastIndexOf('-');
            if (lastDashIndex > 0
                    && vertexAiModelName.substring(lastDashIndex + 1).matches("\\d{8}")) {
                return vertexAiModelName.substring(0, lastDashIndex) + "@"
                        + vertexAiModelName.substring(lastDashIndex + 1);
            }
        }

        return vertexAiModelName;
    }
}
