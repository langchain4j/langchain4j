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
@JsonDeserialize(builder = MistralAiModerationResult.MistralModerationResultBuilder.class)
public class MistralAiModerationResult {

    private final MistralAiCategories categories;
    private final MistralAiCategoryScores categoryScores;

    public MistralAiModerationResult(MistralModerationResultBuilder builder) {
        this.categories = builder.categories;
        this.categoryScores = builder.categoryScores;
    }

    public MistralAiCategories getCategories() {
        return categories;
    }

    public MistralAiCategoryScores getCategoryScores() {
        return categoryScores;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + Objects.hashCode(this.categories);
        hash = 83 * hash + Objects.hashCode(this.categoryScores);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        final MistralAiModerationResult other = (MistralAiModerationResult) obj;
        return Objects.equals(this.categories, other.categories)
                && Objects.equals(this.categoryScores, other.categoryScores);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiModerationResult [", "]")
                .add("categories=" + this.getCategories())
                .add("categoryScores=" + this.getCategoryScores())
                .toString();
    }

    public static MistralModerationResultBuilder builder() {
        return new MistralModerationResultBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class MistralModerationResultBuilder {

        private MistralAiCategories categories;
        private MistralAiCategoryScores categoryScores;

        private MistralModerationResultBuilder() {}

        public MistralModerationResultBuilder categories(MistralAiCategories categories) {
            this.categories = categories;
            return this;
        }

        public MistralModerationResultBuilder categoryScores(MistralAiCategoryScores categoryScores) {
            this.categoryScores = categoryScores;
            return this;
        }

        public MistralAiModerationResult build() {
            return new MistralAiModerationResult(this);
        }
    }
}
