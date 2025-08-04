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
        List<ChatMessage> messages = chatRequest.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                UserMessage transformedMessage = UserMessage.from(transformUserMessage(userMessage.singleText(), memoryId));
                List<ChatMessage> modifiedMessages = chatRequest.messages().stream()
                        .map(message -> message == userMessage ? transformedMessage : message)
                        .toList();
                return ChatRequest.builder()
                        .messages(modifiedMessages)
                        .parameters(chatRequest.parameters())
                        .build();
            }
        }
        return chatRequest;
    }

    String transformUserMessage(String userMessage, Object memoryId);
}
