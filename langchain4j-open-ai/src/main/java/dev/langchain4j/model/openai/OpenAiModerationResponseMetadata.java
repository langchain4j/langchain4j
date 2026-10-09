package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Utils.copy;

import dev.langchain4j.model.moderation.ModerationResponseMetadata;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OpenAiModerationResponseMetadata implements ModerationResponseMetadata {

    private final String id;
    private final String model;
    private final List<OpenAiModerationResultMetadata> results;

    private OpenAiModerationResponseMetadata(Builder builder) {
        this.id = builder.id;
        this.model = builder.model;
        this.results = copy(builder.results);
    }

    public String id() {
        return id;
    }

    public String model() {
        return model;
    }

    public List<OpenAiModerationResultMetadata> results() {
        return results;
    }

    public Builder toBuilder() {
        return builder().id(id).model(model).results(results);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (id != null) {
            map.put("id", id);
        }
        if (model != null) {
            map.put("model", model);
        }
        map.put(
                "results",
                results.stream().map(OpenAiModerationResultMetadata::toMap).toList());
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiModerationResponseMetadata that = (OpenAiModerationResponseMetadata) o;
        return Objects.equals(id, that.id)
                && Objects.equals(model, that.model)
                && Objects.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, model, results);
    }

    @Override
    public String toString() {
        return "OpenAiModerationResponseMetadata{"
                + "id='" + id + '\''
                + ", model='" + model + '\''
                + ", results=" + results
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String id;
        private String model;
        private List<OpenAiModerationResultMetadata> results;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder results(List<OpenAiModerationResultMetadata> results) {
            this.results = results;
            return this;
        }

        public OpenAiModerationResponseMetadata build() {
            return new OpenAiModerationResponseMetadata(this);
        }
    }
}
