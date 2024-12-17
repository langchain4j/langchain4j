package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.Objects;

@Experimental
public class OpenAiChatResponseMetadata extends ChatResponseMetadata {

    private final Long created;
    private final String serviceTier;
    private final String systemFingerprint;

    private OpenAiChatResponseMetadata(Builder builder) {
        super(builder);
        this.created = builder.created;
        this.serviceTier = builder.serviceTier;
        this.systemFingerprint = builder.systemFingerprint;
    }

    @Override
    public OpenAiTokenUsage tokenUsage() {
        return (OpenAiTokenUsage) super.tokenUsage();
    }

    @Experimental
    public Long created() {
        return created;
    }

    @Experimental
    public String serviceTier() {
        return serviceTier;
    }

    @Experimental
    public String systemFingerprint() {
        return systemFingerprint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiChatResponseMetadata that = (OpenAiChatResponseMetadata) o;
        return Objects.equals(created, that.created)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(systemFingerprint, that.systemFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                created,
                serviceTier,
                systemFingerprint
        );
    }

    @Override
    public String toString() {
        // TODO inherited
        return "OpenAiChatResponseMetadata{" +
                "id='" + id() + '\'' +
                ", modelName='" + modelName() + '\'' +
                ", tokenUsage=" + tokenUsage() +
                ", finishReason=" + finishReason() +
                ", created=" + created +
                ", serviceTier='" + serviceTier + '\'' +
                ", systemFingerprint='" + systemFingerprint + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private Long created;
        private String serviceTier;
        private String systemFingerprint;

        @Experimental
        public Builder created(Long created) {
            this.created = created;
            return this;
        }

        @Experimental
        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        @Experimental
        public Builder systemFingerprint(String systemFingerprint) {
            this.systemFingerprint = systemFingerprint;
            return this;
        }

        @Override
        public OpenAiChatResponseMetadata build() {
            return new OpenAiChatResponseMetadata(this);
        }
    }
}
