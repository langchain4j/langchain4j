package dev.langchain4j.model;

import dev.langchain4j.data.message.ChatMessage;

public class ExampleTestTokenCountEstimator implements TokenCountEstimator {

    @Override
    public int estimateTokenCountInText(String text) {
        return text.split(" ").length;
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        throw new RuntimeException("not implemented");
    }
}
