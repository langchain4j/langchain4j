package dev.langchain4j.store.embedding.mongodb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MongoDbMatchedDocument {

    private String id;
    private List<Float> embedding;
    private String text;
    private Map<String, String> metadata;
    private Double score;

    MongoDbMatchedDocument() {
    }

    MongoDbMatchedDocument(String id, List<Float> embedding, String text, Map<String, String> metadata, Double score) {
        this.id = id;
        this.embedding = embedding;
        this.text = text;
        this.metadata = new HashMap<>(metadata);
        this.score = score;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Float> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String id;
        private List<Float> embedding;
        private String text;
        private Map<String, String> metadata;
        private Double score;

        Builder id(String id) {
            this.id = id;
            return this;
        }

        Builder embedding(List<Float> embedding) {
            this.embedding = embedding;
            return this;
        }

        Builder text(String text) {
            this.text = text;
            return this;
        }

        Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        Builder score(Double score) {
            this.score = score;
            return this;
        }
    }
}
