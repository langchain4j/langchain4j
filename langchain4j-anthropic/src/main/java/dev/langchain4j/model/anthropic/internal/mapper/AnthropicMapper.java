package dev.langchain4j.model.anthropic.internal.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicImageContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessageContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTool;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolResultContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolSchema;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolUseContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicUsage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType.TEXT;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType.TOOL_USE;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.ASSISTANT;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.USER;
import static dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper.toMap;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.OTHER;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class AnthropicMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static List<AnthropicMessage> toAnthropicMessages(List<ChatMessage> messages) {

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
                        TextContent textContent = (TextContent) content;
                        return new AnthropicTextContent(textContent.text());
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
                    .map(toolExecutionRequest -> {
                        try {
                            return AnthropicToolUseContent.builder()
                                    .id(toolExecutionRequest.id())
                                    .name(toolExecutionRequest.name())
                                    .input(OBJECT_MAPPER.readValue(toolExecutionRequest.arguments(), Map.class))
                                    .build();
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(toList());
            contents.addAll(toolUseContents);
        }

        return contents;
    }


    public static List<AnthropicTextContent> toAnthropicSystemPrompt(List<ChatMessage> messages, AnthropicCacheType cacheType) {
        return messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> {
                    SystemMessage systemMessage = (SystemMessage) message;
                    if (cacheType != AnthropicCacheType.NO_CACHE) {
                        return new AnthropicTextContent(systemMessage.text(), cacheType.cacheControl());
                    }
                    return new AnthropicTextContent(systemMessage.text());
                })
                .collect(toList());
    }

    public static AiMessage toAiMessage(List<AnthropicContent> contents) {

        String text = contents.stream()
                .filter(content -> content.type == TEXT)
                .map(content -> content.text)
                .collect(joining("\n"));

        List<ToolExecutionRequest> toolExecutionRequests = contents.stream()
                .filter(content -> content.type == TOOL_USE)
                .map(content -> {
                    try {
                        return ToolExecutionRequest.builder()
                                .id(content.id)
                                .name(content.name)
                                .arguments(OBJECT_MAPPER.writeValueAsString(content.input))
                                .build();
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(toList());

        if (isNotNullOrBlank(text) && !isNullOrEmpty(toolExecutionRequests)) {
            return AiMessage.from(text, toolExecutionRequests);
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
        return new AnthropicTokenUsage(anthropicUsage.inputTokens, anthropicUsage.outputTokens, anthropicUsage.cacheCreationInputTokens, anthropicUsage.cacheReadInputTokens);
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

    public static List<AnthropicTool> toAnthropicTools(List<ToolSpecification> toolSpecifications, AnthropicCacheType cacheToolsPrompt) {
        if (toolSpecifications == null) {
            return null;
        }
        return toolSpecifications.stream()
            .map(toolSpecification -> toAnthropicTool(toolSpecification, cacheToolsPrompt))
            .collect(toList());
    }

    public static AnthropicTool toAnthropicTool(ToolSpecification toolSpecification, AnthropicCacheType cacheToolsPrompt) {
        AnthropicTool. AnthropicToolBuilder toolBuilder;
        if (toolSpecification.parameters() != null) {
            JsonObjectSchema parameters = toolSpecification.parameters();
            toolBuilder = AnthropicTool.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .inputSchema(AnthropicToolSchema.builder()
                    .properties(parameters != null ? toMap(parameters.properties()) : emptyMap())
                    .required(parameters != null ? parameters.required() : emptyList())
                    .build());

        } else {
            ToolParameters parameters = toolSpecification.toolParameters();
            toolBuilder = AnthropicTool.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .inputSchema(AnthropicToolSchema.builder()
                    .properties(parameters != null ? parameters.properties() : emptyMap())
                    .required(parameters != null ? parameters.required() : emptyList())
                    .build());

        }

        if (cacheToolsPrompt != AnthropicCacheType.NO_CACHE) {
            return toolBuilder.cacheControl(cacheToolsPrompt.cacheControl()).build();
        }
        return toolBuilder.build();
    }
}
