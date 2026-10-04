package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * A function the model can call.
 *
 * <p>The parameters are described either by {@code parameters}, the typed {@link GeminiSchema}, or by
 * {@code parametersJsonSchema}, plain JSON Schema. The API treats the two as mutually exclusive, so
 * at most one of them is set, and neither is when the function takes no parameters. A null field is
 * omitted by the module's object mapper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiFunctionDeclaration(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("parameters") GeminiSchema parameters,
        @JsonProperty("parametersJsonSchema") Map<String, Object> parametersJsonSchema) {

    static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private String name;
        private String description;
        private GeminiSchema parameters;
        private Map<String, Object> parametersJsonSchema;

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

        Builder parametersJsonSchema(Map<String, Object> parametersJsonSchema) {
            this.parametersJsonSchema = parametersJsonSchema;
            return this;
        }

        GeminiFunctionDeclaration build() {
            return new GeminiFunctionDeclaration(name, description, parameters, parametersJsonSchema);
        }
    }
}
