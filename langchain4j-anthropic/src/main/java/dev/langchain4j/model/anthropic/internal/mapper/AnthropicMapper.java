package dev.langchain4j.model.anthropic.internal.mapper;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType.TEXT;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType.TOOL_USE;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.ASSISTANT;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.USER;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.model.anthropic.internal.client.Json.fromJson;
import static dev.langchain4j.model.anthropic.internal.client.Json.toJson;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.OTHER;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicImageContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessageContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicPdfContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTool;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolChoice;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolChoiceType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolResultContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolSchema;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolUseContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicUsage;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Internal
public class AnthropicMapper {

    public static List<AnthropicMessage> toAnthropicMessages(List<ChatMessage> messages) {

        List<AnthropicMessage> anthropicMessages = new ArrayList<>();
        List<AnthropicMessageContent> toolContents = new ArrayList<>();

        for (ChatMessage message : messages) {

            if (message instanceof ToolExecutionResultMessage) {
                toolContents.add(toAnthropicToolResultContent((ToolExecutionResultMessage) message));
            } else if (message instanceof SystemMessage) {
                // ignore, it is handled in the "toAnthropicSystemPrompt" method
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
                    if (content instanceof TextContent textContent) {
                        return new AnthropicTextContent(textContent.text());
                    } else if (content instanceof ImageContent imageContent) {
                        Image image = imageContent.image();
                        if (image.url() != null) {
                            throw new UnsupportedFeatureException(
                                    "Anthropic does not support images as URLs, only as Base64-encoded strings");
                        }
                        return new AnthropicImageContent(
                                ensureNotBlank(image.mimeType(), "mimeType"),
                                ensureNotBlank(image.base64Data(), "base64Data"));
                    } else if (content instanceof PdfFileContent pdfFileContent) {
                        PdfFile pdfFile = pdfFileContent.pdfFile();
                        return new AnthropicPdfContent(pdfFile.mimeType(), ensureNotBlank(pdfFile.base64Data(), "base64Data"));
                    } else {
                        throw illegalArgument("Unknown content type: " + content);
                    }
                })
                .collect(toList());
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
                            .input(toAnthropicInput(toolExecutionRequest))
                            .build())
                    .toList();
            contents.addAll(toolUseContents);
        }

        return contents;
    }

    private static Map<String, Object> toAnthropicInput(ToolExecutionRequest toolExecutionRequest) {
        String arguments = toolExecutionRequest.arguments();
        if (isNullOrBlank(arguments)) {
            return Map.of();
        }

        return fromJson(arguments, Map.class);
    }

    public static List<AnthropicTextContent> toAnthropicSystemPrompt(
            List<ChatMessage> messages, AnthropicCacheType cacheType) {
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
                .map(content -> ToolExecutionRequest.builder()
                        .id(content.id)
                        .name(content.name)
                        .arguments(toJson(content.input))
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

    public static TokenUsage toTokenUsage(AnthropicUsage anthropicUsage) {
        if (anthropicUsage == null) {
            return null;
        }
        return AnthropicTokenUsage.builder()
                .inputTokenCount(anthropicUsage.inputTokens)
                .outputTokenCount(anthropicUsage.outputTokens)
                .cacheCreationInputTokens(anthropicUsage.cacheCreationInputTokens)
                .cacheReadInputTokens(anthropicUsage.cacheReadInputTokens)
                .build();
    }

    public static FinishReason toFinishReason(String anthropicStopReason) {
        if (anthropicStopReason == null) {
            return null;
        }
        return switch (anthropicStopReason) {
            case "end_turn", "stop_sequence" -> STOP;
            case "max_tokens" -> LENGTH;
            case "tool_use" -> TOOL_EXECUTION;
            default -> OTHER;
        };
    }

    public static List<AnthropicTool> toAnthropicTools(
            List<ToolSpecification> toolSpecifications, AnthropicCacheType cacheToolsPrompt) {
        return toolSpecifications.stream()
                .map(toolSpecification -> toAnthropicTool(toolSpecification, cacheToolsPrompt))
                .collect(toList());
    }

    public static AnthropicTool toAnthropicTool(
            ToolSpecification toolSpecification, AnthropicCacheType cacheToolsPrompt) {
        JsonObjectSchema parameters = toolSpecification.parameters();

        AnthropicTool.Builder toolBuilder = AnthropicTool.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .inputSchema(AnthropicToolSchema.builder()
                        .properties(parameters != null ? toMap(parameters.properties()) : emptyMap())
                        .required(parameters != null ? parameters.required() : emptyList())
                        .build());

        if (cacheToolsPrompt != AnthropicCacheType.NO_CACHE) {
            return toolBuilder.cacheControl(cacheToolsPrompt.cacheControl()).build();
        }

        return toolBuilder.build();
    }

    public static AnthropicToolChoice toAnthropicToolChoice(ToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }

        AnthropicToolChoiceType toolChoiceType = switch (toolChoice) {
            case AUTO -> AnthropicToolChoiceType.AUTO;
            case REQUIRED -> AnthropicToolChoiceType.ANY;
        };

        return AnthropicToolChoice.from(toolChoiceType);
    }
}
