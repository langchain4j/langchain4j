package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiEmbeddingRequest {

    private String model;
    private List<String> input;
    private String encodingFormat;

    public static class MistralAiEmbeddingRequestBuilder {

        private String model;

        private List<String> input;

        private String encodingFormat;

        MistralAiEmbeddingRequestBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingRequest.MistralAiEmbeddingRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingRequest.MistralAiEmbeddingRequestBuilder input(List<String> input) {
            this.input = input;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingRequest.MistralAiEmbeddingRequestBuilder encodingFormat(String encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        public MistralAiEmbeddingRequest build() {
            return new MistralAiEmbeddingRequest(this.model, this.input, this.encodingFormat);
        }

        public String toString() {
            return "MistralAiEmbeddingRequest.MistralAiEmbeddingRequestBuilder("
                    + "model=" + this.model
                    + ", input=" + this.input
                    + ", encodingFormat=" + this.encodingFormat
                    + ")";
        }
    }

    public static MistralAiEmbeddingRequest.MistralAiEmbeddingRequestBuilder builder() {
        return new MistralAiEmbeddingRequest.MistralAiEmbeddingRequestBuilder();
    }

    public MistralAiEmbeddingRequest.MistralAiEmbeddingRequestBuilder toBuilder() {
        return new MistralAiEmbeddingRequest.MistralAiEmbeddingRequestBuilder().model(this.model).input(this.input).encodingFormat(this.encodingFormat);
    }

    public String getModel() {
        return this.model;
    }

    public List<String> getInput() {
        return this.input;
    }

    public String getEncodingFormat() {
        return this.encodingFormat;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setInput(List<String> input) {
        this.input = input;
    }

    public void setEncodingFormat(String encodingFormat) {
        this.encodingFormat = encodingFormat;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.model);
        hash = 29 * hash + Objects.hashCode(this.input);
        hash = 29 * hash + Objects.hashCode(this.encodingFormat);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiEmbeddingRequest other = (MistralAiEmbeddingRequest) obj;
        return Objects.equals(this.model, other.model)
                && Objects.equals(this.encodingFormat, other.encodingFormat)
                && Objects.equals(this.input, other.input);
    }

    public String toString() {
        return "MistralAiEmbeddingRequest("
                + "model=" + this.getModel()
                + ", input=" + this.getInput()
                + ", encodingFormat="
                + this.getEncodingFormat()
                + ")";
    }

    public MistralAiEmbeddingRequest() {
    }

    public MistralAiEmbeddingRequest(String model, List<String> input, String encodingFormat) {
        this.model = model;
        this.input = input;
        this.encodingFormat = encodingFormat;
    }
}
