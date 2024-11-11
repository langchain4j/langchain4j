package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MistralCategories {

    @JsonProperty
    private Boolean sexual;

    @JsonProperty("hate_and_discrimination")
    private Boolean hateAndDiscrimination;

    @JsonProperty("violence_and_threats")
    private Boolean violenceAndThreats;

    @JsonProperty("dangerous_and_criminal_content")
    private Boolean dangerousAndCriminalContent;

    @JsonProperty("selfharm")
    private Boolean selfHarm;

    @JsonProperty
    private Boolean health;

    @JsonProperty
    private Boolean law;

    @JsonProperty
    private Boolean pii;

    public Boolean getSexual() {
        return sexual;
    }

    public void setSexual(Boolean sexual) {
        this.sexual = sexual;
    }

    public Boolean getHateAndDiscrimination() {
        return hateAndDiscrimination;
    }

    public void setHateAndDiscrimination(Boolean hateAndDiscrimination) {
        this.hateAndDiscrimination = hateAndDiscrimination;
    }

    public Boolean getViolenceAndThreats() {
        return violenceAndThreats;
    }

    public void setViolenceAndThreats(Boolean violenceAndThreats) {
        this.violenceAndThreats = violenceAndThreats;
    }

    public Boolean getDangerousAndCriminalContent() {
        return dangerousAndCriminalContent;
    }

    public void setDangerousAndCriminalContent(Boolean dangerousAndCriminalContent) {
        this.dangerousAndCriminalContent = dangerousAndCriminalContent;
    }

    public Boolean getSelfHarm() {
        return selfHarm;
    }

    public void setSelfHarm(Boolean selfHarm) {
        this.selfHarm = selfHarm;
    }

    public Boolean getHealth() {
        return health;
    }

    public void setHealth(Boolean health) {
        this.health = health;
    }

    public Boolean getLaw() {
        return law;
    }

    public void setLaw(Boolean law) {
        this.law = law;
    }

    public Boolean getPii() {
        return pii;
    }

    public void setPii(Boolean pii) {
        this.pii = pii;
    }
}
