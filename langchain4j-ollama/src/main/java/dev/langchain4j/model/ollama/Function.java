package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
class Function {

    private String name;
    private String description;
    private Parameters parameters;

    Function() {
    }

    Function(String name, String description, Parameters parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    static Builder builder() {
        return new Builder();
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    Parameters getParameters() {
        return parameters;
    }

    void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    static class Builder {

        private String name;
        private String description;
        private Parameters parameters;

        Builder name(String name) {
            this.name = name;
            return this;
        }

        Builder description(String description) {
            this.description = description;
            return this;
        }

        Builder parameters(Parameters parameters) {
            this.parameters = parameters;
            return this;
        }

        Function build() {
            return new Function(name, description, parameters);
        }
    }
}
