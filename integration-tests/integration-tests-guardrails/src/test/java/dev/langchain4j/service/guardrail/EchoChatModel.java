package dev.langchain4j.service.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A {@link ChatModel} that echoes out the {@link UserMessage}
 */
public class EchoChatModel implements ChatModel {
    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        var userMessage = ((UserMessage) chatRequest.messages().get(0)).singleText();

        return ChatResponse.builder().aiMessage(AiMessage.from(userMessage)).build();
    }
}
