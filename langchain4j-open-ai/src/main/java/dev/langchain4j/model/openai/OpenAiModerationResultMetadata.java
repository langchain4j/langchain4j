package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Utils.copy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OpenAiModerationResultMetadata {

    private final String text;
    private final Map<String, Boolean> categories;
    private final Map<String, Double> categoryScores;
    private final Map<String, List<String>> categoryAppliedInputTypes;

    private OpenAiModerationResultMetadata(Builder builder) {
        this.text = builder.text;
        this.categories = copy(builder.categories);
        this.categoryScores = copy(builder.categoryScores);
        this.categoryAppliedInputTypes = copy(builder.categoryAppliedInputTypes);
    }

    public String text() {
        return text;
    }

    public Map<String, Boolean> categories() {
        return categories;
    }

    public Map<String, Double> categoryScores() {
        return categoryScores;
    }

    public Map<String, List<String>> categoryAppliedInputTypes() {
        return categoryAppliedInputTypes;
    }

    public Builder toBuilder() {
        return builder()
                .text(text)
                .categories(categories)
                .categoryScores(categoryScores)
                .categoryAppliedInputTypes(categoryAppliedInputTypes);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (text != null) {
            map.put("text", text);
        }
        map.put("categories", categories);
        map.put("categoryScores", categoryScores);
        map.put("categoryAppliedInputTypes", categoryAppliedInputTypes);
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiModerationResultMetadata that = (OpenAiModerationResultMetadata) o;
        return Objects.equals(text, that.text)
                && Objects.equals(categories, that.categories)
                && Objects.equals(categoryScores, that.categoryScores)
                && Objects.equals(categoryAppliedInputTypes, that.categoryAppliedInputTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, categories, categoryScores, categoryAppliedInputTypes);
    }

    @Override
    public String toString() {
        return "OpenAiModerationResultMetadata{"
                + "text='" + text + '\''
                + ", categories=" + categories
                + ", categoryScores=" + categoryScores
                + ", categoryAppliedInputTypes=" + categoryAppliedInputTypes
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String text;
        private Map<String, Boolean> categories;
        private Map<String, Double> categoryScores;
        private Map<String, List<String>> categoryAppliedInputTypes;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder categories(Map<String, Boolean> categories) {
            this.categories = categories;
            return this;
        }

        public Builder categoryScores(Map<String, Double> categoryScores) {
            this.categoryScores = categoryScores;
            return this;
        }

        public Builder categoryAppliedInputTypes(Map<String, List<String>> categoryAppliedInputTypes) {
            this.categoryAppliedInputTypes = categoryAppliedInputTypes;
            return this;
        }

        public OpenAiModerationResultMetadata build() {
            return new OpenAiModerationResultMetadata(this);
        }
    }
}
