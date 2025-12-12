package dev.langchain4j.model.anthropic.internal.mapper;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.ASSISTANT;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.USER;
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
import dev.langchain4j.model.anthropic.AnthropicServerTool;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicImageContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessageContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicPdfContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicRedactedThinkingContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinkingContent;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Internal
public class AnthropicMapper {

    public static final String THINKING_SIGNATURE_KEY =
            "thinking_signature"; // do not change, will break backward compatibility!
    public static final String REDACTED_THINKING_KEY =
            "redacted_thinking"; // do not change, will break backward compatibility!

    public static List<AnthropicMessage> toAnthropicMessages(List<ChatMessage> messages) {
        return toAnthropicMessages(messages, false);
    }

    public static List<AnthropicMessage> toAnthropicMessages(List<ChatMessage> messages, boolean sendThinking) {

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
                } else if (message instanceof AiMessage aiMessage) {
                    List<AnthropicMessageContent> contents = toAnthropicMessageContents(aiMessage, sendThinking);
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
                            return AnthropicImageContent.fromUrl(image.url().toString());
                        }
                        return AnthropicImageContent.fromBase64(
                                ensureNotBlank(image.mimeType(), "mimeType"),
                                ensureNotBlank(image.base64Data(), "base64Data"));
                    } else if (content instanceof PdfFileContent pdfFileContent) {
                        PdfFile pdfFile = pdfFileContent.pdfFile();
                        if (pdfFile.url() != null) {
                            return AnthropicPdfContent.fromUrl(pdfFile.url().toString());
                        }
                        return AnthropicPdfContent.fromBase64(
                                pdfFile.mimeType(), ensureNotBlank(pdfFile.base64Data(), "base64Data"));
                    } else {
                        throw illegalArgument("Unknown content type: " + content);
                    }
                })
                .collect(toList());
    }

    private static List<AnthropicMessageContent> toAnthropicMessageContents(AiMessage message, boolean sendThinking) {
        List<AnthropicMessageContent> contents = new ArrayList<>();

        if (sendThinking && isNotNullOrBlank(message.thinking())) {
            String signature = message.attribute(THINKING_SIGNATURE_KEY, String.class);
            contents.add(new AnthropicThinkingContent(message.thinking(), signature));
        }

        if (sendThinking && message.attributes().containsKey(REDACTED_THINKING_KEY)) {
            List<String> redactedThinkings = message.attribute(REDACTED_THINKING_KEY, List.class);
            for (String redactedThinking : redactedThinkings) {
                contents.add(new AnthropicRedactedThinkingContent(redactedThinking));
            }
        }

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
        List<SystemMessage> systemMessages = messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .collect(toList());

        SystemMessage lastSystemMessage =
                systemMessages.isEmpty() ? null : systemMessages.get(systemMessages.size() - 1);
        return systemMessages.stream()
                .map(message -> {
                    boolean isLastItem = message.equals(lastSystemMessage);
                    if (isLastItem && cacheType != AnthropicCacheType.NO_CACHE) {
                        return new AnthropicTextContent(message.text(), cacheType.cacheControl());
                    }
                    return new AnthropicTextContent(message.text());
                })
                .collect(toList());
    }

    public static AiMessage toAiMessage(List<AnthropicContent> contents) {
        return toAiMessage(contents, false);
    }

    public static AiMessage toAiMessage(List<AnthropicContent> contents, boolean returnThinking) {

        String text = contents.stream()
                .filter(content -> "text".equals(content.type))
                .map(content -> content.text)
                .collect(joining("\n"));

        String thinking = null;
        Map<String, Object> attributes = new HashMap<>();
        if (returnThinking) {
            thinking = contents.stream()
                    .filter(content -> "thinking".equals(content.type))
                    .map(content -> content.thinking)
                    .collect(joining("\n"));

            String signature = contents.stream()
                    .filter(content -> "thinking".equals(content.type))
                    .map(content -> content.signature)
                    .collect(joining("\n"));
            if (isNotNullOrEmpty(signature)) {
                attributes.put(THINKING_SIGNATURE_KEY, signature);
            }

            List<String> redactedThinkings = contents.stream()
                    .filter(content -> "redacted_thinking".equals(content.type))
                    .map(content -> content.data)
                    .collect(toList());
            if (!redactedThinkings.isEmpty()) {
                attributes.put(REDACTED_THINKING_KEY, redactedThinkings);
            }
        }

        List<ToolExecutionRequest> toolExecutionRequests = contents.stream()
                .filter(content -> "tool_use".equals(content.type))
                .map(content -> ToolExecutionRequest.builder()
                        .id(content.id)
                        .name(content.name)
                        .arguments(toJson(content.input))
                        .build())
                .collect(toList());

        return AiMessage.builder()
                .text(isNullOrEmpty(text) ? null : text)
                .thinking(isNullOrEmpty(thinking) ? null : thinking)
                .toolExecutionRequests(toolExecutionRequests)
                .attributes(attributes)
                .build();
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

    public static AnthropicToolChoice toAnthropicToolChoice(
            ToolChoice toolChoice, String toolChoiceName, Boolean disableParallelToolUse) {
        if (toolChoice == null) {
            return null;
        }

        AnthropicToolChoiceType toolChoiceType =
                switch (toolChoice) {
                    case AUTO -> AnthropicToolChoiceType.AUTO;
                    case REQUIRED -> AnthropicToolChoiceType.ANY;
                    case NONE -> AnthropicToolChoiceType.NONE;
                };

        if (toolChoiceName != null) {
            return AnthropicToolChoice.from(toolChoiceName, disableParallelToolUse);
        }

        return AnthropicToolChoice.from(toolChoiceType, disableParallelToolUse);
    }

    public static List<AnthropicTool> toAnthropicTools(
            List<ToolSpecification> toolSpecifications, AnthropicCacheType cacheToolsPrompt) {
        return toAnthropicTools(toolSpecifications, cacheToolsPrompt, Set.of());
    }

    public static List<AnthropicTool> toAnthropicTools(
            List<ToolSpecification> toolSpecifications,
            AnthropicCacheType cacheToolsPrompt,
            Set<String> toolMetadataKeysToSend) {
        ToolSpecification lastToolSpecification =
                toolSpecifications.isEmpty() ? null : toolSpecifications.get(toolSpecifications.size() - 1);
        return toolSpecifications.stream()
                .map(toolSpecification -> {
                    boolean isLastItem = toolSpecification.equals(lastToolSpecification);
                    if (isLastItem && cacheToolsPrompt != AnthropicCacheType.NO_CACHE) {
                        return toAnthropicTool(toolSpecification, cacheToolsPrompt, toolMetadataKeysToSend);
                    }
                    return toAnthropicTool(toolSpecification, AnthropicCacheType.NO_CACHE, toolMetadataKeysToSend);
                })
                .collect(toList());
    }

    public static AnthropicTool toAnthropicTool(
            ToolSpecification toolSpecification, AnthropicCacheType cacheToolsPrompt) {
        return toAnthropicTool(toolSpecification, cacheToolsPrompt, Set.of());
    }

    public static AnthropicTool toAnthropicTool(
            ToolSpecification toolSpecification,
            AnthropicCacheType cacheToolsPrompt,
            Set<String> toolMetadataKeysToSend) {
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

        if (!toolMetadataKeysToSend.isEmpty()) {
            toolBuilder.customParameters(retainKeys(toolSpecification.metadata(), toolMetadataKeysToSend));
        }

        return toolBuilder.build();
    }

    public static Map<String, Object> retainKeys(Map<String, Object> map, Set<String> keys) {
        Map<String, Object> result = new HashMap<>();
        for (String key : keys) {
            if (map.containsKey(key)) {
                result.put(key, map.get(key));
            }
        }
        return result;
    }

    public static List<AnthropicTool> toAnthropicTools(List<AnthropicServerTool> serverTools) {
        return serverTools.stream().map(AnthropicMapper::toAnthropicTool).toList();
    }

    public static AnthropicTool toAnthropicTool(AnthropicServerTool serverTool) {
        Map<String, Object> customParameters = new LinkedHashMap<>();
        customParameters.put("type", serverTool.type());
        customParameters.putAll(serverTool.attributes());

        return AnthropicTool.builder()
                .name(serverTool.name())
                .customParameters(customParameters)
                .build();
    }
}
