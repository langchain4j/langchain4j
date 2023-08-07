package dev.langchain4j.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.context.StreamingChatLanguageModelContext;

import java.util.List;

public class TokenStreamFactory {

    public static TokenStream of(List<ChatMessage> chatMessages, StreamingChatLanguageModelContext context) {
        return new AiServiceTokenStream(chatMessages, context);
    }
}
