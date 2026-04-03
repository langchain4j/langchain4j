package dev.langchain4j.agentic.internal;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.function.UnaryOperator;

public class UserMessageRecorder implements UnaryOperator<ChatRequest> {

    private UserMessage lastUserMessage;

    @Override
    public ChatRequest apply(final ChatRequest chatRequest) {
        List<ChatMessage> messages = chatRequest.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                lastUserMessage = userMessage;
                break;
            }
        }
        return chatRequest;
    }

    public UserMessage lastUserMessage() {
        return lastUserMessage;
    }
}
