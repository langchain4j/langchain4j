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
@JsonDeserialize(builder = MistralModerationResult.MistralModerationResultBuilder.class)
public class MistralModerationResult {

    private final MistralCategories categories;
    private final MistralCategoryScores categoryScores;

    public MistralModerationResult(MistralModerationResultBuilder builder) {
        this.categories = builder.categories;
        this.categoryScores = builder.categoryScores;
    }

    public MistralCategories getCategories() {
        return categories;
    }

    public MistralCategoryScores getCategoryScores() {
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
        final MistralModerationResult other = (MistralModerationResult) obj;
        return Objects.equals(this.categories, other.categories)
                && Objects.equals(this.categoryScores, other.categoryScores);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralModerationResult [", "]")
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

        private MistralCategories categories;
        private MistralCategoryScores categoryScores;

        private MistralModerationResultBuilder() {}

        public MistralModerationResultBuilder categories(MistralCategories categories) {
            this.categories = categories;
            return this;
        }

        public MistralModerationResultBuilder categoryScores(MistralCategoryScores categoryScores) {
            this.categoryScores = categoryScores;
            return this;
        }

        public MistralModerationResult build() {
            return new MistralModerationResult(this);
        }
    }
}
