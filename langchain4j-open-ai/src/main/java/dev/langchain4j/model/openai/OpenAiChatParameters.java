package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatParameters;

import java.util.Map;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

@Experimental
public class OpenAiChatParameters extends ChatParameters {
    // TODO name
    // TODO place

    // TODO a way to create OpenAiChatParameters from ChatParameters?

    private final Map<String, Integer> logitBias;
    private final Integer seed;

    private OpenAiChatParameters(OpenAiChatParameters.Builder builder) {
        super(builder);
        this.logitBias = copyIfNotNull(builder.logitBias);
        this.seed = builder.seed;
    }

    public Map<String, Integer> logitBias() {
        return logitBias;
    }

    public Integer seed() {
        return seed;
    }

    // TODO equals, hashCode, toString

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatParameters.Builder<OpenAiChatParameters.Builder> {

        private Map<String, Integer> logitBias;
        private Integer seed;

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OpenAiChatParameters build() {
            return new OpenAiChatParameters(this);
        }
    }
}
