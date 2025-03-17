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

@JsonDeserialize(builder = Categories.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class Categories {

    @JsonProperty
    private final Boolean hate;

    @JsonProperty("hate/threatening")
    private final Boolean hateThreatening;

    @JsonProperty("self-harm")
    private final Boolean selfHarm;

    @JsonProperty
    private final Boolean sexual;

    @JsonProperty("sexual/minors")
    private final Boolean sexualMinors;

    @JsonProperty
    private final Boolean violence;

    @JsonProperty("violence/graphic")
    private final Boolean violenceGraphic;

    public Categories(Builder builder) {
        this.hate = builder.hate;
        this.hateThreatening = builder.hateThreatening;
        this.selfHarm = builder.selfHarm;
        this.sexual = builder.sexual;
        this.sexualMinors = builder.sexualMinors;
        this.violence = builder.violence;
        this.violenceGraphic = builder.violenceGraphic;
    }

    public Boolean hate() {
        return hate;
    }

    public Boolean hateThreatening() {
        return hateThreatening;
    }

    public Boolean selfHarm() {
        return selfHarm;
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
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Categories
                && equalTo((Categories) another);
    }

    private boolean equalTo(Categories another) {
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
        return "Categories{"
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

        private Boolean hate;
        private Boolean hateThreatening;
        private Boolean selfHarm;
        private Boolean sexual;
        private Boolean sexualMinors;
        private Boolean violence;
        private Boolean violenceGraphic;

        public Builder hate(Boolean hate) {
            this.hate = hate;
            return this;
        }

        @JsonSetter("hate/threatening")
        public Builder hateThreatening(Boolean hateThreatening) {
            this.hateThreatening = hateThreatening;
            return this;
        }

        @JsonSetter("self-harm")
        public Builder selfHarm(Boolean selfHarm) {
            this.selfHarm = selfHarm;
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
