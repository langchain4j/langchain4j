package dev.langchain4j.model.openai.internal.moderation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = CategoryScores.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CategoryScores {

    @JsonProperty
    private final Double hate;

    @JsonProperty("hate/threatening")
    private final Double hateThreatening;

    @JsonProperty("self-harm")
    private final Double selfHarm;

    @JsonProperty
    private final Double sexual;

    @JsonProperty("sexual/minors")
    private final Double sexualMinors;

    @JsonProperty
    private final Double violence;

    @JsonProperty("violence/graphic")
    private final Double violenceGraphic;

    public CategoryScores(Builder builder) {
        this.hate = builder.hate;
        this.hateThreatening = builder.hateThreatening;
        this.selfHarm = builder.selfHarm;
        this.sexual = builder.sexual;
        this.sexualMinors = builder.sexualMinors;
        this.violence = builder.violence;
        this.violenceGraphic = builder.violenceGraphic;
    }

    public Double hate() {
        return hate;
    }

    public Double hateThreatening() {
        return hateThreatening;
    }

    public Double selfHarm() {
        return selfHarm;
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
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof CategoryScores
                && equalTo((CategoryScores) another);
    }

    private boolean equalTo(CategoryScores another) {
        return Objects.equals(hate, another.hate)
                && Objects.equals(hateThreatening, another.hateThreatening)
                && Objects.equals(selfHarm, another.selfHarm)
                && Objects.equals(sexual, another.sexual)
                && Objects.equals(sexualMinors, another.sexualMinors)
                && Objects.equals(violence, another.violence)
                && Objects.equals(violenceGraphic, another.violenceGraphic);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(hate);
        h += (h << 5) + Objects.hashCode(hateThreatening);
        h += (h << 5) + Objects.hashCode(selfHarm);
        h += (h << 5) + Objects.hashCode(sexual);
        h += (h << 5) + Objects.hashCode(sexualMinors);
        h += (h << 5) + Objects.hashCode(violence);
        h += (h << 5) + Objects.hashCode(violenceGraphic);
        return h;
    }

    @Override
    public String toString() {
        return "CategoryScores{"
                + "hate=" + hate
                + ", hateThreatening=" + hateThreatening
                + ", selfHarm=" + selfHarm
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

        private Double hate;
        private Double hateThreatening;
        private Double selfHarm;
        private Double sexual;
        private Double sexualMinors;
        private Double violence;
        private Double violenceGraphic;

        public Builder hate(Double hate) {
            this.hate = hate;
            return this;
        }

        @JsonSetter("hate/threatening")
        public Builder hateThreatening(Double hateThreatening) {
            this.hateThreatening = hateThreatening;
            return this;
        }

        @JsonSetter("self-harm")
        public Builder selfHarm(Double selfHarm) {
            this.selfHarm = selfHarm;
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
