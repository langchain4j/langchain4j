package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageMetadata(
        Integer promptTokenCount,
        Integer candidatesTokenCount,
        Integer totalTokenCount,
        Integer thoughtsTokenCount,
        List<PromptTokensDetails> promptTokensDetails) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromptTokensDetails(String modality, Integer tokenCount) {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer promptTokenCount;
        private Integer candidatesTokenCount;
        private Integer totalTokenCount;

        private Builder() {}

        Builder promptTokenCount(Integer promptTokenCount) {
            this.promptTokenCount = promptTokenCount;
            return this;
        }

        Builder candidatesTokenCount(Integer candidatesTokenCount) {
            this.candidatesTokenCount = candidatesTokenCount;
            return this;
        }

        Builder totalTokenCount(Integer totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
            return this;
        }

        UsageMetadata build() {
            return new UsageMetadata(
                    promptTokenCount, candidatesTokenCount, totalTokenCount, null, Collections.emptyList());
        }
    }
}
