package dev.langchain4j.model.responsibleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponsibleAiToolResultResponse {

    @JsonProperty("result")
    private ToolResult result;

    @JsonProperty("credits_consumed")
    private Double creditsConsumed;

    @JsonProperty("pii_detected")
    private PiiDetectedInfo piiDetectedInfo;

    @JsonProperty("prompt_injection")
    private InjectionDetectedInfo injectionDetectedInfo;

    @JsonProperty("recommended_action")
    private String recommendation;

    public ToolResult getResult() {
        if (result != null) {
            return result;
        }

        if (piiDetectedInfo == null
                && injectionDetectedInfo == null
                && recommendation == null) {
            return null;
        }

        ToolResult flatResult = new ToolResult();

        if (piiDetectedInfo != null) {
            flatResult.setPiiDetected(piiDetectedInfo.getDetected());
            flatResult.setPiiTypes(piiDetectedInfo.getPiiTypes());
            flatResult.setRedactedResult(piiDetectedInfo.getRedactedResult());
        } else {
            flatResult.setPiiDetected(false);
        }

        if (injectionDetectedInfo != null) {
            flatResult.setInjectionDetected(injectionDetectedInfo.getDetected());
        } else {
            flatResult.setInjectionDetected(false);
        }

        flatResult.setRecommendation(recommendation);

        return flatResult;
    }

    public void setResult(ToolResult result) {
        this.result = result;
    }

    public Double getCreditsConsumed() {
        return creditsConsumed;
    }

    public void setCreditsConsumed(Double creditsConsumed) {
        this.creditsConsumed = creditsConsumed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolResult {

        @JsonProperty("pii_detected")
        private Boolean piiDetected;

        @JsonProperty("injection_detected")
        private Boolean injectionDetected;

        @JsonProperty("pii_types")
        private List<String> piiTypes;

        @JsonProperty("redacted_result")
        private String redactedResult;

        @JsonProperty("recommendation")
        private String recommendation;

        public Boolean getPiiDetected() {
            return piiDetected;
        }

        public void setPiiDetected(Boolean piiDetected) {
            this.piiDetected = piiDetected;
        }

        public Boolean getInjectionDetected() {
            return injectionDetected;
        }

        public void setInjectionDetected(Boolean injectionDetected) {
            this.injectionDetected = injectionDetected;
        }

        public List<String> getPiiTypes() {
            return piiTypes;
        }

        public void setPiiTypes(List<String> piiTypes) {
            this.piiTypes = piiTypes;
        }

        public String getRedactedResult() {
            return redactedResult;
        }

        public void setRedactedResult(String redactedResult) {
            this.redactedResult = redactedResult;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PiiDetectedInfo {

        @JsonProperty("found")
        private Boolean detected;

        @JsonProperty("entities")
        private List<PiiEntity> entities;

        @JsonProperty("redacted_result")
        private String redactedResult;

        public Boolean getDetected() {
            return detected;
        }

        public void setDetected(Boolean detected) {
            this.detected = detected;
        }

        public List<String> getPiiTypes() {
            if (entities == null) {
                return null;
            }

            return entities.stream()
                    .map(PiiEntity::getType)
                    .collect(Collectors.toList());
        }

        public String getRedactedResult() {
            return redactedResult;
        }

        public void setRedactedResult(String redactedResult) {
            this.redactedResult = redactedResult;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PiiEntity {

        @JsonProperty("type")
        private String type;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InjectionDetectedInfo {

        @JsonProperty("detected")
        private Boolean detected;

        public Boolean getDetected() {
            return detected;
        }

        public void setDetected(Boolean detected) {
            this.detected = detected;
        }
    }
}