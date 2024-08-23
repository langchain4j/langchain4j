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
import java.util.stream.Collectors;

/**
 * Bedrock Amazon Titan embedding model with support for both versions:
 * {@code amazon.titan-embed-text-v1} and {@code amazon.titan-embed-text-v2:0}
 * <br>
 * See more details <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/titan-embedding-models.html">here</a> and
 * <a href="https://aws.amazon.com/blogs/aws/amazon-titan-text-v2-now-available-in-amazon-bedrock-optimized-for-improving-rag/">here</a>.
 */
@SuperBuilder
@Getter
public class BedrockTitanEmbeddingModel extends AbstractBedrockEmbeddingModel<BedrockTitanEmbeddingResponse> {

    private final static String MODEL_V1_ID = "amazon.titan-embed-text-v1";
    private final static String MODEL_V2_ID = "amazon.titan-embed-text-v2:0";

    @Builder.Default
    private final String model = Types.TitanEmbedTextV1.getValue();

    @Override
    protected String getModelId() {
        return model;
    }

    /**
     * 1024 is default size of output vector for Titan Embedding model V2.
     */
    private final Integer dimensions;

    /**
     * A flag indicating whether to normalize the output embeddings.
     * It defaults to true, which is optimal for RAG use cases.
     */
    private final Boolean normalize;


    @Override
    protected List<Map<String, Object>> getRequestParameters(List<TextSegment> textSegments) {
        if (MODEL_V1_ID.equals(this.model)) {
            if (this.dimensions != null || this.normalize != null) {
                throw new IllegalArgumentException("Dimensions and normalize are not supported for Titan Embedding model V1");
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

    @Getter
    public enum Types {
        TitanEmbedTextV1("amazon.titan-embed-text-v1"),
        TitanEmbedTextV2("amazon.titan-embed-text-v2:0");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }
}
