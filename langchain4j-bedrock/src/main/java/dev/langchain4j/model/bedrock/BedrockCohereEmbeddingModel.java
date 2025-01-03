package dev.langchain4j.model.bedrock;


import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.bedrock.internal.AbstractBedrockEmbeddingModel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bedrock Cohere embedding model with support for both versions:
 * {@code cohere.embed-english-v3} and {@code cohere.embed-multilingual-v3}
 * <br>
 * See more details <a href="https://docs.cohere.com/v2/docs/amazon-bedrock">here</a> and
 * <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed.html">here</a>.
 */
@SuperBuilder
@Getter
public class BedrockCohereEmbeddingModel extends AbstractBedrockEmbeddingModel<BedrockCohereEmbeddingResponse> {

    @Builder.Default
    private final String model = Types.CohereEmbedEnglishV3.getValue();

    @Override
    protected String getModelId() {
        return model;
    }

    /**
     * 1024 is default size of output vector for Cohere Embedding model.
     */
    private final Integer dimensions;

    private final InputType inputType;

    @Builder.Default
    private final Truncate truncate = Truncate.NONE;

    @Builder.Default
    private final EmbeddingType embeddingType = EmbeddingType.FLOAT;

    @Override
    protected List<Map<String, Object>> getRequestParameters(List<TextSegment> textSegments) {
        if (dimensions != null) {
            throw new IllegalArgumentException("Dimensions is not supported for this model.");
        }
        if (inputType == null) {
            throw new IllegalArgumentException("Input type is required.");
        }
        if (embeddingType != EmbeddingType.FLOAT) {
            throw new IllegalArgumentException("Only float embedding type is supported.");
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (TextSegment textSegment : textSegments) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("texts", List.of(textSegment.text()));
            parameters.put("input_type", inputType.getValue());
            parameters.put("truncate", truncate.getValue());
            parameters.put("embedding_types", List.of(embeddingType.getValue()));
            result.add(parameters);
        }
        return result;
    }

    @Override
    protected Class<BedrockCohereEmbeddingResponse> getResponseClassType() {
        return BedrockCohereEmbeddingResponse.class;
    }

    @Getter
    public enum Types {
        CohereEmbedEnglishV3("cohere.embed-english-v3"),
        CohereEmbedMultilingualV3("cohere.embed-multilingual-v3");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }

    @Getter
    public enum InputType {
        SEARCH_DOCUMENT("search_document"),
        SEARCH_QUERY("search_query"),
        CLASSIFICATION("classification"),
        CLUSTERING("clustering");

        private final String value;

        InputType(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum Truncate {
        NONE("NONE"),
        START("START"),
        END("END");

        private final String value;

        Truncate(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum EmbeddingType {
        FLOAT("float"),
        INT8("int8"),
        UINT8("uint8"),
        BINARY("binary"),
        UBINARY("ubinary");

        private final String value;

        EmbeddingType(String value) {
            this.value = value;
        }
    }
}
