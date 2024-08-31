package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class CompletionRequest {

    private String model;
    private String system;
    private String prompt;
    private Options options;
    private String format;
    private Boolean stream;

    CompletionRequest() {
    }

    CompletionRequest(String model, String system, String prompt, Options options, String format, Boolean stream) {
        this.model = model;
        this.system = system;
        this.prompt = prompt;
        this.options = options;
        this.format = format;
        this.stream = stream;
    }

    static Builder builder() {
        return new Builder();
    }

    String getModel() {
        return model;
    }

    void setModel(String model) {
        this.model = model;
    }

    String getSystem() {
        return system;
    }

    void setSystem(String system) {
        this.system = system;
    }

    String getPrompt() {
        return prompt;
    }

    void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    Options getOptions() {
        return options;
    }

    void setOptions(Options options) {
        this.options = options;
    }

    String getFormat() {
        return format;
    }

    void setFormat(String format) {
        this.format = format;
    }

    Boolean getStream() {
        return stream;
    }

    void setStream(Boolean stream) {
        this.stream = stream;
    }

    static class Builder {

        private String model;
        private String system;
        private String prompt;
        private Options options;
        private String format;
        private Boolean stream;

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder system(String system) {
            this.system = system;
            return this;
        }

        Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        Builder options(Options options) {
            this.options = options;
            return this;
        }

        Builder format(String format) {
            this.format = format;
            return this;
        }

        Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        CompletionRequest build() {
            return new CompletionRequest(model, system, prompt, options, format, stream);
        }
    }
}
