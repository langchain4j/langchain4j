package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.bedrock.internal.AbstractBedrockEmbeddingModel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bedrock Amazon Titan embedding model
 */
@SuperBuilder
@Getter
public class BedrockTitanEmbeddingModel extends AbstractBedrockEmbeddingModel<BedrockTitanEmbeddingResponse> {
    private final static String MODEL_ID = "amazon.titan-embed-text-v1";

    @Builder.Default
    private final String model = Types.TitanEmbedTextV1.getValue();

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

    @Getter
    public enum Types {
        TitanEmbedTextV1("amazon.titan-embed-text-v1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }
}
