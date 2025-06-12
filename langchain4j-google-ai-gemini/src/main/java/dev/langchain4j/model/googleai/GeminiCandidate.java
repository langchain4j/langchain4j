package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiCandidate {
    private GeminiContent content;
    private GeminiFinishReason finishReason;
    private List<GeminiSafetySetting> safetySettings;
    private GeminiCitationMetadata citationMetadata;
    private Integer tokenCount; //TODO check why token count for candidate seems to be zero or absent
    private List<GeminiGroundingAttribution> groundingAttributions;
    private Integer index;

    @JsonCreator
    GeminiCandidate(
            @JsonProperty("content") GeminiContent content,
            @JsonProperty("finishReason") GeminiFinishReason finishReason,
            @JsonProperty("safetySettings") List<GeminiSafetySetting> safetySettings,
            @JsonProperty("citationMetadata") GeminiCitationMetadata citationMetadata,
            @JsonProperty("tokenCount") Integer tokenCount,
            @JsonProperty("groundingAttributions") List<GeminiGroundingAttribution> groundingAttributions,
            @JsonProperty("index") Integer index) {
        this.content = content;
        this.finishReason = finishReason;
        this.safetySettings = safetySettings;
        this.citationMetadata = citationMetadata;
        this.tokenCount = tokenCount;
        this.groundingAttributions = groundingAttributions;
        this.index = index;
    }

    public static GeminiCandidateBuilder builder() {
        return new GeminiCandidateBuilder();
    }

    public GeminiContent getContent() {
        return this.content;
    }

    public GeminiFinishReason getFinishReason() {
        return this.finishReason;
    }

    public List<GeminiSafetySetting> getSafetySettings() {
        return this.safetySettings;
    }

    public GeminiCitationMetadata getCitationMetadata() {
        return this.citationMetadata;
    }

    public Integer getTokenCount() {
        return this.tokenCount;
    }

    public List<GeminiGroundingAttribution> getGroundingAttributions() {
        return this.groundingAttributions;
    }

    public Integer getIndex() {
        return this.index;
    }

    public void setContent(GeminiContent content) {
        this.content = content;
    }

    public void setFinishReason(GeminiFinishReason finishReason) {
        this.finishReason = finishReason;
    }

    public void setSafetySettings(List<GeminiSafetySetting> safetySettings) {
        this.safetySettings = safetySettings;
    }

    public void setCitationMetadata(GeminiCitationMetadata citationMetadata) {
        this.citationMetadata = citationMetadata;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public void setGroundingAttributions(List<GeminiGroundingAttribution> groundingAttributions) {
        this.groundingAttributions = groundingAttributions;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiCandidate)) return false;
        final GeminiCandidate other = (GeminiCandidate) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$content = this.getContent();
        final Object other$content = other.getContent();
        if (this$content == null ? other$content != null : !this$content.equals(other$content)) return false;
        final Object this$finishReason = this.getFinishReason();
        final Object other$finishReason = other.getFinishReason();
        if (this$finishReason == null ? other$finishReason != null : !this$finishReason.equals(other$finishReason))
            return false;
        final Object this$safetySettings = this.getSafetySettings();
        final Object other$safetySettings = other.getSafetySettings();
        if (this$safetySettings == null ? other$safetySettings != null : !this$safetySettings.equals(other$safetySettings))
            return false;
        final Object this$citationMetadata = this.getCitationMetadata();
        final Object other$citationMetadata = other.getCitationMetadata();
        if (this$citationMetadata == null ? other$citationMetadata != null : !this$citationMetadata.equals(other$citationMetadata))
            return false;
        final Object this$tokenCount = this.getTokenCount();
        final Object other$tokenCount = other.getTokenCount();
        if (this$tokenCount == null ? other$tokenCount != null : !this$tokenCount.equals(other$tokenCount))
            return false;
        final Object this$groundingAttributions = this.getGroundingAttributions();
        final Object other$groundingAttributions = other.getGroundingAttributions();
        if (this$groundingAttributions == null ? other$groundingAttributions != null : !this$groundingAttributions.equals(other$groundingAttributions))
            return false;
        final Object this$index = this.getIndex();
        final Object other$index = other.getIndex();
        if (this$index == null ? other$index != null : !this$index.equals(other$index)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiCandidate;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $content = this.getContent();
        result = result * PRIME + ($content == null ? 43 : $content.hashCode());
        final Object $finishReason = this.getFinishReason();
        result = result * PRIME + ($finishReason == null ? 43 : $finishReason.hashCode());
        final Object $safetySettings = this.getSafetySettings();
        result = result * PRIME + ($safetySettings == null ? 43 : $safetySettings.hashCode());
        final Object $citationMetadata = this.getCitationMetadata();
        result = result * PRIME + ($citationMetadata == null ? 43 : $citationMetadata.hashCode());
        final Object $tokenCount = this.getTokenCount();
        result = result * PRIME + ($tokenCount == null ? 43 : $tokenCount.hashCode());
        final Object $groundingAttributions = this.getGroundingAttributions();
        result = result * PRIME + ($groundingAttributions == null ? 43 : $groundingAttributions.hashCode());
        final Object $index = this.getIndex();
        result = result * PRIME + ($index == null ? 43 : $index.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiCandidate(content=" + this.getContent() + ", finishReason=" + this.getFinishReason() + ", safetySettings=" + this.getSafetySettings() + ", citationMetadata=" + this.getCitationMetadata() + ", tokenCount=" + this.getTokenCount() + ", groundingAttributions=" + this.getGroundingAttributions() + ", index=" + this.getIndex() + ")";
    }

    public static class GeminiCandidateBuilder {
        private GeminiContent content;
        private GeminiFinishReason finishReason;
        private List<GeminiSafetySetting> safetySettings;
        private GeminiCitationMetadata citationMetadata;
        private Integer tokenCount;
        private List<GeminiGroundingAttribution> groundingAttributions;
        private Integer index;

        GeminiCandidateBuilder() {
        }

        public GeminiCandidateBuilder content(GeminiContent content) {
            this.content = content;
            return this;
        }

        public GeminiCandidateBuilder finishReason(GeminiFinishReason finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public GeminiCandidateBuilder safetySettings(List<GeminiSafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        public GeminiCandidateBuilder citationMetadata(GeminiCitationMetadata citationMetadata) {
            this.citationMetadata = citationMetadata;
            return this;
        }

        public GeminiCandidateBuilder tokenCount(Integer tokenCount) {
            this.tokenCount = tokenCount;
            return this;
        }

        public GeminiCandidateBuilder groundingAttributions(List<GeminiGroundingAttribution> groundingAttributions) {
            this.groundingAttributions = groundingAttributions;
            return this;
        }

        public GeminiCandidateBuilder index(Integer index) {
            this.index = index;
            return this;
        }

        public GeminiCandidate build() {
            return new GeminiCandidate(this.content, this.finishReason, this.safetySettings, this.citationMetadata, this.tokenCount, this.groundingAttributions, this.index);
        }

        public String toString() {
            return "GeminiCandidate.GeminiCandidateBuilder(content=" + this.content + ", finishReason=" + this.finishReason + ", safetySettings=" + this.safetySettings + ", citationMetadata=" + this.citationMetadata + ", tokenCount=" + this.tokenCount + ", groundingAttributions=" + this.groundingAttributions + ", index=" + this.index + ")";
        }
    }
}
