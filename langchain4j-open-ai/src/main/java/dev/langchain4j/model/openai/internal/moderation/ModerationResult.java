package dev.langchain4j.model.openai.internal.moderation;

import static dev.langchain4j.internal.Utils.copy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.List;
import java.util.Map;
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
    private final Map<String, List<String>> categoryAppliedInputTypes;

    @JsonProperty
    private final Boolean flagged;

    public ModerationResult(Builder builder) {
        this.categories = builder.categories;
        this.categoryScores = builder.categoryScores;
        this.categoryAppliedInputTypes = copy(builder.categoryAppliedInputTypes);
        this.flagged = builder.flagged;
    }

    public Categories categories() {
        return categories;
    }

    public CategoryScores categoryScores() {
        return categoryScores;
    }

    public Map<String, List<String>> categoryAppliedInputTypes() {
        return categoryAppliedInputTypes;
    }

    public Boolean isFlagged() {
        return flagged;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ModerationResult && equalTo((ModerationResult) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(ModerationResult another) {
        return Objects.equals(categories, another.categories)
                && Objects.equals(categoryScores, another.categoryScores)
                && Objects.equals(categoryAppliedInputTypes, another.categoryAppliedInputTypes)
                && Objects.equals(flagged, another.flagged);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(categories);
        h += (h << 5) + Objects.hashCode(categoryScores);
        h += (h << 5) + Objects.hashCode(categoryAppliedInputTypes);
        h += (h << 5) + Objects.hashCode(flagged);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "ModerationResult{"
                + "categories=" + categories
                + ", categoryScores=" + categoryScores
                + ", categoryAppliedInputTypes=" + categoryAppliedInputTypes
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
        private Map<String, List<String>> categoryAppliedInputTypes;
        private Boolean flagged;

        public Builder categories(Categories categories) {
            this.categories = categories;
            return this;
        }

        public Builder categoryScores(CategoryScores categoryScores) {
            this.categoryScores = categoryScores;
            return this;
        }

        public Builder categoryAppliedInputTypes(Map<String, List<String>> categoryAppliedInputTypes) {
            this.categoryAppliedInputTypes = categoryAppliedInputTypes;
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
