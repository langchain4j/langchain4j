package dev.langchain4j.model.zhipu.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.internal.Utils;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.zhipu.embedding.EmbeddingModel.EMBEDDING_2;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class EmbeddingRequest {
    private String input;
    private String model;
    private Integer dimensions;

    public EmbeddingRequest(String input, String model, Integer dimensions) {
        this.input = input;
        this.model = Utils.getOrDefault(model, EMBEDDING_2.toString());
        this.dimensions = dimensions;
    }

    public static EmbeddingRequestBuilder builder() {
        return new EmbeddingRequestBuilder();
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getDimensions() {
        return dimensions;
    }

    public void setDimensions(Integer dimensions) {
        this.dimensions = dimensions;
    }

    public static class EmbeddingRequestBuilder {
        private String input;
        private String model;
        private Integer dimensions;

        EmbeddingRequestBuilder() {
        }

        public EmbeddingRequestBuilder input(String input) {
            this.input = input;
            return this;
        }

        public EmbeddingRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        public EmbeddingRequestBuilder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public EmbeddingRequest build() {

            return new EmbeddingRequest(this.input, this.model, this.dimensions);
        }
    }
}
