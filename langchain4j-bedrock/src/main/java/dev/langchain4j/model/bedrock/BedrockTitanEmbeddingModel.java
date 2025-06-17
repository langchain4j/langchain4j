package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Bedrock Amazon Titan embedding model with support for both versions:
 * {@code amazon.titan-embed-text-v1} and {@code amazon.titan-embed-text-v2:0}
 * <br>
 * See more details <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/titan-embedding-models.html">here</a> and
 * <a href="https://aws.amazon.com/blogs/aws/amazon-titan-text-v2-now-available-in-amazon-bedrock-optimized-for-improving-rag/">here</a>.
 */
public class BedrockTitanEmbeddingModel extends AbstractBedrockEmbeddingModel<BedrockTitanEmbeddingResponse> {

    private final String model;
    private final Integer dimensions;
    private final Boolean normalize;

    protected BedrockTitanEmbeddingModel(BedrockTitanEmbeddingModelBuilder<?, ?> builder) {
        super(builder);
        this.model = ensureNotBlank(builder.model, "model");
        this.dimensions = builder.dimensions;
        this.normalize = builder.normalize;
    }

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Integer knownDimension() {
        return dimensions;
    }

    @Override
    protected List<Map<String, Object>> getRequestParameters(List<TextSegment> textSegments) {
        if (Types.TitanEmbedTextV1.getValue().equals(this.model)) {
            if (this.dimensions != null || this.normalize != null) {
                throw new IllegalArgumentException(
                        "Dimensions and normalize are not supported for Titan Embedding model V1");
            }
            return textSegments.stream()
                    .map(TextSegment::text)
                    .map(text -> of("inputText", text))
                    .collect(Collectors.toList());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (TextSegment textSegment : textSegments) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("inputText", textSegment.text());
            parameters.put("dimensions", dimensions);
            parameters.put("normalize", normalize);
            result.add(parameters);
        }
        return result;
    }

    @Override
    protected Class<BedrockTitanEmbeddingResponse> getResponseClassType() {
        return BedrockTitanEmbeddingResponse.class;
    }

    public enum Types {
        TitanEmbedTextV1("amazon.titan-embed-text-v1"),
        TitanEmbedTextV2("amazon.titan-embed-text-v2:0");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }

        public String getValue() {
            return value;
        }
    }

    public String getModel() {
        return model;
    }

    public Integer getDimensions() {
        return dimensions;
    }

    public Boolean getNormalize() {
        return normalize;
    }

    public static BedrockTitanEmbeddingModelBuilder<?, ?> builder() {
        return new BedrockTitanEmbeddingModelBuilderImpl();
    }

    public abstract static class BedrockTitanEmbeddingModelBuilder<
                    C extends BedrockTitanEmbeddingModel, B extends BedrockTitanEmbeddingModelBuilder<C, B>>
            extends AbstractBedrockEmbeddingModel.AbstractBedrockEmbeddingModelBuilder<
                    BedrockTitanEmbeddingResponse, C, B> {

        private String model;
        private Integer dimensions;
        private Boolean normalize;

        public B model(String model) {
            this.model = model;
            return self();
        }

        public B dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return self();
        }

        public B normalize(Boolean normalize) {
            this.normalize = normalize;
            return self();
        }

        protected abstract B self();

        public abstract C build();
    }

    private static final class BedrockTitanEmbeddingModelBuilderImpl
            extends BedrockTitanEmbeddingModelBuilder<
                    BedrockTitanEmbeddingModel, BedrockTitanEmbeddingModelBuilderImpl> {
        protected BedrockTitanEmbeddingModelBuilderImpl self() {
            return this;
        }

        public BedrockTitanEmbeddingModel build() {
            return new BedrockTitanEmbeddingModel(this);
        }
    }
}
