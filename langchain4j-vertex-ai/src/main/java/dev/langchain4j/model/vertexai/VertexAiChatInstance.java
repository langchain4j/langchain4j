package dev.langchain4j.model.vertexai;

import java.util.List;

class VertexAiChatInstance {

    private final String context;
    private final List<Message> messages;

    VertexAiChatInstance(String context, List<Message> messages) {
        this.context = context;
        this.messages = messages;
    }

    static class Message {

        private final String author;
        private final String content;

        Message(String author, String content) {
            this.author = author;
            this.content = content;
        }
    }
}
