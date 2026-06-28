package dev.langchain4j.model.responsibleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
class ResponsibleAiEvalResponse {

    @JsonProperty("policy_outcome")
    private PolicyOutcome policyOutcome;

    @JsonProperty("result")
    private EvalResult result;

    @JsonProperty("from_cache")
    private Boolean fromCache;

    @JsonProperty("credits_consumed")
    private Double creditsConsumed;

    public PolicyOutcome getPolicyOutcome() {
        return policyOutcome;
    }

    public void setPolicyOutcome(PolicyOutcome policyOutcome) {
        this.policyOutcome = policyOutcome;
    }

    public EvalResult getResult() {
        return result;
    }

    public void setResult(EvalResult result) {
        this.result = result;
    }

    public RailScore getRailScore() {
        return result != null ? result.getRailScore() : null;
    }

    public void setRailScore(RailScore railScore) {
        if (this.result == null) {
            this.result = new EvalResult();
        }
        this.result.setRailScore(railScore);
    }

    public Map<String, DimensionScore> getDimensionScores() {
        return result != null ? result.getDimensionScores() : null;
    }

    public void setDimensionScores(Map<String, DimensionScore> dimensionScores) {
        if (this.result == null) {
            this.result = new EvalResult();
        }
        this.result.setDimensionScores(dimensionScores);
    }

    public Boolean getFromCache() {
        return fromCache;
    }

    public void setFromCache(Boolean fromCache) {
        this.fromCache = fromCache;
    }

    public Double getCreditsConsumed() {
        return creditsConsumed;
    }

    public void setCreditsConsumed(Double creditsConsumed) {
        this.creditsConsumed = creditsConsumed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RailScore {
        @JsonProperty("score")
        private Double score;

        @JsonProperty("confidence")
        private Double confidence;

        @JsonProperty("summary")
        private String summary;

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DimensionScore {
        @JsonProperty("score")
        private Double score;

        @JsonProperty("confidence")
        private Double confidence;

        @JsonProperty("explanation")
        private String explanation;

        @JsonProperty("issues")
        private List<String> issues;

        @JsonProperty("suggestions")
        private List<String> suggestions;

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }

        public List<String> getIssues() {
            return issues;
        }

        public void setIssues(List<String> issues) {
            this.issues = issues;
        }

        public List<String> getSuggestions() {
            return suggestions;
        }

        public void setSuggestions(List<String> suggestions) {
            this.suggestions = suggestions;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PolicyOutcome {
        @JsonProperty("enforced")
        private Boolean enforced;

        @JsonProperty("enforcement")
        private String enforcement;

        @JsonProperty("threshold")
        private Double threshold;

        @JsonProperty("score")
        private Double score;

        @JsonProperty("passed")
        private Boolean passed;

        public Boolean getEnforced() {
            return enforced;
        }

        public void setEnforced(Boolean enforced) {
            this.enforced = enforced;
        }

        public String getEnforcement() {
            return enforcement;
        }

        public void setEnforcement(String enforcement) {
            this.enforcement = enforcement;
        }

        public Double getThreshold() {
            return threshold;
        }

        public void setThreshold(Double threshold) {
            this.threshold = threshold;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public Boolean getPassed() {
            return passed;
        }

        public void setPassed(Boolean passed) {
            this.passed = passed;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EvalResult {
        @JsonProperty("rail_score")
        private RailScore railScore;

        @JsonProperty("dimension_scores")
        private Map<String, DimensionScore> dimensionScores;

        @JsonProperty("explanation")
        private String explanation;

        @JsonProperty("issues")
        private List<Issue> issues;

        @JsonProperty("improvement_suggestions")
        private List<String> improvementSuggestions;

        public RailScore getRailScore() {
            return railScore;
        }

        public void setRailScore(RailScore railScore) {
            this.railScore = railScore;
        }

        public Map<String, DimensionScore> getDimensionScores() {
            return dimensionScores;
        }

        public void setDimensionScores(Map<String, DimensionScore> dimensionScores) {
            this.dimensionScores = dimensionScores;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }

        public List<Issue> getIssues() {
            return issues;
        }

        public void setIssues(List<Issue> issues) {
            this.issues = issues;
        }

        public List<String> getImprovementSuggestions() {
            return improvementSuggestions;
        }

        public void setImprovementSuggestions(List<String> improvementSuggestions) {
            this.improvementSuggestions = improvementSuggestions;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Issue {
        @JsonProperty("description")
        private String description;

        @JsonProperty("dimension")
        private String dimension;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDimension() {
            return dimension;
        }

        public void setDimension(String dimension) {
            this.dimension = dimension;
        }
    }
}
