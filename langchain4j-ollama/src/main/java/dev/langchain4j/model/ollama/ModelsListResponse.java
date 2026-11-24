package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
class ModelsListResponse {

    private List<OllamaModel> models;

    ModelsListResponse() {
    }

    ModelsListResponse(List<OllamaModel> models) {
        this.models = models;
    }

    static Builder builder() {
        return new Builder();
    }

    public List<OllamaModel> getModels() {
        return models;
    }

    public void setModels(List<OllamaModel> models) {
        this.models = models;
    }

    static class Builder {

        private List<OllamaModel> models;

        Builder models(List<OllamaModel> models) {
            this.models = models;
            return this;
        }

        ModelsListResponse build() {
            return new ModelsListResponse(models);
        }
    }
}
