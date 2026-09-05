package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GeminiSafetySetting {

    @JsonProperty
    private GeminiHarmCategory category;

    @JsonProperty
    private GeminiHarmBlockThreshold threshold;

    public GeminiSafetySetting(GeminiHarmCategory category, GeminiHarmBlockThreshold threshold) {
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
}
