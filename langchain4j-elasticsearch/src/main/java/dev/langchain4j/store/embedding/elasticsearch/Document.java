package dev.langchain4j.store.embedding.elasticsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
class Document {

    private float[] vector;
    private String text;
    private Map<String, Object> metadata;

    Document() {

    }

    Document(float[] vector, String text, Map<String, Object> metadata) {
        this.vector = vector;
        this.text = text;
        this.metadata = metadata;
    }

    public float[] getVector() {
        return vector;
    }

    public void setVector(float[] vector) {
        this.vector = vector;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private float[] vector;
        private String text;
        private Map<String, Object> metadata;

        Builder vector(float[] vector) {
            this.vector = vector;
            return this;
        }

        Builder text(String text) {
            this.text = text;
            return this;
        }

        Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        Document build() {
            return new Document(vector, text, metadata);
        }
    }
}
