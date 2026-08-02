package dev.langchain4j.model.openai.internal.moderation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.Objects;

@JsonDeserialize(builder = CategoryScores.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CategoryScores {

    @JsonProperty
    private final Double harassment;

    @JsonProperty("harassment/threatening")
    private final Double harassmentThreatening;

    @JsonProperty
    private final Double hate;

    @JsonProperty("hate/threatening")
    private final Double hateThreatening;

    @JsonProperty
    private final Double illicit;

    @JsonProperty("illicit/violent")
    private final Double illicitViolent;

    @JsonProperty("self-harm")
    private final Double selfHarm;

    @JsonProperty("self-harm/intent")
    private final Double selfHarmIntent;

    @JsonProperty("self-harm/instructions")
    private final Double selfHarmInstructions;

    @JsonProperty
    private final Double sexual;

    @JsonProperty("sexual/minors")
    private final Double sexualMinors;

    @JsonProperty
    private final Double violence;

    @JsonProperty("violence/graphic")
    private final Double violenceGraphic;

    public CategoryScores(Builder builder) {
        this.harassment = builder.harassment;
        this.harassmentThreatening = builder.harassmentThreatening;
        this.hate = builder.hate;
        this.hateThreatening = builder.hateThreatening;
        this.illicit = builder.illicit;
        this.illicitViolent = builder.illicitViolent;
        this.selfHarm = builder.selfHarm;
        this.selfHarmIntent = builder.selfHarmIntent;
        this.selfHarmInstructions = builder.selfHarmInstructions;
        this.sexual = builder.sexual;
        this.sexualMinors = builder.sexualMinors;
        this.violence = builder.violence;
        this.violenceGraphic = builder.violenceGraphic;
    }

    public Double harassment() {
        return harassment;
    }

    public Double harassmentThreatening() {
        return harassmentThreatening;
    }

    public Double hate() {
        return hate;
    }

    public Double hateThreatening() {
        return hateThreatening;
    }

    public Double illicit() {
        return illicit;
    }

    public Double illicitViolent() {
        return illicitViolent;
    }

    public Double selfHarm() {
        return selfHarm;
    }

    public Double selfHarmIntent() {
        return selfHarmIntent;
    }

    public Double selfHarmInstructions() {
        return selfHarmInstructions;
    }

    public Double sexual() {
        return sexual;
    }

    public Double sexualMinors() {
        return sexualMinors;
    }

    public Double violence() {
        return violence;
    }

    public Double violenceGraphic() {
        return violenceGraphic;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof CategoryScores && equalTo((CategoryScores) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(CategoryScores another) {
        return Objects.equals(harassment, another.harassment)
                && Objects.equals(harassmentThreatening, another.harassmentThreatening)
                && Objects.equals(hate, another.hate)
                && Objects.equals(hateThreatening, another.hateThreatening)
                && Objects.equals(illicit, another.illicit)
                && Objects.equals(illicitViolent, another.illicitViolent)
                && Objects.equals(selfHarm, another.selfHarm)
                && Objects.equals(selfHarmIntent, another.selfHarmIntent)
                && Objects.equals(selfHarmInstructions, another.selfHarmInstructions)
                && Objects.equals(sexual, another.sexual)
                && Objects.equals(sexualMinors, another.sexualMinors)
                && Objects.equals(violence, another.violence)
                && Objects.equals(violenceGraphic, another.violenceGraphic);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(harassment);
        h += (h << 5) + Objects.hashCode(harassmentThreatening);
        h += (h << 5) + Objects.hashCode(hate);
        h += (h << 5) + Objects.hashCode(hateThreatening);
        h += (h << 5) + Objects.hashCode(illicit);
        h += (h << 5) + Objects.hashCode(illicitViolent);
        h += (h << 5) + Objects.hashCode(selfHarm);
        h += (h << 5) + Objects.hashCode(selfHarmIntent);
        h += (h << 5) + Objects.hashCode(selfHarmInstructions);
        h += (h << 5) + Objects.hashCode(sexual);
        h += (h << 5) + Objects.hashCode(sexualMinors);
        h += (h << 5) + Objects.hashCode(violence);
        h += (h << 5) + Objects.hashCode(violenceGraphic);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "CategoryScores{"
                + "harassment=" + harassment
                + ", harassmentThreatening=" + harassmentThreatening
                + ", hate=" + hate
                + ", hateThreatening=" + hateThreatening
                + ", illicit=" + illicit
                + ", illicitViolent=" + illicitViolent
                + ", selfHarm=" + selfHarm
                + ", selfHarmIntent=" + selfHarmIntent
                + ", selfHarmInstructions=" + selfHarmInstructions
                + ", sexual=" + sexual
                + ", sexualMinors=" + sexualMinors
                + ", violence=" + violence
                + ", violenceGraphic=" + violenceGraphic
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private Double harassment;
        private Double harassmentThreatening;
        private Double hate;
        private Double hateThreatening;
        private Double illicit;
        private Double illicitViolent;
        private Double selfHarm;
        private Double selfHarmIntent;
        private Double selfHarmInstructions;
        private Double sexual;
        private Double sexualMinors;
        private Double violence;
        private Double violenceGraphic;

        public Builder harassment(Double harassment) {
            this.harassment = harassment;
            return this;
        }

        @JsonSetter("harassment/threatening")
        public Builder harassmentThreatening(Double harassmentThreatening) {
            this.harassmentThreatening = harassmentThreatening;
            return this;
        }

        public Builder hate(Double hate) {
            this.hate = hate;
            return this;
        }

        @JsonSetter("hate/threatening")
        public Builder hateThreatening(Double hateThreatening) {
            this.hateThreatening = hateThreatening;
            return this;
        }

        public Builder illicit(Double illicit) {
            this.illicit = illicit;
            return this;
        }

        @JsonSetter("illicit/violent")
        public Builder illicitViolent(Double illicitViolent) {
            this.illicitViolent = illicitViolent;
            return this;
        }

        @JsonSetter("self-harm")
        public Builder selfHarm(Double selfHarm) {
            this.selfHarm = selfHarm;
            return this;
        }

        @JsonSetter("self-harm/intent")
        public Builder selfHarmIntent(Double selfHarmIntent) {
            this.selfHarmIntent = selfHarmIntent;
            return this;
        }

        @JsonSetter("self-harm/instructions")
        public Builder selfHarmInstructions(Double selfHarmInstructions) {
            this.selfHarmInstructions = selfHarmInstructions;
            return this;
        }

        public Builder sexual(Double sexual) {
            this.sexual = sexual;
            return this;
        }

        @JsonSetter("sexual/minors")
        public Builder sexualMinors(Double sexualMinors) {
            this.sexualMinors = sexualMinors;
            return this;
        }

        public Builder violence(Double violence) {
            this.violence = violence;
            return this;
        }

        @JsonSetter("violence/graphic")
        public Builder violenceGraphic(Double violenceGraphic) {
            this.violenceGraphic = violenceGraphic;
            return this;
        }

        public CategoryScores build() {
            return new CategoryScores(this);
        }
    }
}
