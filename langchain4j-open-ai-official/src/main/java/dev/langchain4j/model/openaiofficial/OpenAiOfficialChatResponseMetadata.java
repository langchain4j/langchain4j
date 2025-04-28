package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.Objects;

@Experimental
public class OpenAiOfficialChatResponseMetadata extends ChatResponseMetadata {

    private final Long created;
    private final String serviceTier;
    private final String systemFingerprint;

    private OpenAiOfficialChatResponseMetadata(Builder builder) {
        super(builder);
        this.created = builder.created;
        this.serviceTier = builder.serviceTier;
        this.systemFingerprint = builder.systemFingerprint;
    }

    @Override
    public OpenAiOfficialTokenUsage tokenUsage() {
        return (OpenAiOfficialTokenUsage) super.tokenUsage();
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

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder()))
                .created(created)
                .serviceTier(serviceTier)
                .systemFingerprint(systemFingerprint);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiOfficialChatResponseMetadata that = (OpenAiOfficialChatResponseMetadata) o;
        return Objects.equals(created, that.created)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(systemFingerprint, that.systemFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), created, serviceTier, systemFingerprint);
    }

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

        @Override
        public OpenAiOfficialChatResponseMetadata build() {
            return new OpenAiOfficialChatResponseMetadata(this);
        }
    }
}
