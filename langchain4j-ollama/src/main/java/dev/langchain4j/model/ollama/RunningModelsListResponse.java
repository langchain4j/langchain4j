package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class RunningModelsListResponse {

    private List<RunningOllamaModel> models;

    RunningModelsListResponse() {

    }

    RunningModelsListResponse(List<RunningOllamaModel> models) {
        this.models = models;
    }

    public List<RunningOllamaModel> getModels() {
        return models;
    }

    public void setModels(List<RunningOllamaModel> models) {
        this.models = models;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private List<RunningOllamaModel> models;

        Builder models(List<RunningOllamaModel> models) {
            this.models = models;
            return this;
        }

        RunningModelsListResponse build() {
            return new RunningModelsListResponse(models);
        }
    }
}
