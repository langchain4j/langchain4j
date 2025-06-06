package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiSafetySetting {
    private GeminiHarmCategory category;
    private GeminiHarmBlockThreshold threshold;

    @JsonCreator
    public GeminiSafetySetting(@JsonProperty("category") GeminiHarmCategory category, @JsonProperty("threshold") GeminiHarmBlockThreshold threshold) {
        this.category = category;
        this.threshold = threshold;
    }

    public GeminiHarmCategory getCategory() {
        return this.category;
    }

    public GeminiHarmBlockThreshold getThreshold() {
        return this.threshold;
    }

    public void setCategory(GeminiHarmCategory category) {
        this.category = category;
    }

    public void setThreshold(GeminiHarmBlockThreshold threshold) {
        this.threshold = threshold;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiSafetySetting)) return false;
        final GeminiSafetySetting other = (GeminiSafetySetting) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$category = this.getCategory();
        final Object other$category = other.getCategory();
        if (this$category == null ? other$category != null : !this$category.equals(other$category)) return false;
        final Object this$threshold = this.getThreshold();
        final Object other$threshold = other.getThreshold();
        if (this$threshold == null ? other$threshold != null : !this$threshold.equals(other$threshold)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiSafetySetting;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $category = this.getCategory();
        result = result * PRIME + ($category == null ? 43 : $category.hashCode());
        final Object $threshold = this.getThreshold();
        result = result * PRIME + ($threshold == null ? 43 : $threshold.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiSafetySetting(category=" + this.getCategory() + ", threshold=" + this.getThreshold() + ")";
    }
}
