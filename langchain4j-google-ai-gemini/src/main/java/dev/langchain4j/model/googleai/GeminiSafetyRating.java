package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiSafetyRating {
    private GeminiHarmCategory category;
    private GeminiHarmBlockThreshold threshold;
    private Boolean blocked;

    @JsonCreator
    GeminiSafetyRating(@JsonProperty("category") GeminiHarmCategory category,
                       @JsonProperty("threshold") GeminiHarmBlockThreshold threshold,
                       @JsonProperty("blocked") Boolean blocked) {
        this.category = category;
        this.threshold = threshold;
        this.blocked = blocked;
    }

    public static GeminiSafetyRatingBuilder builder() {
        return new GeminiSafetyRatingBuilder();
    }

    public GeminiHarmCategory getCategory() {
        return this.category;
    }

    public GeminiHarmBlockThreshold getThreshold() {
        return this.threshold;
    }

    public Boolean getBlocked() {
        return this.blocked;
    }

    public void setCategory(GeminiHarmCategory category) {
        this.category = category;
    }

    public void setThreshold(GeminiHarmBlockThreshold threshold) {
        this.threshold = threshold;
    }

    public void setBlocked(Boolean blocked) {
        this.blocked = blocked;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiSafetyRating)) return false;
        final GeminiSafetyRating other = (GeminiSafetyRating) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$category = this.getCategory();
        final Object other$category = other.getCategory();
        if (this$category == null ? other$category != null : !this$category.equals(other$category)) return false;
        final Object this$threshold = this.getThreshold();
        final Object other$threshold = other.getThreshold();
        if (this$threshold == null ? other$threshold != null : !this$threshold.equals(other$threshold)) return false;
        final Object this$blocked = this.getBlocked();
        final Object other$blocked = other.getBlocked();
        if (this$blocked == null ? other$blocked != null : !this$blocked.equals(other$blocked)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiSafetyRating;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $category = this.getCategory();
        result = result * PRIME + ($category == null ? 43 : $category.hashCode());
        final Object $threshold = this.getThreshold();
        result = result * PRIME + ($threshold == null ? 43 : $threshold.hashCode());
        final Object $blocked = this.getBlocked();
        result = result * PRIME + ($blocked == null ? 43 : $blocked.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiSafetyRating(category=" + this.getCategory() + ", threshold=" + this.getThreshold() + ", blocked=" + this.getBlocked() + ")";
    }

    public static class GeminiSafetyRatingBuilder {
        private GeminiHarmCategory category;
        private GeminiHarmBlockThreshold threshold;
        private Boolean blocked;

        GeminiSafetyRatingBuilder() {
        }

        public GeminiSafetyRatingBuilder category(GeminiHarmCategory category) {
            this.category = category;
            return this;
        }

        public GeminiSafetyRatingBuilder threshold(GeminiHarmBlockThreshold threshold) {
            this.threshold = threshold;
            return this;
        }

        public GeminiSafetyRatingBuilder blocked(Boolean blocked) {
            this.blocked = blocked;
            return this;
        }

        public GeminiSafetyRating build() {
            return new GeminiSafetyRating(this.category, this.threshold, this.blocked);
        }

        public String toString() {
            return "GeminiSafetyRating.GeminiSafetyRatingBuilder(category=" + this.category + ", threshold=" + this.threshold + ", blocked=" + this.blocked + ")";
        }
    }
}
