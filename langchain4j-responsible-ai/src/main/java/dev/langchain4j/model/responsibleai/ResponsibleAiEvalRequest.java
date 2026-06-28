package dev.langchain4j.model.responsibleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
class ResponsibleAiEvalRequest {

    @JsonProperty("content")
    private final String content;

    @JsonProperty("mode")
    private final String mode;

    @JsonProperty("dimensions")
    private final List<String> dimensions;

    @JsonProperty("weights")
    private final Map<String, Double> weights;

    @JsonProperty("domain")
    private final String domain;

    @JsonProperty("include_explanations")
    private final Boolean includeExplanations;

    @JsonProperty("include_issues")
    private final Boolean includeIssues;

    @JsonProperty("include_suggestions")
    private final Boolean includeSuggestions;

    private ResponsibleAiEvalRequest(Builder builder) {
        this.content = builder.content;
        this.mode = builder.mode;
        this.dimensions = builder.dimensions;
        this.weights = builder.weights;
        this.domain = builder.domain;
        this.includeExplanations = builder.includeExplanations;
        this.includeIssues = builder.includeIssues;
        this.includeSuggestions = builder.includeSuggestions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String content;
        private String mode;
        private List<String> dimensions;
        private Map<String, Double> weights;
        private String domain;
        private Boolean includeExplanations;
        private Boolean includeIssues;
        private Boolean includeSuggestions;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder dimensions(List<String> dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder weights(Map<String, Double> weights) {
            this.weights = weights;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder includeExplanations(Boolean includeExplanations) {
            this.includeExplanations = includeExplanations;
            return this;
        }

        public Builder includeIssues(Boolean includeIssues) {
            this.includeIssues = includeIssues;
            return this;
        }

        public Builder includeSuggestions(Boolean includeSuggestions) {
            this.includeSuggestions = includeSuggestions;
            return this;
        }

        public ResponsibleAiEvalRequest build() {
            return new ResponsibleAiEvalRequest(this);
        }
    }
}
