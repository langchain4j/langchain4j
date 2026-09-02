package dev.langchain4j.model.watsonx;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.Objects;

public class WatsonxChatResponseMetadata extends ChatResponseMetadata {

    private final Long created;
    private final String modelVersion;
    private final String serviceTier;
    private final String systemFingerprint;
    private final Boolean cached;

    private WatsonxChatResponseMetadata(Builder builder) {
        super(builder);
        created = builder.created;
        modelVersion = builder.modelVersion;
        serviceTier = builder.serviceTier;
        systemFingerprint = builder.systemFingerprint;
        cached = builder.cached;
    }

    public Long getCreated() {
        return created;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getServiceTier() {
        return serviceTier;
    }

    public String getSystemFingerprint() {
        return systemFingerprint;
    }

    public Boolean getCached() {
        return cached;
    }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder()))
                .created(created)
                .modelVersion(modelVersion)
                .serviceTier(serviceTier)
                .systemFingerprint(systemFingerprint)
                .cached(cached);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        WatsonxChatResponseMetadata that = (WatsonxChatResponseMetadata) o;
        return Objects.equals(created, that.created)
                && Objects.equals(modelVersion, that.modelVersion)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(systemFingerprint, that.systemFingerprint)
                && Objects.equals(cached, that.cached);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), created, modelVersion, serviceTier, systemFingerprint, cached);
    }

    @Override
    public String toString() {
        return "WatsonxChatResponseMetadata{" + "id='"
                + id() + '\'' + ", modelName='"
                + modelName() + '\'' + ", tokenUsage="
                + tokenUsage() + ", finishReason="
                + finishReason() + ", created="
                + created + ", modelVersion='"
                + modelVersion + '\'' + ", serviceTier='"
                + serviceTier + '\'' + ", systemFingerprint='"
                + systemFingerprint + '\'' + ", cached="
                + cached + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {
        private Long created;
        private String modelVersion;
        private String serviceTier;
        private String systemFingerprint;
        private Boolean cached;

        public Builder created(Long created) {
            this.created = created;
            return this;
        }

        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
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

        public Builder cached(Boolean cached) {
            this.cached = cached;
            return this;
        }

        @Override
        public WatsonxChatResponseMetadata build() {
            return new WatsonxChatResponseMetadata(this);
        }
    }
}
