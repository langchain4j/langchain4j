package dev.langchain4j.model.chat.common;

import dev.langchain4j.model.chat.ChatLanguageModel;

public class ChatLanguageModelCapabilities extends ChatModelCapabilities<ChatLanguageModel> {

    private ChatLanguageModelCapabilities(Builder builder) {
        super(builder);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<Builder, ChatLanguageModel> {

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ChatLanguageModelCapabilities build() {
            if (super.model == null) {
                throw new IllegalStateException("Le modèle ne doit pas être null");
            }
            return new ChatLanguageModelCapabilities(this);
        }
    }
}
