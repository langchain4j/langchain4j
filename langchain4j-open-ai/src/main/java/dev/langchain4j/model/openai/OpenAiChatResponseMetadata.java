package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

@Experimental
public class OpenAiChatResponseMetadata extends ChatResponseMetadata {

    private final Long created;
    private final String serviceTier;
    private final String systemFingerprint;

    private OpenAiChatResponseMetadata(Builder builder) { // TODO
        super(builder);
        this.created = builder.created;
        this.serviceTier = builder.serviceTier;
        this.systemFingerprint = builder.systemFingerprint;
    }

    @Override
    public OpenAiTokenUsage tokenUsage() {
        return (OpenAiTokenUsage) super.tokenUsage();
    }

    public Long created() {
        return created;
    }

    public String serviceTier() {
        return serviceTier;
    }

    public String systemFingerprint() {
        return systemFingerprint;
    }

    // TODO eq, hash, tostr

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private Long created;
        private String serviceTier;
        private String systemFingerprint;

        public Builder created(Long created) {
            this.created = created;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public Builder systemFingerprint(String systemFingerprint) {
            this.systemFingerprint = systemFingerprint;
            return this;
        }

        public OpenAiChatResponseMetadata build() {
            return new OpenAiChatResponseMetadata(this);
        }
    }
}
