package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

public class MistralAiChatRequestParameters extends DefaultChatRequestParameters {

    private Boolean stream;
    private Boolean safePrompt;
    private Integer randomSeed;

    private MistralAiChatRequestParameters(Builder builder) {
        super(builder);
        this.randomSeed = builder.randomSeed;
        this.safePrompt = builder.safePrompt;
        this.stream = builder.stream;
    }

    public Boolean stream() {
        return stream;
    }

    public Boolean safePrompt() {
        return safePrompt;
    }

    public Integer randomSeed() {
        return randomSeed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Boolean stream;
        private Boolean safePrompt;
        private Integer randomSeed;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof MistralAiChatRequestParameters mistralAiParameters) {
                stream(getOrDefault(mistralAiParameters.stream(), stream));
                safePrompt(getOrDefault(mistralAiParameters.safePrompt(), safePrompt));
                randomSeed(getOrDefault(mistralAiParameters.randomSeed(), randomSeed));
            }
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder safePrompt(Boolean safePrompt) {
            this.safePrompt = safePrompt;
            return this;
        }

        public Builder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        @Override
        public MistralAiChatRequestParameters build() {
            return new MistralAiChatRequestParameters(this);
        }
    }
}
