package dev.langchain4j.model.ollama;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class CompletionResponse {

    private String model;
    private String createdAt;
    private String response;
    private String image;
    private Boolean done;
    private Integer promptEvalCount;
    private Integer evalCount;

    CompletionResponse() {}

    CompletionResponse(
            String model,
            String createdAt,
            String response,
            String image,
            Boolean done,
            Integer promptEvalCount,
            Integer evalCount) {
        this.model = model;
        this.createdAt = createdAt;
        this.response = response;
        this.image = image;
        this.done = done;
        this.promptEvalCount = promptEvalCount;
        this.evalCount = evalCount;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getDone() {
        return done;
    }

    public void setDone(Boolean done) {
        this.done = done;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Integer getPromptEvalCount() {
        return promptEvalCount;
    }

    public void setPromptEvalCount(Integer promptEvalCount) {
        this.promptEvalCount = promptEvalCount;
    }

    public Integer getEvalCount() {
        return evalCount;
    }

    public void setEvalCount(Integer evalCount) {
        this.evalCount = evalCount;
    }

    static class Builder {

        private String model;
        private String createdAt;
        private String response;
        private String image;
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

        Builder image(String image) {
            this.image = image;
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
            return new CompletionResponse(model, createdAt, response, image, done, promptEvalCount, evalCount);
        }
    }
}
