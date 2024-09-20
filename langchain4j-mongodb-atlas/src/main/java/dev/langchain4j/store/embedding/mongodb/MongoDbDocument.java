package dev.langchain4j.store.embedding.mongodb;

import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;
import java.util.Map;

class MongoDbDocument {

    @BsonId
    private String id;
    private List<Float> embedding;
    private String text;
    private Map<String, Object> metadata;

    MongoDbDocument() {
    }

    MongoDbDocument(String id, List<Float> embedding, String text, Map<String, Object> metadata) {
        this.id = id;
        this.embedding = embedding;
        this.text = text;
        this.metadata = metadata;
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

        private String id;
        private List<Float> embedding;
        private String text;
        private Map<String, Object> metadata;

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

        Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        MongoDbDocument build() {
            return new MongoDbDocument(id, embedding, text, metadata);
        }

    }
}
