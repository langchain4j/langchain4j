package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiPromptFeedback {
    private GeminiBlockReason blockReason;
    private List<GeminiSafetyRating> safetyRatings;

    @JsonCreator
    GeminiPromptFeedback(@JsonProperty("blockReason") GeminiBlockReason blockReason, @JsonProperty("safetyRatings") List<GeminiSafetyRating> safetyRatings) {
        this.blockReason = blockReason;
        this.safetyRatings = safetyRatings;
    }

    public static GeminiPromptFeedbackBuilder builder() {
        return new GeminiPromptFeedbackBuilder();
    }

    public GeminiBlockReason getBlockReason() {
        return this.blockReason;
    }

    public List<GeminiSafetyRating> getSafetyRatings() {
        return this.safetyRatings;
    }

    public void setBlockReason(GeminiBlockReason blockReason) {
        this.blockReason = blockReason;
    }

    public void setSafetyRatings(List<GeminiSafetyRating> safetyRatings) {
        this.safetyRatings = safetyRatings;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiPromptFeedback)) return false;
        final GeminiPromptFeedback other = (GeminiPromptFeedback) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$blockReason = this.getBlockReason();
        final Object other$blockReason = other.getBlockReason();
        if (this$blockReason == null ? other$blockReason != null : !this$blockReason.equals(other$blockReason))
            return false;
        final Object this$safetyRatings = this.getSafetyRatings();
        final Object other$safetyRatings = other.getSafetyRatings();
        if (this$safetyRatings == null ? other$safetyRatings != null : !this$safetyRatings.equals(other$safetyRatings))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiPromptFeedback;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $blockReason = this.getBlockReason();
        result = result * PRIME + ($blockReason == null ? 43 : $blockReason.hashCode());
        final Object $safetyRatings = this.getSafetyRatings();
        result = result * PRIME + ($safetyRatings == null ? 43 : $safetyRatings.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiPromptFeedback(blockReason=" + this.getBlockReason() + ", safetyRatings=" + this.getSafetyRatings() + ")";
    }

    public static class GeminiPromptFeedbackBuilder {
        private GeminiBlockReason blockReason;
        private List<GeminiSafetyRating> safetyRatings;

        GeminiPromptFeedbackBuilder() {
        }

        public GeminiPromptFeedbackBuilder blockReason(GeminiBlockReason blockReason) {
            this.blockReason = blockReason;
            return this;
        }

        public GeminiPromptFeedbackBuilder safetyRatings(List<GeminiSafetyRating> safetyRatings) {
            this.safetyRatings = safetyRatings;
            return this;
        }

        public GeminiPromptFeedback build() {
            return new GeminiPromptFeedback(this.blockReason, this.safetyRatings);
        }

        public String toString() {
            return "GeminiPromptFeedback.GeminiPromptFeedbackBuilder(blockReason=" + this.blockReason + ", safetyRatings=" + this.safetyRatings + ")";
        }
    }
}
