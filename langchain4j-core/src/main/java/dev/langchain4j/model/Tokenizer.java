package dev.langchain4j.model;

import dev.langchain4j.data.message.ChatMessage;

public interface Tokenizer {

    int countTokens(String text);

    int countTokens(ChatMessage message);

    int countTokens(Iterable<ChatMessage> messages);
}
