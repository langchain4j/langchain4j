package dev.langchain4j.agentic.internal;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.function.BiFunction;

@FunctionalInterface
public interface UserMessageTransformer extends BiFunction<ChatRequest, Object, ChatRequest> {

    @Override
    default ChatRequest apply(ChatRequest chatRequest, Object memoryId) {
        return chatRequest.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .findFirst()
                .map(userMessage -> {
                    UserMessage transformedMessage = UserMessage.from(transformUserMessage(userMessage.singleText(), memoryId));
                    List<ChatMessage> messages = chatRequest.messages().stream()
                            .map(message -> message == userMessage ? transformedMessage : message)
                            .toList();
                    return ChatRequest.builder()
                            .messages(messages)
                            .parameters(chatRequest.parameters())
                            .build();
                })
                .orElse(chatRequest);
    }

    String transformUserMessage(String userMessage, Object memoryId);
}
