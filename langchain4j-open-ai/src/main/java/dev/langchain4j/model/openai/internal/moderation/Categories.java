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

@JsonDeserialize(builder = Categories.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class Categories {

    @JsonProperty
    private final Boolean harassment;

    @JsonProperty("harassment/threatening")
    private final Boolean harassmentThreatening;

    @JsonProperty
    private final Boolean hate;

    @JsonProperty("hate/threatening")
    private final Boolean hateThreatening;

    @JsonProperty
    private final Boolean illicit;

    @JsonProperty("illicit/violent")
    private final Boolean illicitViolent;

    @JsonProperty("self-harm")
    private final Boolean selfHarm;

    @JsonProperty("self-harm/intent")
    private final Boolean selfHarmIntent;

    @JsonProperty("self-harm/instructions")
    private final Boolean selfHarmInstructions;

    @JsonProperty
    private final Boolean sexual;

    @JsonProperty("sexual/minors")
    private final Boolean sexualMinors;

    @JsonProperty
    private final Boolean violence;

    @JsonProperty("violence/graphic")
    private final Boolean violenceGraphic;

    public Categories(Builder builder) {
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

    public Boolean harassment() {
        return harassment;
    }

    public Boolean harassmentThreatening() {
        return harassmentThreatening;
    }

    public Boolean hate() {
        return hate;
    }

    public Boolean hateThreatening() {
        return hateThreatening;
    }

    public Boolean illicit() {
        return illicit;
    }

    public Boolean illicitViolent() {
        return illicitViolent;
    }

    public Boolean selfHarm() {
        return selfHarm;
    }

    public Boolean selfHarmIntent() {
        return selfHarmIntent;
    }

    public Boolean selfHarmInstructions() {
        return selfHarmInstructions;
    }

    public Boolean sexual() {
        return sexual;
    }

    public Boolean sexualMinors() {
        return sexualMinors;
    }

    public Boolean violence() {
        return violence;
    }

    public Boolean violenceGraphic() {
        return violenceGraphic;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Categories && equalTo((Categories) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(Categories another) {
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
        return "Categories{"
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

        private Boolean harassment;
        private Boolean harassmentThreatening;
        private Boolean hate;
        private Boolean hateThreatening;
        private Boolean illicit;
        private Boolean illicitViolent;
        private Boolean selfHarm;
        private Boolean selfHarmIntent;
        private Boolean selfHarmInstructions;
        private Boolean sexual;
        private Boolean sexualMinors;
        private Boolean violence;
        private Boolean violenceGraphic;

        public Builder harassment(Boolean harassment) {
            this.harassment = harassment;
            return this;
        }

        @JsonSetter("harassment/threatening")
        public Builder harassmentThreatening(Boolean harassmentThreatening) {
            this.harassmentThreatening = harassmentThreatening;
            return this;
        }

        public Builder hate(Boolean hate) {
            this.hate = hate;
            return this;
        }

        @JsonSetter("hate/threatening")
        public Builder hateThreatening(Boolean hateThreatening) {
            this.hateThreatening = hateThreatening;
            return this;
        }

        public Builder illicit(Boolean illicit) {
            this.illicit = illicit;
            return this;
        }

        @JsonSetter("illicit/violent")
        public Builder illicitViolent(Boolean illicitViolent) {
            this.illicitViolent = illicitViolent;
            return this;
        }

        @JsonSetter("self-harm")
        public Builder selfHarm(Boolean selfHarm) {
            this.selfHarm = selfHarm;
            return this;
        }

        @JsonSetter("self-harm/intent")
        public Builder selfHarmIntent(Boolean selfHarmIntent) {
            this.selfHarmIntent = selfHarmIntent;
            return this;
        }

        @JsonSetter("self-harm/instructions")
        public Builder selfHarmInstructions(Boolean selfHarmInstructions) {
            this.selfHarmInstructions = selfHarmInstructions;
            return this;
        }

        public Builder sexual(Boolean sexual) {
            this.sexual = sexual;
            return this;
        }

        @JsonSetter("sexual/minors")
        public Builder sexualMinors(Boolean sexualMinors) {
            this.sexualMinors = sexualMinors;
            return this;
        }

        public Builder violence(Boolean violence) {
            this.violence = violence;
            return this;
        }

        @JsonSetter("violence/graphic")
        public Builder violenceGraphic(Boolean violenceGraphic) {
            this.violenceGraphic = violenceGraphic;
            return this;
        }

        public Categories build() {
            return new Categories(this);
        }
    }
}
