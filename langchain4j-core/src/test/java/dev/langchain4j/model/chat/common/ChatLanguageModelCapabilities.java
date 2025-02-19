package dev.langchain4j.model.chat.common;

import static java.util.Objects.nonNull;

import dev.langchain4j.model.chat.ChatLanguageModel;

public class ChatLanguageModelCapabilities extends ChatModelCapabilities<ChatLanguageModel> {

    private ChatLanguageModelCapabilities(Builder builder) {
        super(builder);
    }

    @Override
    // toString will be used to name Parameterized tests so it should show the model used
    public String toString() {
        if (nonNull(super.mnemonicName())) return super.mnemonicName();

        String modelName = nonNull(super.model().defaultRequestParameters())
                        && nonNull(super.model().defaultRequestParameters().modelName())
                ? " - " + super.model().defaultRequestParameters().modelName()
                : "";
        return super.model().getClass().getSimpleName() + modelName;
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
