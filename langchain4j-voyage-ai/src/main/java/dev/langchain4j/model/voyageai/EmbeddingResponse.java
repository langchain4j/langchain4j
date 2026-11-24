package dev.langchain4j.model.voyageai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

class EmbeddingResponse {

    private String object;
    private List<EmbeddingData> data;
    private String model;
    private TokenUsage usage;

    public String getObject() {
        return object;
    }

    public List<EmbeddingData> getData() {
        return data;
    }

    public String getModel() {
        return model;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    static class EmbeddingData {

        private String object;
        private List<Float> embedding;
        private Integer index;

        EmbeddingData() {
        }

        EmbeddingData(String object, List<Float> embedding, Integer index) {
            this.object = object;
            this.embedding = embedding;
            this.index = index;
        }

        public String getObject() {
            return object;
        }

        public List<Float> getEmbedding() {
            return embedding;
        }

        public Integer getIndex() {
            return index;
        }

        /**
         * Voyage sends an embedding as an array of numbers, or as little-endian float bytes in
         * Base64 when {@code encoding_format} is {@code base64}.
         */
        @JsonProperty("embedding")
        void setEmbedding(Object embedding) {
            this.embedding = toFloats(embedding);
        }

        private static List<Float> toFloats(Object embedding) {
            if (embedding == null) {
                return null;
            }
            if (embedding instanceof String base64) {
                return decodeBase64(base64);
            }
            if (embedding instanceof List<?> values) {
                List<Float> floats = new ArrayList<>(values.size());
                for (Object value : values) {
                    if (!(value instanceof Number number)) {
                        throw new IllegalArgumentException("Unexpected embedding " + embedding);
                    }
                    floats.add(number.floatValue());
                }
                return floats;
            }
            throw new IllegalArgumentException("Unexpected embedding " + embedding);
        }

        private static List<Float> decodeBase64(String base64) {
            byte[] bytes = Base64.getDecoder().decode(base64);
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            List<Float> embedding = new ArrayList<>(bytes.length / Float.BYTES);
            while (buffer.remaining() >= Float.BYTES) {
                embedding.add(buffer.getFloat());
            }
            return embedding;
        }
    }
}
