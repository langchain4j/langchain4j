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
 * Bedrock Cohere Embedding Model with support for both versions:
 * {@code cohere.embed-english-v3} and it's multilingual version {@code cohere.embed-multilingual-v3}
 * <br>
 * See more details <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed.html">here</a>.
 */
@SuperBuilder
@Getter
public class BedrockCohereEmbeddingModel extends AbstractBedrockEmbeddingModel<BedrockCohereEmbeddingResponse> {

    @Builder.Default
    private final String model = Types.CohereEmbedEnglishTextV3.getValue();

    @Builder.Default
    private final String inputType = InputType.SEARCH_DOCUMENT.getValue();

    @Override
    protected List<Map<String, Object>> getRequestParameters(List<TextSegment> textSegments) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();
        for (TextSegment textSegmentItem: textSegments) {
            parameters.put("texts", new String[] {textSegmentItem.text()});
            parameters.put("input_type", inputType);
            result.add(parameters);
        }
        return result;
    }

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Class<BedrockCohereEmbeddingResponse> getResponseClassType() {
        return BedrockCohereEmbeddingResponse.class;
    }

    @Getter
    public enum Types {
        CohereEmbedEnglishTextV3("cohere.embed-english-v3"),
        CohereEmbedMultilingualTextV3("cohere.embed-multilingual-v3");

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

        InputType(String modelID) {
            this.value = modelID;
        }
    }

}
