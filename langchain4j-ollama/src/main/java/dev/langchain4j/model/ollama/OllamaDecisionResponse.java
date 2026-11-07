package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
class OllamaDecisionResponse {

    private String model;
    private Map<String, Answer> answers;
    private Usage usage;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Map<String, Answer> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, Answer> answers) {
        this.answers = answers;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Answer {

        private String type;
        private Double noul;
        private String choice;
        private Double score;
        private Map<String, Double> probabilities;
        private Double confidence;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Double getNoul() {
            return noul;
        }

        public void setNoul(Double noul) {
            this.noul = noul;
        }

        public String getChoice() {
            return choice;
        }

        public void setChoice(String choice) {
            this.choice = choice;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public Map<String, Double> getProbabilities() {
            return probabilities;
        }

        public void setProbabilities(Map<String, Double> probabilities) {
            this.probabilities = probabilities;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Usage {

        private Integer inputTokens;
        private Integer outputTokens;

        public Integer getInputTokens() {
            return inputTokens;
        }

        public void setInputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
        }

        public Integer getOutputTokens() {
            return outputTokens;
        }

        public void setOutputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
        }
    }
}
