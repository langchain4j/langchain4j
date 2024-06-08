package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.bedrock.internal.AbstractBedrockEmbeddingModel;
import lombok.Getter;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * Bedrock Amazon Titan embedding model
 */
@Getter
public class BedrockTitanEmbeddingModel extends AbstractBedrockEmbeddingModel<BedrockTitanEmbeddingResponse> {
    private static final String MODEL_ID = "amazon.titan-embed-text-v1";

    private final String model;

    public BedrockTitanEmbeddingModel(Region region,
                                      AwsCredentialsProvider credentialsProvider,
                                      Integer maxRetries,
                                      String model) {
        super(region, credentialsProvider, maxRetries);
        this.model = getOrDefault(model, Types.TitanEmbedTextV1.getValue());
    }

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected List<Map<String, Object>> getRequestParameters(List<TextSegment> textSegments) {
        return textSegments.stream()
                .map(TextSegment::text)
                .map(text -> of("inputText", text))
                .collect(Collectors.toList());
    }

    @Override
    protected Class<BedrockTitanEmbeddingResponse> getResponseClassType() {
        return BedrockTitanEmbeddingResponse.class;
    }

    @Override
    protected Map<String, Integer> dimensionMap() {
        return new HashMap<String, Integer>() {{
            put(model, 1536);
        }};
    }

    @Override
    protected String modelName() {
        return model;
    }

    @Getter
    public enum Types {
        TitanEmbedTextV1("amazon.titan-embed-text-v1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }

    public static BedrockTitanEmbeddingModelBuilder builder() {
        return new BedrockTitanEmbeddingModelBuilder();
    }

    public static class BedrockTitanEmbeddingModelBuilder extends AbstractBedrockEmbeddingModelBuilder<BedrockTitanEmbeddingModel, BedrockTitanEmbeddingModelBuilder> {

        private String model;

        public BedrockTitanEmbeddingModelBuilder model(String model) {
            this.model = model;
            return self();
        }

        @Override
        protected BedrockTitanEmbeddingModelBuilder self() {
            return this;
        }

        @Override
        public BedrockTitanEmbeddingModel build() {
            return new BedrockTitanEmbeddingModel(region, credentialsProvider, maxRetries, model);
        }
    }
}
