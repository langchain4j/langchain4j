package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class ChatResponse {

    private String model;
    private String createdAt;
    private Message message;
    private Boolean done;
    private Integer promptEvalCount;
    private Integer evalCount;

    ChatResponse() {

    }

    ChatResponse(String model, String createdAt, Message message, Boolean done, Integer promptEvalCount, Integer evalCount) {
        this.model = model;
        this.createdAt = createdAt;
        this.message = message;
        this.done = done;
        this.promptEvalCount = promptEvalCount;
        this.evalCount = evalCount;
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

    String getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    Message getMessage() {
        return message;
    }

    void setMessage(Message message) {
        this.message = message;
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
        private Message message;
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

        Builder message(Message message) {
            this.message = message;
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

        ChatResponse build() {
            return new ChatResponse(model, createdAt, message, done, promptEvalCount, evalCount);
        }
    }
}
