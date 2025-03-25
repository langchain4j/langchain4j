package dev.langchain4j.model.googleai;

import java.util.List;

class GeminiGenerateContentResponse {
    private List<GeminiCandidate> candidates;
    private GeminiPromptFeedback promptFeedback;
    private GeminiUsageMetadata usageMetadata;

    GeminiGenerateContentResponse(List<GeminiCandidate> candidates, GeminiPromptFeedback promptFeedback, GeminiUsageMetadata usageMetadata) {
        this.candidates = candidates;
        this.promptFeedback = promptFeedback;
        this.usageMetadata = usageMetadata;
    }

    public static GeminiGenerateContentResponseBuilder builder() {
        return new GeminiGenerateContentResponseBuilder();
    }

    public List<GeminiCandidate> getCandidates() {
        return this.candidates;
    }

    public GeminiPromptFeedback getPromptFeedback() {
        return this.promptFeedback;
    }

    public GeminiUsageMetadata getUsageMetadata() {
        return this.usageMetadata;
    }

    public void setCandidates(List<GeminiCandidate> candidates) {
        this.candidates = candidates;
    }

    public void setPromptFeedback(GeminiPromptFeedback promptFeedback) {
        this.promptFeedback = promptFeedback;
    }

    public void setUsageMetadata(GeminiUsageMetadata usageMetadata) {
        this.usageMetadata = usageMetadata;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiGenerateContentResponse)) return false;
        final GeminiGenerateContentResponse other = (GeminiGenerateContentResponse) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$candidates = this.getCandidates();
        final Object other$candidates = other.getCandidates();
        if (this$candidates == null ? other$candidates != null : !this$candidates.equals(other$candidates))
            return false;
        final Object this$promptFeedback = this.getPromptFeedback();
        final Object other$promptFeedback = other.getPromptFeedback();
        if (this$promptFeedback == null ? other$promptFeedback != null : !this$promptFeedback.equals(other$promptFeedback))
            return false;
        final Object this$usageMetadata = this.getUsageMetadata();
        final Object other$usageMetadata = other.getUsageMetadata();
        if (this$usageMetadata == null ? other$usageMetadata != null : !this$usageMetadata.equals(other$usageMetadata))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiGenerateContentResponse;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $candidates = this.getCandidates();
        result = result * PRIME + ($candidates == null ? 43 : $candidates.hashCode());
        final Object $promptFeedback = this.getPromptFeedback();
        result = result * PRIME + ($promptFeedback == null ? 43 : $promptFeedback.hashCode());
        final Object $usageMetadata = this.getUsageMetadata();
        result = result * PRIME + ($usageMetadata == null ? 43 : $usageMetadata.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiGenerateContentResponse(candidates=" + this.getCandidates() + ", promptFeedback=" + this.getPromptFeedback() + ", usageMetadata=" + this.getUsageMetadata() + ")";
    }

    public static class GeminiGenerateContentResponseBuilder {
        private List<GeminiCandidate> candidates;
        private GeminiPromptFeedback promptFeedback;
        private GeminiUsageMetadata usageMetadata;

        GeminiGenerateContentResponseBuilder() {
        }

        public GeminiGenerateContentResponseBuilder candidates(List<GeminiCandidate> candidates) {
            this.candidates = candidates;
            return this;
        }

        public GeminiGenerateContentResponseBuilder promptFeedback(GeminiPromptFeedback promptFeedback) {
            this.promptFeedback = promptFeedback;
            return this;
        }

        public GeminiGenerateContentResponseBuilder usageMetadata(GeminiUsageMetadata usageMetadata) {
            this.usageMetadata = usageMetadata;
            return this;
        }

        public GeminiGenerateContentResponse build() {
            return new GeminiGenerateContentResponse(this.candidates, this.promptFeedback, this.usageMetadata);
        }

        public String toString() {
            return "GeminiGenerateContentResponse.GeminiGenerateContentResponseBuilder(candidates=" + this.candidates + ", promptFeedback=" + this.promptFeedback + ", usageMetadata=" + this.usageMetadata + ")";
        }
    }
}
