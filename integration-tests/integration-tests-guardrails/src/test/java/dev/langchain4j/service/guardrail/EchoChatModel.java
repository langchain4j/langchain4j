package dev.langchain4j.service.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A {@link ChatLanguageModel} that echoes out the {@link UserMessage}
 */
public class EchoChatModel implements ChatLanguageModel {
    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        var userMessage = ((UserMessage) chatRequest.messages().get(0)).singleText();

        return ChatResponse.builder().aiMessage(AiMessage.from(userMessage)).build();
    }
}
