package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MistralModerationResult {

    @JsonProperty("categories")
    private MistralCategories categories;

    @JsonProperty("category_scores")
    private MistralCategoryScores categoryScores;

    public MistralCategories getCategories() {
        return categories;
    }

    public void setCategories(MistralCategories categories) {
        this.categories = categories;
    }

    public MistralCategoryScores getCategoryScores() {
        return categoryScores;
    }

    public void setCategoryScores(MistralCategoryScores categoryScores) {
        this.categoryScores = categoryScores;
    }
}
