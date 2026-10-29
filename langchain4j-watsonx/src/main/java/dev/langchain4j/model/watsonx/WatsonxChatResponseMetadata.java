package dev.langchain4j.model.watsonx;

import com.ibm.watsonx.ai.chat.TextChatResponse.DetectionEntry;
import com.ibm.watsonx.ai.chat.TextChatResponse.ModerationResult;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WatsonxChatResponseMetadata extends ChatResponseMetadata {

    private final Long created;
    private final String modelVersion;
    private final String serviceTier;
    private final String systemFingerprint;
    private final Boolean cached;
    private final Map<String, List<ModerationResult>> moderations;
    private final Map<String, List<DetectionEntry>> detections;

    private WatsonxChatResponseMetadata(Builder builder) {
        super(builder);
        created = builder.created;
        modelVersion = builder.modelVersion;
        serviceTier = builder.serviceTier;
        systemFingerprint = builder.systemFingerprint;
        cached = builder.cached;
        moderations = builder.moderations;
        detections = builder.detections;
    }

    public Long created() {
        return created;
    }

    public String modelVersion() {
        return modelVersion;
    }

    public String serviceTier() {
        return serviceTier;
    }

    public String systemFingerprint() {
        return systemFingerprint;
    }

    public Boolean cached() {
        return cached;
    }

    public Map<String, List<ModerationResult>> moderations() {
        return moderations;
    }

    public Map<String, List<DetectionEntry>> detections() {
        return detections;
    }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder()))
                .created(created)
                .modelVersion(modelVersion)
                .serviceTier(serviceTier)
                .systemFingerprint(systemFingerprint)
                .cached(cached)
                .moderations(moderations)
                .detections(detections);
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
                && Objects.equals(cached, that.cached)
                && Objects.equals(moderations, that.moderations)
                && Objects.equals(detections, that.detections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                created,
                modelVersion,
                serviceTier,
                systemFingerprint,
                cached,
                moderations,
                detections);
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
                + cached + ", moderations="
                + moderations + ", detections="
                + detections + '}';
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
        private Map<String, List<ModerationResult>> moderations;
        private Map<String, List<DetectionEntry>> detections;

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

        public Builder moderations(Map<String, List<ModerationResult>> moderations) {
            this.moderations = moderations;
            return this;
        }

        public Builder detections(Map<String, List<DetectionEntry>> detections) {
            this.detections = detections;
            return this;
        }

        @Override
        public WatsonxChatResponseMetadata build() {
            return new WatsonxChatResponseMetadata(this);
        }
    }
}
