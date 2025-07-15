package dev.langchain4j.model.vertexai.anthropic.internal.mapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
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
            Integer maxTokens,
            Double temperature,
            Double topP,
            Integer topK,
            List<String> stopSequences,
            Boolean enablePromptCaching) {

        AnthropicRequest request = new AnthropicRequest();
        request.maxTokens = maxTokens != null ? maxTokens : Constants.DEFAULT_MAX_TOKENS;
        request.temperature = temperature;
        request.topP = topP;
        request.topK = topK;
        request.stopSequences = stopSequences;
        request.stream = false;

        // Process messages
        List<AnthropicMessage> anthropicMessages = new ArrayList<>();
        List<AnthropicSystemMessage> systemMessages = new ArrayList<>();

        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                systemMessages.add(AnthropicSystemMessage.textSystemMessage(((SystemMessage) message).text()));
            } else {
                anthropicMessages.add(toMessage(message));
            }
        }

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
            request.toolChoice = AnthropicToolChoice.auto();
        }
        request.anthropicVersion = Constants.ANTHROPIC_VERSION;

        return request;
    }

    private static AnthropicMessage toMessage(ChatMessage message) {
        List<AnthropicContent> contents = new ArrayList<>();

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            if (userMessage.hasSingleText()) {
                contents.add(AnthropicContent.textContent(userMessage.singleText()));
            } else {
                for (Content content : userMessage.contents()) {
                    if (content instanceof TextContent) {
                        contents.add(AnthropicContent.textContent(((TextContent) content).text()));
                    } else if (content instanceof ImageContent) {
                        ImageContent imageContent = (ImageContent) content;
                        if (imageContent.image() != null) {
                            String base64Data = imageContent.image().base64Data() != null
                                    ? imageContent.image().base64Data()
                                    : ""; // Handle case where base64Data is null
                            String mimeType = imageContent.image().mimeType() != null
                                    ? imageContent.image().mimeType()
                                    : "image/jpeg"; // Default MIME type
                            AnthropicSource source = AnthropicSource.base64(mimeType, base64Data);
                            contents.add(AnthropicContent.imageContent(source));
                        }
                    }
                }
            }
            return new AnthropicMessage(Constants.USER_ROLE, contents);

        } else if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.hasToolExecutionRequests() && aiMessage.toolExecutionRequests() != null) {
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    if (request != null) {
                        contents.add(AnthropicContent.toolUse(request.id(), request.name(), request.arguments()));
                    }
                }
            } else if (aiMessage.text() != null) {
                contents.add(AnthropicContent.textContent(aiMessage.text()));
            }
            return new AnthropicMessage(Constants.ASSISTANT_ROLE, contents);

        } else if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) message;
            contents.add(AnthropicContent.toolResult(toolResult.id(), toolResult.text()));
            return new AnthropicMessage(Constants.USER_ROLE, contents);
        }

        throw new IllegalArgumentException(
                "Unsupported message type: " + message.getClass().getSimpleName());
    }

    private static AnthropicTool toTool(ToolSpecification toolSpec) {
        return new AnthropicTool(
                toolSpec.name(), toolSpec.description(), convertJsonSchemaToMap(toolSpec.parameters()));
    }

    private static Map<String, Object> convertJsonSchemaToMap(Object schema) {
        if (schema instanceof JsonSchemaElement) {
            // Use the utility method from the core library
            return JsonSchemaElementUtils.toMap((JsonSchemaElement) schema);
        } else if (schema instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapSchema = (Map<String, Object>) schema;
            return mapSchema;
        } else {
            // For other schema types, return as is
            return Map.of("type", schema.toString());
        }
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
