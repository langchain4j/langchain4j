package dev.langchain4j.model.mistralai.internal.api;

public class MistralModerationResult {


    private MistralCategories categories;
    private MistralCategoryScores categoryScores;
    private Boolean flagged;

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

    public Boolean getFlagged() {
        return flagged;
    }

    public void setFlagged(Boolean flagged) {
        this.flagged = flagged;
    }
}
