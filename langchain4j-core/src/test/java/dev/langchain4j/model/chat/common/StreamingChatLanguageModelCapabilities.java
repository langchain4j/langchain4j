package dev.langchain4j.model.chat.common;

import static java.util.Objects.nonNull;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;

public class StreamingChatLanguageModelCapabilities extends ChatModelCapabilities<StreamingChatLanguageModel> {

    private StreamingChatLanguageModelCapabilities(Builder builder) {
        super(builder);
    }

    public static Builder builder() {
        return new Builder();
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
