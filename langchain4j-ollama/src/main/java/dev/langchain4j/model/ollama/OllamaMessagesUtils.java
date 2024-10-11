package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.ContentType.IMAGE;
import static dev.langchain4j.data.message.ContentType.TEXT;
import static dev.langchain4j.model.ollama.OllamaJsonUtils.toJson;
import static dev.langchain4j.model.ollama.OllamaJsonUtils.toObject;

class OllamaMessagesUtils {

    private static final Predicate<ChatMessage> isUserMessage =
            UserMessage.class::isInstance;
    private static final Predicate<UserMessage> hasImages =
            userMessage -> userMessage.contents().stream()
                    .anyMatch(content -> IMAGE.equals(content.type()));

    static List<Message> toOllamaMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> isUserMessage.test(message) && hasImages.test((UserMessage) message) ?
                        messagesWithImageSupport((UserMessage) message)
                        : otherMessages(message)
                ).collect(Collectors.toList());
    }

    static List<Tool> toOllamaTools(List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null) {
            return null;
        }
        return toolSpecifications.stream().map(toolSpecification ->
                        Tool.builder()
                                .function(Function.builder()
                                        .name(toolSpecification.name())
                                        .description(toolSpecification.description())
                                        .parameters(toolSpecification.parameters() == null ? null : Parameters.builder()
                                                .properties(toolSpecification.parameters().properties())
                                                .required(toolSpecification.parameters().required())
                                                .build())
                                        .build())
                                .build())
                .collect(Collectors.toList());
    }

    static List<ToolExecutionRequest> toToolExecutionRequest(List<ToolCall> toolCalls) {
        return toolCalls.stream().map(toolCall ->
                        ToolExecutionRequest.builder()
                                .name(toolCall.getFunction().getName())
                                .arguments(toJson(toolCall.getFunction().getArguments()))
                                .build())
                .collect(Collectors.toList());
    }

    private static Message messagesWithImageSupport(UserMessage userMessage) {
        Map<ContentType, List<Content>> groupedContents = userMessage.contents().stream()
                .collect(Collectors.groupingBy(Content::type));

        if (groupedContents.get(TEXT).size() != 1) {
            throw new RuntimeException("Expecting single text content, but got: " + userMessage.contents());
        }

        String text = ((TextContent) groupedContents.get(TEXT).get(0)).text();

        List<ImageContent> imageContents = groupedContents.get(IMAGE).stream()
                .map(content -> (ImageContent) content)
                .collect(Collectors.toList());

        return Message.builder()
                .role(toOllamaRole(userMessage.type()))
                .content(text)
                .images(ImageUtils.base64EncodeImageList(imageContents))
                .build();
    }

    private static Message otherMessages(ChatMessage chatMessage) {
        List<ToolCall> toolCalls = null;
        if (ChatMessageType.AI == chatMessage.type()) {
            AiMessage aiMessage = (AiMessage) chatMessage;
            List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
            toolCalls = Optional.ofNullable(toolExecutionRequests)
                    .map(reqs -> reqs.stream()
                            .map(toolExecutionRequest -> {
                                TypeReference<HashMap<String, Object>> typeReference = new TypeReference<HashMap<String, Object>>() {
                                };
                                FunctionCall functionCall = FunctionCall.builder()
                                        .name(toolExecutionRequest.name())
                                        .arguments(toObject(toolExecutionRequest.arguments(), typeReference))
                                        .build();
                                return ToolCall.builder()
                                        .function(functionCall).build();
                            }).collect(Collectors.toList()))
                    .orElse(null);

        }
        return Message.builder()
                .role(toOllamaRole(chatMessage.type()))
                .content(chatMessage.text())
                .toolCalls(toolCalls)
                .build();
    }

    private static Role toOllamaRole(ChatMessageType chatMessageType) {
        switch (chatMessageType) {
            case SYSTEM:
                return Role.SYSTEM;
            case USER:
                return Role.USER;
            case AI:
                return Role.ASSISTANT;
            case TOOL_EXECUTION_RESULT:
                return Role.TOOL;
            default:
                throw new IllegalArgumentException("Unknown ChatMessageType: " + chatMessageType);
        }
    }
}
