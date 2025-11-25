package dev.langchain4j.model.vertexai.anthropic.internal.mapper;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.vertexai.anthropic.internal.Constants;
import dev.langchain4j.model.vertexai.anthropic.internal.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnthropicRequestMapper {

    private AnthropicRequestMapper() {}

    public static AnthropicRequest toRequest(
            String model,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecs,
            ToolChoice toolChoice,
            Integer maxTokens,
            Double temperature,
            Double topP,
            Integer topK,
            List<String> stopSequences,
            Boolean enablePromptCaching) {
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("model cannot be null or empty");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages cannot be null or empty");
        }

        AnthropicRequest request = new AnthropicRequest();
        request.maxTokens = maxTokens != null ? maxTokens : Constants.DEFAULT_MAX_TOKENS;
        request.temperature = temperature;
        request.topP = topP;
        request.topK = topK;
        request.stopSequences = stopSequences;
        request.stream = false;

        // Process messages like the original AnthropicMapper
        List<AnthropicMessage> anthropicMessages = toAnthropicMessages(messages);
        List<AnthropicSystemMessage> systemMessages = toAnthropicSystemMessages(messages);

        // Apply cache control if enabled
        if (enablePromptCaching != null && enablePromptCaching) {
            applyCacheControl(anthropicMessages, systemMessages);
        }

        request.messages = anthropicMessages;
        request.system = systemMessages.isEmpty() ? null : systemMessages;

        // Process tools
        if (toolSpecs != null && !toolSpecs.isEmpty()) {
            request.tools =
                    toolSpecs.stream().map(AnthropicRequestMapper::toTool).toList();
            request.toolChoice = toAnthropicToolChoice(toolChoice);
        }
        request.anthropicVersion = Constants.ANTHROPIC_VERSION;

        return request;
    }

    public static List<AnthropicMessage> toAnthropicMessages(List<ChatMessage> messages) {
        List<AnthropicMessage> anthropicMessages = new ArrayList<>();
        List<AnthropicContent> toolContents = new ArrayList<>();

        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage) {
                toolContents.add(toAnthropicToolResultContent((ToolExecutionResultMessage) message));
            } else if (message instanceof SystemMessage) {
                // ignore, it is handled in the "toAnthropicSystemMessages" method
            } else {
                if (!toolContents.isEmpty()) {
                    anthropicMessages.add(new AnthropicMessage(Constants.USER_ROLE, toolContents));
                    toolContents = new ArrayList<>();
                }

                if (message instanceof UserMessage) {
                    List<AnthropicContent> contents = toAnthropicMessageContents((UserMessage) message);
                    anthropicMessages.add(new AnthropicMessage(Constants.USER_ROLE, contents));
                } else if (message instanceof AiMessage) {
                    List<AnthropicContent> contents = toAnthropicMessageContents((AiMessage) message);
                    anthropicMessages.add(new AnthropicMessage(Constants.ASSISTANT_ROLE, contents));
                }
            }
        }

        if (!toolContents.isEmpty()) {
            anthropicMessages.add(new AnthropicMessage(Constants.USER_ROLE, toolContents));
        }

        return anthropicMessages;
    }

    private static AnthropicContent toAnthropicToolResultContent(ToolExecutionResultMessage message) {
        return AnthropicContent.toolResult(message.id(), message.text());
    }

    private static List<AnthropicContent> toAnthropicMessageContents(UserMessage message) {
        return message.contents().stream()
                .map(content -> {
                    if (content instanceof TextContent textContent) {
                        return AnthropicContent.textContent(textContent.text());
                    } else if (content instanceof ImageContent imageContent) {
                        Image image = imageContent.image();
                        if (image.url() != null) {
                            throw new UnsupportedFeatureException(
                                    "Anthropic does not support images as URLs, only as Base64-encoded strings");
                        }
                        String base64Data = ensureNotBlank(image.base64Data(), "base64Data");
                        String mimeType = ensureNotBlank(image.mimeType(), "mimeType");
                        AnthropicSource source = AnthropicSource.base64(mimeType, base64Data);
                        return AnthropicContent.imageContent(source);
                    } else if (content instanceof PdfFileContent pdfFileContent) {
                        PdfFile pdfFile = pdfFileContent.pdfFile();
                        String base64Data = ensureNotBlank(pdfFile.base64Data(), "base64Data");
                        // Note: Vertex AI Anthropic may not support PDF files yet, but keeping for consistency
                        throw new UnsupportedFeatureException("PDF files are not yet supported in Vertex AI Anthropic");
                    } else {
                        throw illegalArgument("Unknown content type: " + content);
                    }
                })
                .collect(toList());
    }

    private static List<AnthropicContent> toAnthropicMessageContents(AiMessage message) {
        List<AnthropicContent> contents = new ArrayList<>();

        if (isNotNullOrBlank(message.text())) {
            contents.add(AnthropicContent.textContent(message.text()));
        }

        if (message.hasToolExecutionRequests()) {
            List<AnthropicContent> toolUseContents = message.toolExecutionRequests().stream()
                    .map(toolExecutionRequest -> AnthropicContent.toolUse(
                            toolExecutionRequest.id(),
                            toolExecutionRequest.name(),
                            toAnthropicInput(toolExecutionRequest)))
                    .toList();
            contents.addAll(toolUseContents);
        }

        return contents;
    }

    private static Object toAnthropicInput(ToolExecutionRequest toolExecutionRequest) {
        String arguments = toolExecutionRequest.arguments();
        if (isNullOrBlank(arguments)) {
            return Map.of(); // Empty map instead of "{}" string
        }
        try {
            // Parse the JSON string into an Object (Map)
            return Json.fromJson(arguments, Map.class);
        } catch (Exception e) {
            // If parsing fails, return empty map to avoid API errors
            return Map.of();
        }
    }

    public static List<AnthropicSystemMessage> toAnthropicSystemMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> AnthropicSystemMessage.textSystemMessage(((SystemMessage) message).text()))
                .collect(toList());
    }

    public static AnthropicTool toAnthropicTool(ToolSpecification toolSpecification) {
        JsonObjectSchema parameters = toolSpecification.parameters();

        Object properties;
        if (parameters != null && parameters.properties() != null) {
            // toMap returns Map<String, Map<String, Object>> for properties
            properties = toMap(parameters.properties());
        } else {
            properties = emptyMap();
        }

        List<String> required = parameters != null ? parameters.required() : emptyList();

        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", properties,
                "required", required);

        String description;
        if (isNotNullOrBlank(toolSpecification.description())) {
            description = toolSpecification.description();
        } else {
            // Provide intelligent default descriptions based on tool name
            description = switch (toolSpecification.name().toLowerCase()) {
                case "get_current_time", "current_time", "time" -> "Gets the current time";
                case "get_weather", "weather" -> "Gets weather information";
                case "calculator", "calculate" -> "Performs mathematical calculations";
                default -> "Tool: " + toolSpecification.name();
            };
        }

        return new AnthropicTool(toolSpecification.name(), description, inputSchema);
    }

    private static AnthropicTool toTool(ToolSpecification toolSpec) {
        return toAnthropicTool(toolSpec);
    }

    public static AnthropicToolChoice toAnthropicToolChoice(ToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }

        return switch (toolChoice) {
            case AUTO -> AnthropicToolChoice.auto();
            case REQUIRED -> AnthropicToolChoice.any();
            case NONE -> null;
        };
    }

    private static void applyCacheControl(
            List<AnthropicMessage> anthropicMessages, List<AnthropicSystemMessage> systemMessages) {
        // Apply cache control based on Claude's caching strategy
        // According to the documentation, caching happens in this order: tools → system → messages

        // For now, implement a simple strategy: cache the last system message if it exists
        // and the last user message that has substantial content

        // Cache the last system message if it exists
        if (!systemMessages.isEmpty()) {
            AnthropicSystemMessage lastSystemMessage = systemMessages.get(systemMessages.size() - 1);
            lastSystemMessage.cacheControl = AnthropicCacheControl.ephemeral();
        }

        // Cache the last user message if it has substantial content (more than 100 characters)
        for (int i = anthropicMessages.size() - 1; i >= 0; i--) {
            AnthropicMessage message = anthropicMessages.get(i);
            if (Constants.USER_ROLE.equals(message.role) && message.content != null && !message.content.isEmpty()) {
                // Check if the message has substantial text content
                boolean hasSubstantialContent = message.content.stream()
                        .anyMatch(content -> Constants.TEXT_CONTENT_TYPE.equals(content.type)
                                && content.text != null
                                && content.text.length() > Constants.SUBSTANTIAL_CONTENT_THRESHOLD);

                if (hasSubstantialContent) {
                    message.cacheControl = AnthropicCacheControl.ephemeral();
                    break; // Only cache one message for now
                }
            }
        }
    }
}
