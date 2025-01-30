package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.bedrock.internal.AbstractBedrockEmbeddingModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Bedrock Cohere embedding model with support for both versions:
 * {@code cohere.embed-english-v3} and {@code cohere.embed-multilingual-v3}
 * <br>
 * See more details <a href="https://docs.cohere.com/v2/docs/amazon-bedrock">here</a> and
 * <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed.html">here</a>.
 */
public class BedrockCohereEmbeddingModel extends AbstractBedrockEmbeddingModel<BedrockCohereEmbeddingResponse> {

    private final String model;
    private final String inputType;
    private final String truncate;

    public BedrockCohereEmbeddingModel(Builder builder) {
        super(builder);
        this.model = ensureNotBlank(builder.model, "model");
        this.inputType = ensureNotBlank(builder.inputType, "inputType");
        this.truncate = builder.truncate;
    }

    @Override
    protected List<Map<String, Object>> getRequestParameters(List<TextSegment> textSegments) {

        List<Map<String, Object>> result = new ArrayList<>();
        for (TextSegment textSegment : textSegments) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("texts", List.of(textSegment.text()));
            parameters.put("input_type", inputType);
            parameters.put("truncate", truncate);
            parameters.put("embedding_types", List.of("float"));
            result.add(parameters);
        }
        return result;
    }

    @Override
    protected Class<BedrockCohereEmbeddingResponse> getResponseClassType() {
        return BedrockCohereEmbeddingResponse.class;
    }

    @Override
    protected String getModelId() {
        return model;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBedrockEmbeddingModelBuilder<BedrockCohereEmbeddingResponse, BedrockCohereEmbeddingModel, Builder> {

        private String model;
        private String inputType;
        private String truncate;

        @Override
        protected Builder self() {
            return this;
        }

        public Builder model(Model model) {
            return model(model.getValue());
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder inputType(InputType inputType) {
            return inputType(inputType.getValue());
        }

        public Builder inputType(String inputType) {
            this.inputType = inputType;
            return this;
        }

        public Builder truncate(Truncate truncate) {
            return truncate(truncate.getValue());
        }

        public Builder truncate(String truncate) {
            this.truncate = truncate;
            return this;
        }

        public BedrockCohereEmbeddingModel build() {
            return new BedrockCohereEmbeddingModel(self());
        }
    }

    public enum Model {

        COHERE_EMBED_ENGLISH_V3("cohere.embed-english-v3"),
        COHERE_EMBED_MULTILINGUAL_V3("cohere.embed-multilingual-v3");

        private final String value;

        Model(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum InputType {

        SEARCH_DOCUMENT("search_document"),
        SEARCH_QUERY("search_query"),
        CLASSIFICATION("classification"),
        CLUSTERING("clustering");

        private final String value;

        InputType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum Truncate {

        NONE("NONE"),
        START("START"),
        END("END");

        private final String value;

        Truncate(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
