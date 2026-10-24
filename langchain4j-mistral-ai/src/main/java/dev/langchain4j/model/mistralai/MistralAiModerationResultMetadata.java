package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.Utils.copy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class MistralAiModerationResultMetadata {

    private final String text;
    private final Map<String, Boolean> categories;
    private final Map<String, Double> categoryScores;

    private MistralAiModerationResultMetadata(Builder builder) {
        this.text = builder.text;
        this.categories = copy(builder.categories);
        this.categoryScores = copy(builder.categoryScores);
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

    public Builder toBuilder() {
        return builder().text(text).categories(categories).categoryScores(categoryScores);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (text != null) {
            map.put("text", text);
        }
        map.put("categories", categories);
        map.put("categoryScores", categoryScores);
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MistralAiModerationResultMetadata that = (MistralAiModerationResultMetadata) o;
        return Objects.equals(text, that.text)
                && Objects.equals(categories, that.categories)
                && Objects.equals(categoryScores, that.categoryScores);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, categories, categoryScores);
    }

    @Override
    public String toString() {
        return "MistralAiModerationResultMetadata{"
                + "text='" + text + '\''
                + ", categories=" + categories
                + ", categoryScores=" + categoryScores
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String text;
        private Map<String, Boolean> categories;
        private Map<String, Double> categoryScores;

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

        public MistralAiModerationResultMetadata build() {
            return new MistralAiModerationResultMetadata(this);
        }
    }
}
