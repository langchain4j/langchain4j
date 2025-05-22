package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiUsageMetadata {
    private Integer promptTokenCount;
    private Integer cachedContentTokenCount;
    private Integer candidatesTokenCount;
    private Integer totalTokenCount;

    @JsonCreator
    GeminiUsageMetadata(@JsonProperty("promptTokenCount") Integer promptTokenCount,
                        @JsonProperty("cachedContentTokenCount") Integer cachedContentTokenCount,
                        @JsonProperty("candidatesTokenCount") Integer candidatesTokenCount,
                        @JsonProperty("totalTokenCount") Integer totalTokenCount) {
        this.promptTokenCount = promptTokenCount;
        this.cachedContentTokenCount = cachedContentTokenCount;
        this.candidatesTokenCount = candidatesTokenCount;
        this.totalTokenCount = totalTokenCount;
    }

    public static GeminiUsageMetadataBuilder builder() {
        return new GeminiUsageMetadataBuilder();
    }

    public Integer getPromptTokenCount() {
        return this.promptTokenCount;
    }

    public Integer getCachedContentTokenCount() {
        return this.cachedContentTokenCount;
    }

    public Integer getCandidatesTokenCount() {
        return this.candidatesTokenCount;
    }

    public Integer getTotalTokenCount() {
        return this.totalTokenCount;
    }

    public void setPromptTokenCount(Integer promptTokenCount) {
        this.promptTokenCount = promptTokenCount;
    }

    public void setCachedContentTokenCount(Integer cachedContentTokenCount) {
        this.cachedContentTokenCount = cachedContentTokenCount;
    }

    public void setCandidatesTokenCount(Integer candidatesTokenCount) {
        this.candidatesTokenCount = candidatesTokenCount;
    }

    public void setTotalTokenCount(Integer totalTokenCount) {
        this.totalTokenCount = totalTokenCount;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiUsageMetadata)) return false;
        final GeminiUsageMetadata other = (GeminiUsageMetadata) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$promptTokenCount = this.getPromptTokenCount();
        final Object other$promptTokenCount = other.getPromptTokenCount();
        if (this$promptTokenCount == null ? other$promptTokenCount != null : !this$promptTokenCount.equals(other$promptTokenCount))
            return false;
        final Object this$cachedContentTokenCount = this.getCachedContentTokenCount();
        final Object other$cachedContentTokenCount = other.getCachedContentTokenCount();
        if (this$cachedContentTokenCount == null ? other$cachedContentTokenCount != null : !this$cachedContentTokenCount.equals(other$cachedContentTokenCount))
            return false;
        final Object this$candidatesTokenCount = this.getCandidatesTokenCount();
        final Object other$candidatesTokenCount = other.getCandidatesTokenCount();
        if (this$candidatesTokenCount == null ? other$candidatesTokenCount != null : !this$candidatesTokenCount.equals(other$candidatesTokenCount))
            return false;
        final Object this$totalTokenCount = this.getTotalTokenCount();
        final Object other$totalTokenCount = other.getTotalTokenCount();
        if (this$totalTokenCount == null ? other$totalTokenCount != null : !this$totalTokenCount.equals(other$totalTokenCount))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiUsageMetadata;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $promptTokenCount = this.getPromptTokenCount();
        result = result * PRIME + ($promptTokenCount == null ? 43 : $promptTokenCount.hashCode());
        final Object $cachedContentTokenCount = this.getCachedContentTokenCount();
        result = result * PRIME + ($cachedContentTokenCount == null ? 43 : $cachedContentTokenCount.hashCode());
        final Object $candidatesTokenCount = this.getCandidatesTokenCount();
        result = result * PRIME + ($candidatesTokenCount == null ? 43 : $candidatesTokenCount.hashCode());
        final Object $totalTokenCount = this.getTotalTokenCount();
        result = result * PRIME + ($totalTokenCount == null ? 43 : $totalTokenCount.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiUsageMetadata(promptTokenCount=" + this.getPromptTokenCount() + ", cachedContentTokenCount=" + this.getCachedContentTokenCount() + ", candidatesTokenCount=" + this.getCandidatesTokenCount() + ", totalTokenCount=" + this.getTotalTokenCount() + ")";
    }

    public static class GeminiUsageMetadataBuilder {
        private Integer promptTokenCount;
        private Integer cachedContentTokenCount;
        private Integer candidatesTokenCount;
        private Integer totalTokenCount;

        GeminiUsageMetadataBuilder() {
        }

        public GeminiUsageMetadataBuilder promptTokenCount(Integer promptTokenCount) {
            this.promptTokenCount = promptTokenCount;
            return this;
        }

        public GeminiUsageMetadataBuilder cachedContentTokenCount(Integer cachedContentTokenCount) {
            this.cachedContentTokenCount = cachedContentTokenCount;
            return this;
        }

        public GeminiUsageMetadataBuilder candidatesTokenCount(Integer candidatesTokenCount) {
            this.candidatesTokenCount = candidatesTokenCount;
            return this;
        }

        public GeminiUsageMetadataBuilder totalTokenCount(Integer totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
            return this;
        }

        public GeminiUsageMetadata build() {
            return new GeminiUsageMetadata(this.promptTokenCount, this.cachedContentTokenCount, this.candidatesTokenCount, this.totalTokenCount);
        }

        public String toString() {
            return "GeminiUsageMetadata.GeminiUsageMetadataBuilder(promptTokenCount=" + this.promptTokenCount + ", cachedContentTokenCount=" + this.cachedContentTokenCount + ", candidatesTokenCount=" + this.candidatesTokenCount + ", totalTokenCount=" + this.totalTokenCount + ")";
        }
    }
}
