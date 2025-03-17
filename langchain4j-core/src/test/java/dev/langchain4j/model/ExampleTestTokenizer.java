package dev.langchain4j.model;

import dev.langchain4j.data.message.ChatMessage;

public class ExampleTestTokenizer implements Tokenizer {

    @Override
    public int estimateTokenCountInText(String text) {
        return text.split(" ").length;
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInText(message.text());
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int tokenCount = 0;
        for (ChatMessage message : messages) {
            tokenCount += estimateTokenCountInMessage(message);
        }
        return tokenCount;
    }
}
