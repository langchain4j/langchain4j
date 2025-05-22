package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiGroundingAttribution {
    private GeminiAttributionSourceId sourceId;
    private GeminiContent content;

    @JsonCreator
    GeminiGroundingAttribution(@JsonProperty("sourceId") GeminiAttributionSourceId sourceId, @JsonProperty("content") GeminiContent content) {
        this.sourceId = sourceId;
        this.content = content;
    }

    public static GeminiGroundingAttributionBuilder builder() {
        return new GeminiGroundingAttributionBuilder();
    }

    public GeminiAttributionSourceId getSourceId() {
        return this.sourceId;
    }

    public GeminiContent getContent() {
        return this.content;
    }

    public void setSourceId(GeminiAttributionSourceId sourceId) {
        this.sourceId = sourceId;
    }

    public void setContent(GeminiContent content) {
        this.content = content;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiGroundingAttribution)) return false;
        final GeminiGroundingAttribution other = (GeminiGroundingAttribution) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$sourceId = this.getSourceId();
        final Object other$sourceId = other.getSourceId();
        if (this$sourceId == null ? other$sourceId != null : !this$sourceId.equals(other$sourceId)) return false;
        final Object this$content = this.getContent();
        final Object other$content = other.getContent();
        if (this$content == null ? other$content != null : !this$content.equals(other$content)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiGroundingAttribution;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $sourceId = this.getSourceId();
        result = result * PRIME + ($sourceId == null ? 43 : $sourceId.hashCode());
        final Object $content = this.getContent();
        result = result * PRIME + ($content == null ? 43 : $content.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiGroundingAttribution(sourceId=" + this.getSourceId() + ", content=" + this.getContent() + ")";
    }

    public static class GeminiGroundingAttributionBuilder {
        private GeminiAttributionSourceId sourceId;
        private GeminiContent content;

        GeminiGroundingAttributionBuilder() {
        }

        public GeminiGroundingAttributionBuilder sourceId(GeminiAttributionSourceId sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public GeminiGroundingAttributionBuilder content(GeminiContent content) {
            this.content = content;
            return this;
        }

        public GeminiGroundingAttribution build() {
            return new GeminiGroundingAttribution(this.sourceId, this.content);
        }

        public String toString() {
            return "GeminiGroundingAttribution.GeminiGroundingAttributionBuilder(sourceId=" + this.sourceId + ", content=" + this.content + ")";
        }
    }
}
