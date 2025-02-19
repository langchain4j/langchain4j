package dev.langchain4j.model.chat.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;

public class StreamingChatLanguageModelCapabilities extends ChatModelCapabilities<StreamingChatLanguageModel> {

    private StreamingChatLanguageModelCapabilities(Builder builder) {
        super(builder);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Builder, StreamingChatLanguageModel> {

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public StreamingChatLanguageModelCapabilities build() {
            if (super.model == null) {
                throw new IllegalStateException("Model can't be null");
            }
            return new StreamingChatLanguageModelCapabilities(this);
        }
    }
}
