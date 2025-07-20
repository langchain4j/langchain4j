package dev.langchain4j.model.openai.internal.moderation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = ModerationResult.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ModerationResult {

    @JsonProperty
    private final Categories categories;
    @JsonProperty
    private final CategoryScores categoryScores;
    @JsonProperty
    private final Boolean flagged;

    public ModerationResult(Builder builder) {
        this.categories = builder.categories;
        this.categoryScores = builder.categoryScores;
        this.flagged = builder.flagged;
    }

    public Categories categories() {
        return categories;
    }

    public CategoryScores categoryScores() {
        return categoryScores;
    }

    public Boolean isFlagged() {
        return flagged;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ModerationResult
                && equalTo((ModerationResult) another);
    }

    private boolean equalTo(ModerationResult another) {
        return Objects.equals(categories, another.categories)
                && Objects.equals(categoryScores, another.categoryScores)
                && Objects.equals(flagged, another.flagged);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(categories);
        h += (h << 5) + Objects.hashCode(categoryScores);
        h += (h << 5) + Objects.hashCode(flagged);
        return h;
    }

    @Override
    public String toString() {
        return "ModerationResult{"
                + "categories=" + categories
                + ", categoryScores=" + categoryScores
                + ", flagged=" + flagged
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private Categories categories;
        private CategoryScores categoryScores;
        private Boolean flagged;

        public Builder categories(Categories categories) {
            this.categories = categories;
            return this;
        }

        public Builder categoryScores(CategoryScores categoryScores) {
            this.categoryScores = categoryScores;
            return this;
        }

        public Builder flagged(Boolean flagged) {
            this.flagged = flagged;
            return this;
        }

        public ModerationResult build() {
            return new ModerationResult(this);
        }
    }
}
