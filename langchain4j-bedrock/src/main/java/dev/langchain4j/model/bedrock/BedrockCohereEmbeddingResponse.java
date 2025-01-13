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
class BedrockCohereEmbeddingResponse implements BedrockEmbeddingResponse {

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

        public float[][] getFloatEmbeddings() {
            return floatEmbeddings;
        }

        public void setFloatEmbeddings(final float[][] floatEmbeddings) {
            this.floatEmbeddings = floatEmbeddings;
        }

        public int[][] getInt8Embeddings() {
            return int8Embeddings;
        }

        public void setInt8Embeddings(final int[][] int8Embeddings) {
            this.int8Embeddings = int8Embeddings;
        }

        public int[][] getUnsignedInt8Embeddings() {
            return unsignedInt8Embeddings;
        }

        public void setUnsignedInt8Embeddings(final int[][] unsignedInt8Embeddings) {
            this.unsignedInt8Embeddings = unsignedInt8Embeddings;
        }

        public byte[][] getBinaryEmbeddings() {
            return binaryEmbeddings;
        }

        public void setBinaryEmbeddings(final byte[][] binaryEmbeddings) {
            this.binaryEmbeddings = binaryEmbeddings;
        }

        public byte[][] getUnsignedBinaryEmbeddings() {
            return unsignedBinaryEmbeddings;
        }

        public void setUnsignedBinaryEmbeddings(final byte[][] unsignedBinaryEmbeddings) {
            this.unsignedBinaryEmbeddings = unsignedBinaryEmbeddings;
        }
    }


    private String id;
    private Embeddings embeddings;
    @JsonProperty("response_type")
    private String responseType;
    private List<String> texts;

    private int inputTextTokenCount = -1;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Embeddings getEmbeddings() {
        return embeddings;
    }

    public void setEmbeddings(final Embeddings embeddings) {
        this.embeddings = embeddings;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(final String responseType) {
        this.responseType = responseType;
    }

    public List<String> getTexts() {
        return texts;
    }

    public void setTexts(final List<String> texts) {
        this.texts = texts;
    }

    public int getInputTextTokenCount() {
        return inputTextTokenCount;
    }

    public void setInputTextTokenCount(final int inputTextTokenCount) {
        this.inputTextTokenCount = inputTextTokenCount;
    }

    @Override
    public Embedding toEmbedding() {
        return new Embedding(embeddings.getFloatEmbeddings()[0]);
    }
}
