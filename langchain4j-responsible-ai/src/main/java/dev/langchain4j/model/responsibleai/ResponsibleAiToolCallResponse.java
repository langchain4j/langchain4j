package dev.langchain4j.model.responsibleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Maps the actual flat API response from /railscore/v1/agent/tool-call.
 * Provides getResult() for backwards-compatibility with test code.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponsibleAiToolCallResponse {

    @JsonProperty("decision")
    private String decision;

    @JsonProperty("decision_reason")
    private String decisionReason;

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("credits_consumed")
    private Double creditsConsumed;

    @JsonProperty("rail_score")
    private RailScore railScore;

    @JsonProperty("policy")
    private Policy policy;

    @JsonProperty("dimension_scores")
    private Map<String, DimensionScore> dimensionScores;

    @JsonProperty("context_signals")
    private ContextSignals contextSignals;

    @JsonProperty("compliance_violations")
    private List<String> complianceViolations;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Double getCreditsConsumed() {
        return creditsConsumed;
    }

    public void setCreditsConsumed(Double creditsConsumed) {
        this.creditsConsumed = creditsConsumed;
    }

    public RailScore getRailScore() {
        return railScore;
    }

    public void setRailScore(RailScore railScore) {
        this.railScore = railScore;
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public Map<String, DimensionScore> getDimensionScores() {
        return dimensionScores;
    }

    public void setDimensionScores(Map<String, DimensionScore> dimensionScores) {
        this.dimensionScores = dimensionScores;
    }

    public ContextSignals getContextSignals() {
        return contextSignals;
    }

    public void setContextSignals(ContextSignals contextSignals) {
        this.contextSignals = contextSignals;
    }

    public List<String> getComplianceViolations() {
        return complianceViolations;
    }

    public void setComplianceViolations(List<String> complianceViolations) {
        this.complianceViolations = complianceViolations;
    }

    /**
     * Builds a ToolCallResult from the flat API response fields.
     * Returns null only if no meaningful data was returned (decision is null).
     */
    public ToolCallResult getResult() {
        if (decision == null && railScore == null) {
            return null;
        }
        ToolCallResult r = new ToolCallResult();
        r.setSafe("ALLOW".equalsIgnoreCase(decision));
        r.setRiskLevel(contextSignals != null ? contextSignals.getToolRiskLevel() : null);
        r.setRiskScore(railScore != null ? railScore.getScore() : null);
        r.setExplanation(decisionReason != null ? decisionReason : (railScore != null ? railScore.getSummary() : null));
        r.setRecommendation(policy != null ? policy.getAppliedRule() : null);
        return r;
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
    public static class Policy {
        @JsonProperty("applied_rule")
        private String appliedRule;

        @JsonProperty("source")
        private String source;

        @JsonProperty("violated_dimensions")
        private List<String> violatedDimensions;

        public String getAppliedRule() {
            return appliedRule;
        }

        public void setAppliedRule(String appliedRule) {
            this.appliedRule = appliedRule;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public List<String> getViolatedDimensions() {
            return violatedDimensions;
        }

        public void setViolatedDimensions(List<String> v) {
            this.violatedDimensions = v;
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
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContextSignals {
        @JsonProperty("tool_risk_level")
        private String toolRiskLevel;

        @JsonProperty("surveillance_risk")
        private Boolean surveillanceRisk;

        @JsonProperty("high_stakes_domain")
        private Boolean highStakesDomain;

        public String getToolRiskLevel() {
            return toolRiskLevel;
        }

        public void setToolRiskLevel(String toolRiskLevel) {
            this.toolRiskLevel = toolRiskLevel;
        }

        public Boolean getSurveillanceRisk() {
            return surveillanceRisk;
        }

        public void setSurveillanceRisk(Boolean surveillanceRisk) {
            this.surveillanceRisk = surveillanceRisk;
        }

        public Boolean getHighStakesDomain() {
            return highStakesDomain;
        }

        public void setHighStakesDomain(Boolean highStakesDomain) {
            this.highStakesDomain = highStakesDomain;
        }
    }

    /** Adapter class used by test code via getResult(). */
    public static class ToolCallResult {
        private Boolean safe;
        private String riskLevel;
        private Double riskScore;
        private String explanation;
        private String recommendation;

        public Boolean getSafe() {
            return safe;
        }

        public void setSafe(Boolean safe) {
            this.safe = safe;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public Double getRiskScore() {
            return riskScore;
        }

        public void setRiskScore(Double riskScore) {
            this.riskScore = riskScore;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }
    }
}
