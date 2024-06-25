package dev.langchain4j.model.ollama;

import java.util.List;

@SuppressWarnings("unused")
public record ChatRequest(String model, List<Message> messages, Options options, String format, Boolean stream) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private List<Message> messages;
        private Options options;
        private String format;
        private Boolean stream;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        public Builder options(Options options) {
            this.options = options;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder from(ChatRequest request) {
            model(request.model);
            messages(request.messages);
            options(request.options);
            format(request.format);
            stream(request.stream);
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(model, messages, options, format, stream);
        }
    }

}
