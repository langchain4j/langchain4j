package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class ModelParam {

    private String modelId;
    private List<String> fields;
    private String out;

    ModelParam() {
    }

    ModelParam(String modelId, List<String> fields, String out) {
        this.modelId = modelId;
        this.fields = fields;
        this.out = out;
    }

    public String getModelId() {
        return modelId;
    }

    public List<String> getFields() {
        return fields;
    }

    public String getOut() {
        return out;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String modelId;
        private List<String> fields;
        private String out;

        Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        Builder fields(List<String> fields) {
            this.fields = fields;
            return this;
        }

        Builder out(String out) {
            this.out = out;
            return this;
        }

        ModelParam build() {
            return new ModelParam(modelId, fields, out);
        }
    }
}
