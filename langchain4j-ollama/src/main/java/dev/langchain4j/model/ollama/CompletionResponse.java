package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class CompletionResponse {

    private String model;
    private String createdAt;
    private String response;
    private Boolean done;
    private Integer promptEvalCount;
    private Integer evalCount;

    CompletionResponse() {
    }

    CompletionResponse(String model, String createdAt, String response, Boolean done, Integer promptEvalCount, Integer evalCount) {
        this.model = model;
        this.createdAt = createdAt;
        this.response = response;
        this.done = done;
        this.promptEvalCount = promptEvalCount;
        this.evalCount = evalCount;
    }

    String getModel() {
        return model;
    }

    void setModel(String model) {
        this.model = model;
    }

    String getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    String getResponse() {
        return response;
    }

    void setResponse(String response) {
        this.response = response;
    }

    Boolean getDone() {
        return done;
    }

    void setDone(Boolean done) {
        this.done = done;
    }

    Integer getPromptEvalCount() {
        return promptEvalCount;
    }

    void setPromptEvalCount(Integer promptEvalCount) {
        this.promptEvalCount = promptEvalCount;
    }

    Integer getEvalCount() {
        return evalCount;
    }

    void setEvalCount(Integer evalCount) {
        this.evalCount = evalCount;
    }

    static class Builder {

        private String model;
        private String createdAt;
        private String response;
        private Boolean done;
        private Integer promptEvalCount;
        private Integer evalCount;

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder createdAt(String createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        Builder response(String response) {
            this.response = response;
            return this;
        }

        Builder done(Boolean done) {
            this.done = done;
            return this;
        }

        Builder promptEvalCount(Integer promptEvalCount) {
            this.promptEvalCount = promptEvalCount;
            return this;
        }

        Builder evalCount(Integer evalCount) {
            this.evalCount = evalCount;
            return this;
        }

        CompletionResponse build() {
            return new CompletionResponse(model, createdAt, response, done, promptEvalCount, evalCount);
        }
    }
}
