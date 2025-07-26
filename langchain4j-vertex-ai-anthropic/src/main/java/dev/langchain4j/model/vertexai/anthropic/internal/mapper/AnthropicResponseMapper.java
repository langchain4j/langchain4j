package dev.langchain4j.model.vertexai.anthropic.internal.mapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicContent;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicResponse;
import java.util.ArrayList;
import java.util.List;

public class AnthropicResponseMapper {

    private AnthropicResponseMapper() {
    }

    public static ChatResponse toChatResponse(AnthropicResponse anthropicResponse) {
        AiMessage aiMessage = toAiMessage(anthropicResponse);
        TokenUsage tokenUsage = toTokenUsage(anthropicResponse);
        FinishReason finishReason = toFinishReason(anthropicResponse.stopReason);

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .tokenUsage(tokenUsage)
                .finishReason(finishReason)
                .build();

        return ChatResponse.builder().aiMessage(aiMessage).metadata(metadata).build();
    }

    private static AiMessage toAiMessage(AnthropicResponse anthropicResponse) {
        if (anthropicResponse.content == null || anthropicResponse.content.isEmpty()) {
            return AiMessage.from("");
        }

        StringBuilder textBuilder = new StringBuilder();
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();

        for (AnthropicContent content : anthropicResponse.content) {
            if ("text".equals(content.type)) {
                textBuilder.append(content.text);
            } else if ("tool_use".equals(content.type)) {
                toolRequests.add(ToolExecutionRequest.builder()
                        .id(content.id)
                        .name(content.name)
                        .arguments(content.input.toString())
                        .build());
            }
        }

        if (!toolRequests.isEmpty()) {
            return AiMessage.from(toolRequests);
        } else {
            return AiMessage.from(textBuilder.toString());
        }
    }

    private static TokenUsage toTokenUsage(AnthropicResponse anthropicResponse) {
        if (anthropicResponse.usage == null) {
            return null;
        }

        // Calculate total input tokens including cache-related tokens
        Integer totalInputTokens = anthropicResponse.usage.inputTokens;
        if (anthropicResponse.usage.cacheCreationInputTokens != null) {
            totalInputTokens = (totalInputTokens != null ? totalInputTokens : 0)
                    + anthropicResponse.usage.cacheCreationInputTokens;
        }
        if (anthropicResponse.usage.cacheReadInputTokens != null) {
            totalInputTokens =
                    (totalInputTokens != null ? totalInputTokens : 0) + anthropicResponse.usage.cacheReadInputTokens;
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
}
