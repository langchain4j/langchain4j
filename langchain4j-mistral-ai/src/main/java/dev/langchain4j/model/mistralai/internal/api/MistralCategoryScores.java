package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MistralCategoryScores {

    @JsonProperty
    private Double sexual;

    @JsonProperty("hate_and_discrimination")
    private Double hateAndDiscrimination;

    @JsonProperty("violence_and_threats")
    private Double violenceAndThreats;

    @JsonProperty("dangerous_and_criminal_content")
    private Double dangerousAndCriminalContent;

    @JsonProperty("selfharm")
    private Double selfHarm;

    @JsonProperty
    private Double health;

    @JsonProperty
    private Double law;

    @JsonProperty
    private Double pii;

    public Double getSexual() {
        return sexual;
    }

    public void setSexual(Double sexual) {
        this.sexual = sexual;
    }

    public Double getHateAndDiscrimination() {
        return hateAndDiscrimination;
    }

    public void setHateAndDiscrimination(Double hateAndDiscrimination) {
        this.hateAndDiscrimination = hateAndDiscrimination;
    }

    public Double getViolenceAndThreats() {
        return violenceAndThreats;
    }

    public void setViolenceAndThreats(Double violenceAndThreats) {
        this.violenceAndThreats = violenceAndThreats;
    }

    public Double getDangerousAndCriminalContent() {
        return dangerousAndCriminalContent;
    }

    public void setDangerousAndCriminalContent(Double dangerousAndCriminalContent) {
        this.dangerousAndCriminalContent = dangerousAndCriminalContent;
    }

    public Double getSelfHarm() {
        return selfHarm;
    }

    public void setSelfHarm(Double selfHarm) {
        this.selfHarm = selfHarm;
    }

    public Double getHealth() {
        return health;
    }

    public void setHealth(Double health) {
        this.health = health;
    }

    public Double getLaw() {
        return law;
    }

    public void setLaw(Double law) {
        this.law = law;
    }

    public Double getPii() {
        return pii;
    }

    public void setPii(Double pii) {
        this.pii = pii;
    }
}
