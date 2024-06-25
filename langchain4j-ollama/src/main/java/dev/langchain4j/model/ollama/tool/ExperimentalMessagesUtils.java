package dev.langchain4j.model.ollama.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.ollama.ImageUtils;
import dev.langchain4j.model.ollama.Message;
import dev.langchain4j.model.ollama.Role;
import dev.langchain4j.model.ollama.tool.ExperimentalParallelToolsDelegate.AiStatsMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.ContentType.IMAGE;
import static dev.langchain4j.data.message.ContentType.TEXT;
import static java.util.Collections.singletonList;

class ExperimentalMessagesUtils {

    final static Predicate<ChatMessage> isUserMessage =
            chatMessage -> chatMessage instanceof UserMessage;
    final static Predicate<ChatMessage> isToolsResultMessage =
            chatMessage -> chatMessage instanceof ToolExecutionResultMessage;
    final static Predicate<ChatMessage> isToolsRequestMessage =
            chatMessage -> chatMessage instanceof AiMessage && ((AiMessage) chatMessage).hasToolExecutionRequests();
    final static Predicate<ChatMessage> isAiStatsMessage =
            chatMessage -> chatMessage instanceof ExperimentalParallelToolsDelegate.AiStatsMessage;
    final static Predicate<UserMessage> hasImages =
            userMessage -> userMessage.contents().stream()
                    .anyMatch(content -> IMAGE.equals(content.type()));

    static boolean hasAiStatsMessage(List<ChatMessage> messages) {
        return messages.stream().anyMatch(ExperimentalMessagesUtils::isAiStatsMessage);
    }

    static boolean isAiStatsMessage(ChatMessage message) {
        return isAiStatsMessage.test(message);
    }

    static AiStatsMessage toAiStatsMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(ExperimentalMessagesUtils::isAiStatsMessage)
                .map(AiStatsMessage.class::cast)
                .findFirst().orElseThrow();
    }

    static AiStatsMessage withoutRequests(AiStatsMessage aiStatsMessage) {
        return new AiStatsMessage(aiStatsMessage.text(), aiStatsMessage.getTokenUsage());
    }

    static List<Message> toOllamaGroupedMessages(List<ChatMessage> messages) {
        List<Message> ollamaMessages = new ArrayList<>();
        List<String> toolRequests = new ArrayList<>();
        for (ChatMessage chatMessage : messages) {
            if (isToolsRequestMessage.test(chatMessage)) {
                toToolsRequestMessages(chatMessage).stream()
                        .map(Message::getContent)
                        .forEach(toolRequests::add);
                String msg = "Please provide result for " + String.join("\n", toolRequests);
                ollamaMessages.add(Message.builder().role(Role.ASSISTANT).content(msg).build());
            } else if (isToolsResultMessage.test(chatMessage)) {
                ToolExecutionResultMessage toolMessage = (ToolExecutionResultMessage) chatMessage;
                String toolId = toolMessage.id() == null ? "" : "with id " + toolMessage.id();
                String toolRequest = toolRequests.remove(0);
                ollamaMessages.add(Message.builder().role(Role.USER)
                        .content(String.format("Result %s of %s is %s .", toolId, toolRequest,
                                toolMessage.text())).build());
            } else {
                ollamaMessages.addAll(toOllamaMessages(chatMessage));
            }
        }
        return ollamaMessages;
    }

    private static List<Message> toOllamaMessages(ChatMessage message) {
        if (isUserMessage.test(message) && hasImages.test((UserMessage) message)) {
            return messagesWithImageSupport((UserMessage) message);
        } else if (isToolsResultMessage.test(message)) {
            return toToolsResultMessages(message);
        } else if (isToolsRequestMessage.test(message)) {
            return toToolsRequestMessages(message);
        } else {
            return otherMessages(message);
        }
    }

    private static List<Message> messagesWithImageSupport(UserMessage userMessage) {
        Map<ContentType, List<Content>> groupedContents = userMessage.contents().stream()
                .collect(Collectors.groupingBy(Content::type));

        if (groupedContents.get(TEXT).size() != 1) {
            throw new RuntimeException("Expecting single text content, but got: " + userMessage.contents());
        }

        String text = ((TextContent) groupedContents.get(TEXT).get(0)).text();

        List<ImageContent> imageContents = groupedContents.get(IMAGE).stream()
                .map(content -> (ImageContent) content)
                .collect(Collectors.toList());

        return singletonList(Message.builder()
                .role(toOllamaRole(userMessage.type()))
                .content(text)
                .images(ImageUtils.base64EncodeImageList(imageContents))
                .build());
    }

    private static List<Message> toToolsResultMessages(ChatMessage chatMessage) {
        ToolExecutionResultMessage toolMessage = (ToolExecutionResultMessage) chatMessage;
        return singletonList(Message.builder()
                .role(Role.USER) // ASSISTANT
                .content("Result of " + toolMessage.toolName() + " is " + toolMessage.text())
                .build());
    }

    private static List<Message> toToolsRequestMessages(ChatMessage chatMessage) {
        AiMessage toolMessage = (AiMessage) chatMessage;
        return toolMessage.toolExecutionRequests().stream()
                .map(ter -> Message.builder()
                        .role(Role.USER) // ASSISTANT
                        .content(toText(ter))
                        .build())
                .toList();
    }

    static String toText(ToolExecutionRequest toolExecutionRequest) {
        return String.format("%s (%s)"
                , toolExecutionRequest.name(), toolExecutionRequest.arguments().replace("\n", ""));
    }

    private static List<Message> otherMessages(ChatMessage chatMessage) {
        return singletonList(Message.builder()
                .role(toOllamaRole(chatMessage.type()))
                .content(chatMessage.text())
                .build());
    }

    private static Role toOllamaRole(ChatMessageType chatMessageType) {
        return switch (chatMessageType) {
            case SYSTEM -> Role.SYSTEM;
            case USER -> Role.USER;
            case AI, TOOL_EXECUTION_RESULT -> Role.ASSISTANT;
        };
    }

}
