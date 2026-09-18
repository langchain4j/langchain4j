package dev.langchain4j.model.cohere;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Wire response for Cohere's v2 embed endpoint. Embeddings are nested under {@code embeddings.float}.
 */
class EmbedV2Response {

    private String id;
    private Embeddings embeddings;
    private Meta meta;

    public String getId() {
        return id;
    }

    public Embeddings getEmbeddings() {
        return embeddings;
    }

    public Meta getMeta() {
        return meta;
    }

    static class Embeddings {

        // "float" is a Java keyword, so the field is named differently and mapped explicitly.
        @JsonProperty("float")
        private List<List<Float>> floatEmbeddings;

        public List<List<Float>> getFloatEmbeddings() {
            return floatEmbeddings;
        }
    }
}
