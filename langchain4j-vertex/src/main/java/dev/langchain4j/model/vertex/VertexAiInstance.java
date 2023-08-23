package dev.langchain4j.model.vertex;

import java.util.List;

class VertexAiInstance {

    private final String context;
    private final List<Message> messages;

    public VertexAiInstance(String context, List<Message> messages) {
        this.context = context;
        this.messages = messages;
    }

    public String context() {
        return context;
    }

    public List<Message> messages() {
        return messages;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String context;
        private List<Message> messages;

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        public VertexAiInstance build() {
            return new VertexAiInstance(context, messages);
        }
    }

    public static class Message {
        private String author;
        private String content;

        public Message(String author, String content) {
            this.author = author;
            this.content = content;
        }

        public String author() {
            return author;
        }

        public String content() {
            return content;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String author;
            private String content;

            public Builder author(String author) {
                this.author = author;
                return this;
            }

            public Builder content(String content) {
                this.content = content;
                return this;
            }

            public Message build() {
                return new Message(author, content);
            }
        }
    }
}
