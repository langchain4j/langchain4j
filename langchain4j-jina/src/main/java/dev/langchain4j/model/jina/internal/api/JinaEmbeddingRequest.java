package dev.langchain4j.model.jina.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JinaEmbeddingRequest {

    public String model;
    public String task;
    public Boolean lateChunking;
    public List<String> input;

    JinaEmbeddingRequest(String model, String task, Boolean lateChunking, List<String> input) {
        this.model = model;
        this.task = task;
        this.lateChunking = lateChunking;
        this.input = input;
    }

    public static JinaEmbeddingRequestBuilder builder() {
        return new JinaEmbeddingRequestBuilder();
    }

    public static class JinaEmbeddingRequestBuilder {
        private String model;
        private String task;
        private Boolean lateChunking;
        private List<String> input;

        JinaEmbeddingRequestBuilder() {}

        public JinaEmbeddingRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        public JinaEmbeddingRequestBuilder task(String task) {
            this.task = task;
            return this;
        }

        public JinaEmbeddingRequestBuilder lateChunking(Boolean lateChunking) {
            this.lateChunking = lateChunking;
            return this;
        }

        public JinaEmbeddingRequestBuilder input(List<String> input) {
            this.input = input;
            return this;
        }

        public JinaEmbeddingRequest build() {
            return new JinaEmbeddingRequest(this.model, this.task, this.lateChunking, this.input);
        }

        public String toString() {
            return "JinaEmbeddingRequest.JinaEmbeddingRequestBuilder(model=" + this.model + ", task=" + this.task
                    + ", lateChunking=" + this.lateChunking + ", input=" + this.input + ")";
        }
    }
}
