package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class OllamaModelCard {

    private String modelfile;
    private String parameters;
    private String template;
    private OllamaModelDetails details;

    OllamaModelCard() {
    }

    OllamaModelCard(String modelfile, String parameters, String template, OllamaModelDetails details) {
        this.modelfile = modelfile;
        this.parameters = parameters;
        this.template = template;
        this.details = details;
    }

    static Builder builder() {
        return new Builder();
    }

    public String getModelfile() {
        return modelfile;
    }

    public void setModelfile(String modelfile) {
        this.modelfile = modelfile;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public OllamaModelDetails getDetails() {
        return details;
    }

    public void setDetails(OllamaModelDetails details) {
        this.details = details;
    }

    static class Builder {

        private String modelfile;
        private String parameters;
        private String template;
        private OllamaModelDetails details;

        Builder modelfile(String modelfile) {
            this.modelfile = modelfile;
            return this;
        }

        Builder parameters(String parameters) {
            this.parameters = parameters;
            return this;
        }

        Builder template(String template) {
            this.template = template;
            return this;
        }

        Builder details(OllamaModelDetails details) {
            this.details = details;
            return this;
        }

        OllamaModelCard build() {
            return new OllamaModelCard(modelfile, parameters, template, details);
        }
    }
}
