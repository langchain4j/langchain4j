package dev.langchain4j.model.googleai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatParameters;

@Experimental
public class GoogleAiGeminiChatParameters extends ChatParameters {
    // TODO name
    // TODO place
    // TODO add specific parameters

    private GoogleAiGeminiChatParameters(Builder builder) {
        super(builder);
    }

    // TODO equals, hashCode, toString

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatParameters.Builder<Builder> {

        public GoogleAiGeminiChatParameters build() {
            return new GoogleAiGeminiChatParameters(this);
        }
    }
}
