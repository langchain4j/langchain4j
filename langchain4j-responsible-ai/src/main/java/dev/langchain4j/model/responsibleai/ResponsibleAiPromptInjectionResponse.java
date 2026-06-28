package dev.langchain4j.model.responsibleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * Maps the actual flat API response from /railscore/v1/agent/prompt-injection.
 * Provides getResult() for backwards-compatibility with test code.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponsibleAiPromptInjectionResponse {

    @JsonProperty("injection_detected")
    private Boolean injectionDetected;

    @JsonProperty("attack_type")
    private String attackType;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    @JsonProperty("payload_preview")
    private String payloadPreview;

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("credits_consumed")
    private Double creditsConsumed;

    public Boolean getInjectionDetected() { return injectionDetected; }
    public void setInjectionDetected(Boolean injectionDetected) { this.injectionDetected = injectionDetected; }

    public String getAttackType() { return attackType; }
    public void setAttackType(String attackType) { this.attackType = attackType; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }

    public String getPayloadPreview() { return payloadPreview; }
    public void setPayloadPreview(String payloadPreview) { this.payloadPreview = payloadPreview; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Double getCreditsConsumed() { return creditsConsumed; }
    public void setCreditsConsumed(Double creditsConsumed) { this.creditsConsumed = creditsConsumed; }

    /**
     * Builds a PromptInjectionResult from flat API response fields.
     * Returns null only if no data was returned (injectionDetected is null).
     */
    public PromptInjectionResult getResult() {
        if (injectionDetected == null && confidence == null) {
            return null;
        }
        PromptInjectionResult r = new PromptInjectionResult();
        r.setInjectionDetected(injectionDetected);
        r.setRiskScore(confidence);           // API uses "confidence" for risk score
        r.setRiskLevel(severity);             // API uses "severity" for risk level
        r.setAttackTypes(attackType != null ? Collections.singletonList(attackType) : null);
        r.setRecommendation(recommendedAction);
        return r;
    }

    /** Adapter class used by test code via getResult(). */
    public static class PromptInjectionResult {
        private Boolean injectionDetected;
        private Double riskScore;
        private String riskLevel;
        private List<String> attackTypes;
        private String recommendation;

        public Boolean getInjectionDetected() { return injectionDetected; }
        public void setInjectionDetected(Boolean injectionDetected) { this.injectionDetected = injectionDetected; }
        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public List<String> getAttackTypes() { return attackTypes; }
        public void setAttackTypes(List<String> attackTypes) { this.attackTypes = attackTypes; }
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    }
}
