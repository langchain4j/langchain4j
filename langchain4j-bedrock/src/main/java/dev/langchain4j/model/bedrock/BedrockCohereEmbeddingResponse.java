package dev.langchain4j.model.bedrock;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.bedrock.internal.BedrockEmbeddingResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Bedrock Cohere embedding response
 * See more details <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed.html">here</a>.
 */
@Getter
@Setter
public class BedrockCohereEmbeddingResponse implements BedrockEmbeddingResponse {

    @Getter
    @Setter
    public static class Embeddings {
        @JsonProperty("float")
        private float[][] floatEmbeddings;
        @JsonProperty("int8")
        private int[][] int8Embeddings;
        @JsonProperty("uint8")
        private int[][] unsignedInt8Embeddings;
        @JsonProperty("binary")
        private byte[][] binaryEmbeddings;
        @JsonProperty("ubinary")
        private byte[][] unsignedBinaryEmbeddings;
    }


    private String id;
    private Embeddings embeddings;
    @JsonProperty("response_type")
    private String responseType;
    private List<String> texts;

    private int inputTextTokenCount = -1;

    @Override
    public Embedding toEmbedding() {
        return new Embedding(embeddings.getFloatEmbeddings()[0]);
    }
}
