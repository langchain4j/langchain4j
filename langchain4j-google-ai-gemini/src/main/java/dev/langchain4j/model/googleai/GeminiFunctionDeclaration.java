package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiFunctionDeclaration(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("parameters") GeminiSchema parameters) {

    static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private String name;
        private String description;
        private GeminiSchema parameters;

        private Builder() {}

        Builder name(String name) {
            this.name = name;
            return this;
        }

        Builder description(String description) {
            this.description = description;
            return this;
        }

        Builder parameters(GeminiSchema parameters) {
            this.parameters = parameters;
            return this;
        }

        GeminiFunctionDeclaration build() {
            return new GeminiFunctionDeclaration(name, description, parameters);
        }
    }
}
