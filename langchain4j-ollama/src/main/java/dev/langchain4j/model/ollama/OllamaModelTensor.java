package dev.langchain4j.model.ollama;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class OllamaModelTensor {

    private String name;
    private String type;
    private List<Long> shape;

    OllamaModelTensor() {}

    public OllamaModelTensor(String name, String type, List<Long> shape) {
        this.name = name;
        this.type = type;
        this.shape = shape;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Long> getShape() {
        return shape;
    }

    public void setShape(List<Long> shape) {
        this.shape = shape;
    }

    public static class Builder {

        private String name;
        private String type;
        private List<Long> shape;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder shape(List<Long> shape) {
            this.shape = shape;
            return this;
        }

        public OllamaModelTensor build() {
            return new OllamaModelTensor(name, type, shape);
        }
    }
}
