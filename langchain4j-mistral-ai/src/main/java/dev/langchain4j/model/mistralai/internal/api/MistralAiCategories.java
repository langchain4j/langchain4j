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
@JsonDeserialize(builder = MistralAiCategories.MistralCategoriesBuilder.class)
public class MistralAiCategories {

    private final Boolean sexual;
    private final Boolean hateAndDiscrimination;
    private final Boolean violenceAndThreats;
    private final Boolean dangerousAndCriminalContent;
    private final Boolean selfHarm;
    private final Boolean health;
    private final Boolean law;
    private final Boolean pii;

    private MistralAiCategories(MistralCategoriesBuilder builder) {
        this.selfHarm = builder.selfharm;
        this.sexual = builder.sexual;
        this.hateAndDiscrimination = builder.hateAndDiscrimination;
        this.violenceAndThreats = builder.violenceAndThreats;
        this.dangerousAndCriminalContent = builder.dangerousAndCriminalContent;
        this.health = builder.health;
        this.law = builder.law;
        this.pii = builder.pii;
    }

    public Boolean getSexual() {
        return sexual;
    }

    public Boolean getHateAndDiscrimination() {
        return hateAndDiscrimination;
    }

    public Boolean getViolenceAndThreats() {
        return violenceAndThreats;
    }

    public Boolean getDangerousAndCriminalContent() {
        return dangerousAndCriminalContent;
    }

    public Boolean getSelfHarm() {
        return selfHarm;
    }

    public Boolean getHealth() {
        return health;
    }

    public Boolean getLaw() {
        return law;
    }

    public Boolean getPii() {
        return pii;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 67 * hash + Objects.hashCode(this.sexual);
        hash = 67 * hash + Objects.hashCode(this.hateAndDiscrimination);
        hash = 67 * hash + Objects.hashCode(this.violenceAndThreats);
        hash = 67 * hash + Objects.hashCode(this.dangerousAndCriminalContent);
        hash = 67 * hash + Objects.hashCode(this.selfHarm);
        hash = 67 * hash + Objects.hashCode(this.health);
        hash = 67 * hash + Objects.hashCode(this.law);
        hash = 67 * hash + Objects.hashCode(this.pii);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MistralAiCategories other = (MistralAiCategories) obj;
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
        return new StringJoiner(", ", "MistralAiCategories [", "]")
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

    public static MistralCategoriesBuilder builder() {
        return new MistralCategoriesBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class MistralCategoriesBuilder {

        private Boolean sexual;
        private Boolean hateAndDiscrimination;
        private Boolean violenceAndThreats;
        private Boolean dangerousAndCriminalContent;
        private Boolean selfharm;
        private Boolean health;
        private Boolean law;
        private Boolean pii;

        private MistralCategoriesBuilder() {}

        public MistralCategoriesBuilder sexual(Boolean sexual) {
            this.sexual = sexual;
            return this;
        }

        public Boolean getHateAndDiscrimination() {
            return hateAndDiscrimination;
        }

        public MistralCategoriesBuilder hateAndDiscrimination(Boolean hateAndDiscrimination) {
            this.hateAndDiscrimination = hateAndDiscrimination;
            return this;
        }

        public MistralCategoriesBuilder violenceAndThreats(Boolean violenceAndThreats) {
            this.violenceAndThreats = violenceAndThreats;
            return this;
        }

        public MistralCategoriesBuilder dangerousAndCriminalContent(Boolean dangerousAndCriminalContent) {
            this.dangerousAndCriminalContent = dangerousAndCriminalContent;
            return this;
        }

        public MistralCategoriesBuilder selfharm(Boolean selfHarm) {
            this.selfharm = selfHarm;
            return this;
        }

        public MistralCategoriesBuilder health(Boolean health) {
            this.health = health;
            return this;
        }

        public MistralCategoriesBuilder law(Boolean law) {
            this.law = law;
            return this;
        }

        public MistralCategoriesBuilder pii(Boolean pii) {
            this.pii = pii;
            return this;
        }

        public MistralAiCategories build() {
            return new MistralAiCategories(this);
        }
    }
}
