package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

@Experimental
public class OpenAiChatResponse extends ChatResponse {

    private final String systemFingerprint;

    private OpenAiChatResponse(Builder builder) { // TODO
        super(builder);
        this.systemFingerprint = builder.systemFingerprint;
    }

    public String systemFingerprint() {
        return systemFingerprint;
    }

    @Override
    public TokenUsage tokenUsage() {
        return super.tokenUsage(); // TODO return specialized
    }

    // TODO eq, hash, tostr

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponse.Builder<Builder> {

        private String systemFingerprint;

        public Builder systemFingerprint(String systemFingerprint) {
            this.systemFingerprint = systemFingerprint;
            return this;
        }

        public OpenAiChatResponse build() {
            return new OpenAiChatResponse(this);
        }
    }
}
