package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonDeserialize(builder = MistralAiCategoryScores.MistralCategoryScoresBuilder.class)
public class MistralAiCategoryScores {

    private Double sexual;
    private Double hateAndDiscrimination;
    private Double violenceAndThreats;
    private Double dangerousAndCriminalContent;
    private Double selfHarm;
    private Double health;
    private Double law;
    private Double pii;

    private MistralAiCategoryScores(MistralCategoryScoresBuilder builder) {
        this.sexual = builder.sexual;
        this.hateAndDiscrimination = builder.hateAndDiscrimination;
        this.violenceAndThreats = builder.violenceAndThreats;
        this.dangerousAndCriminalContent = builder.dangerousAndCriminalContent;
        this.selfHarm = builder.selfHarm;
        this.health = builder.health;
        this.law = builder.law;
        this.pii = builder.pii;
    }

    public Double getSexual() {
        return sexual;
    }

    public Double getHateAndDiscrimination() {
        return hateAndDiscrimination;
    }

    public Double getViolenceAndThreats() {
        return violenceAndThreats;
    }

    public Double getDangerousAndCriminalContent() {
        return dangerousAndCriminalContent;
    }

    public Double getSelfHarm() {
        return selfHarm;
    }

    public Double getHealth() {
        return health;
    }

    public Double getLaw() {
        return law;
    }

    public Double getPii() {
        return pii;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.sexual);
        hash = 37 * hash + Objects.hashCode(this.hateAndDiscrimination);
        hash = 37 * hash + Objects.hashCode(this.violenceAndThreats);
        hash = 37 * hash + Objects.hashCode(this.dangerousAndCriminalContent);
        hash = 37 * hash + Objects.hashCode(this.selfHarm);
        hash = 37 * hash + Objects.hashCode(this.health);
        hash = 37 * hash + Objects.hashCode(this.law);
        hash = 37 * hash + Objects.hashCode(this.pii);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        final MistralAiCategoryScores other = (MistralAiCategoryScores) obj;
        return Objects.equals(this.sexual, other.sexual)
                && Objects.equals(this.hateAndDiscrimination, other.hateAndDiscrimination)
                && Objects.equals(this.violenceAndThreats, other.violenceAndThreats)
                && Objects.equals(this.dangerousAndCriminalContent, other.dangerousAndCriminalContent)
                && Objects.equals(this.selfHarm, other.selfHarm)
                && Objects.equals(this.health, other.health)
                && Objects.equals(this.law, other.law)
                && Objects.equals(this.pii, other.pii);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiCategoryScores [", "]")
                .add("sexual=" + this.getSexual())
                .add("hateAndDiscrimination=" + this.getHateAndDiscrimination())
                .add("violenceAndThreats=" + this.getViolenceAndThreats())
                .add("dangerousAndCriminalContent=" + this.getDangerousAndCriminalContent())
                .add("selfHarm=" + this.getSelfHarm())
                .add("health=" + this.getHealth())
                .add("law=" + this.getLaw())
                .add("pii=" + this.getPii())
                .toString();
    }

    public static MistralCategoryScoresBuilder builder() {
        return new MistralCategoryScoresBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class MistralCategoryScoresBuilder {

        private Double sexual;
        private Double hateAndDiscrimination;
        private Double violenceAndThreats;
        private Double dangerousAndCriminalContent;
        private Double selfHarm;
        private Double health;
        private Double law;
        private Double pii;

        private MistralCategoryScoresBuilder() {}

        public MistralCategoryScoresBuilder sexual(Double sexual) {
            this.sexual = sexual;
            return this;
        }

        public MistralCategoryScoresBuilder hateAndDiscrimination(Double hateAndDiscrimination) {
            this.hateAndDiscrimination = hateAndDiscrimination;
            return this;
        }

        public MistralCategoryScoresBuilder violenceAndThreats(Double violenceAndThreats) {
            this.violenceAndThreats = violenceAndThreats;
            return this;
        }

        public MistralCategoryScoresBuilder dangerousAndCriminalContent(Double dangerousAndCriminalContent) {
            this.dangerousAndCriminalContent = dangerousAndCriminalContent;
            return this;
        }

        public MistralCategoryScoresBuilder selfharm(Double selfHarm) {
            this.selfHarm = selfHarm;
            return this;
        }

        public MistralCategoryScoresBuilder health(Double health) {
            this.health = health;
            return this;
        }

        public MistralCategoryScoresBuilder law(Double law) {
            this.law = law;
            return this;
        }

        public MistralCategoryScoresBuilder pii(Double pii) {
            this.pii = pii;
            return this;
        }

        public MistralAiCategoryScores build() {
            return new MistralAiCategoryScores(this);
        }
    }
}
