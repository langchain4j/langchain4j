package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
class DeleteModelRequest {

    private String name;

    DeleteModelRequest() {
    }

    DeleteModelRequest(String name) {
        this.name = name;
    }

    static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    static class Builder {

        private String name;

        Builder name(String name) {
            this.name = name;
            return this;
        }

        DeleteModelRequest build() {
            return new DeleteModelRequest(name);
        }
    }
}
