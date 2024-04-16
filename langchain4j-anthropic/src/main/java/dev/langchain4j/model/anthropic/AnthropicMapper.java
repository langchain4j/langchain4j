package dev.langchain4j.model.anthropic;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.AnthropicRole.ASSISTANT;
import static dev.langchain4j.model.anthropic.AnthropicRole.USER;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class AnthropicMapper {

    static List<AnthropicMessage> toAnthropicMessages(List<ChatMessage> messages) {

        List<AnthropicMessage> anthropicMessages = new ArrayList<>();
        List<AnthropicMessageContent> toolContents = new ArrayList<>();

        for (ChatMessage message : messages) {

            if (message instanceof ToolExecutionResultMessage) {
                toolContents.add(toAnthropicToolResultContent((ToolExecutionResultMessage) message));
            } else {
                if (!toolContents.isEmpty()) {
                    anthropicMessages.add(new AnthropicMessage(USER, toolContents));
                    toolContents = new ArrayList<>();
                }

                if (message instanceof UserMessage) {
                    List<AnthropicMessageContent> contents = toAnthropicMessageContents((UserMessage) message);
                    anthropicMessages.add(new AnthropicMessage(USER, contents));
                } else if (message instanceof AiMessage) {
                    List<AnthropicMessageContent> contents = toAnthropicMessageContents((AiMessage) message);
                    anthropicMessages.add(new AnthropicMessage(ASSISTANT, contents));
                }
            }
        }

        if (!toolContents.isEmpty()) {
            anthropicMessages.add(new AnthropicMessage(USER, toolContents));
        }

        return anthropicMessages;
    }

    private static AnthropicToolResultContent toAnthropicToolResultContent(ToolExecutionResultMessage message) {
        return new AnthropicToolResultContent(message.id(), message.text(), null); // TODO propagate isError
    }

    private static List<AnthropicMessageContent> toAnthropicMessageContents(UserMessage message) {
        return message.contents().stream()
                .map(content -> {
                    if (content instanceof TextContent) {
                        return new AnthropicTextContent(((TextContent) content).text());
                    } else if (content instanceof ImageContent) {
                        Image image = ((ImageContent) content).image();
                        if (image.url() != null) {
                            throw illegalArgument("Anthropic does not support images as URLs, " +
                                    "only as Base64-encoded strings");
                        }
                        return new AnthropicImageContent(
                                ensureNotBlank(image.mimeType(), "mimeType"),
                                ensureNotBlank(image.base64Data(), "base64Data")
                        );
                    } else {
                        throw illegalArgument("Unknown content type: " + content);
                    }
                }).collect(toList());
    }

    private static List<AnthropicMessageContent> toAnthropicMessageContents(AiMessage message) {
        List<AnthropicMessageContent> contents = new ArrayList<>();

        if (isNotNullOrBlank(message.text())) {
            contents.add(new AnthropicTextContent(message.text()));
        }

        if (message.hasToolExecutionRequests()) {
            List<AnthropicToolUseContent> toolUseContents = message.toolExecutionRequests().stream()
                    .map(toolExecutionRequest -> AnthropicToolUseContent.builder()
                            .id(toolExecutionRequest.id())
                            .name(toolExecutionRequest.name())
                            .input(Json.fromJson(toolExecutionRequest.arguments(), Map.class))
                            .build())
                    .collect(toList());
            contents.addAll(toolUseContents);
        }

        return contents;
    }

    static String toAnthropicSystemPrompt(List<ChatMessage> messages) {
        String systemPrompt = messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> ((SystemMessage) message).text())
                .collect(joining("\n\n"));

        if (isNullOrBlank(systemPrompt)) {
            return null;
        } else {
            return systemPrompt;
        }
    }

    public static AiMessage toAiMessage(List<AnthropicContent> contents) {

        String text = contents.stream()
                .filter(content -> "text".equals(content.type))
                .map(content -> content.text)
                .collect(joining("\n"));

        List<ToolExecutionRequest> toolExecutionRequests = contents.stream()
                .filter(content -> "tool_use".equals(content.type))
                .map(content -> ToolExecutionRequest.builder()
                        .id(content.id)
                        .name(content.name)
                        .arguments(Json.toJson(content.input))
                        .build())
                .collect(toList());

        if (isNotNullOrBlank(text) && !isNullOrEmpty(toolExecutionRequests)) {
            return new AiMessage(text, toolExecutionRequests);
        } else if (!isNullOrEmpty(toolExecutionRequests)) {
            return AiMessage.from(toolExecutionRequests);
        } else {
            return AiMessage.from(text);
        }
    }

    public static TokenUsage toTokenUsage(AnthropicUsage anthropicUsage) {
        if (anthropicUsage == null) {
            return null;
        }
        return new TokenUsage(anthropicUsage.inputTokens, anthropicUsage.outputTokens);
    }

    public static FinishReason toFinishReason(String anthropicStopReason) {
        if (anthropicStopReason == null) {
            return null;
        }
        switch (anthropicStopReason) {
            case "end_turn":
                return STOP;
            case "max_tokens":
                return LENGTH;
            case "stop_sequence":
                return OTHER; // TODO
            case "tool_use":
                return TOOL_EXECUTION;
            default:
                return null; // TODO
        }
    }

    static List<AnthropicTool> toAnthropicTools(List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null) {
            return null;
        }
        return toolSpecifications.stream()
                .map(AnthropicMapper::toAnthropicTool)
                .collect(toList());
    }

    static AnthropicTool toAnthropicTool(ToolSpecification toolSpecification) {
        ToolParameters parameters = toolSpecification.parameters();
        return AnthropicTool.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .inputSchema(AnthropicToolSchema.builder()
                        .properties(parameters != null ? parameters.properties() : emptyMap())
                        .required(parameters != null ? parameters.required() : emptyList())
                        .build())
                .build();
    }
}
