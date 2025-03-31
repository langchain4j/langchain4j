package dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore;

import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;
import java.util.Map;

public class AzureCosmosDbMongoVCoreDocument {

    @BsonId
    private String id;
    private List<Float> embedding;
    private String text;
    private Map<String, String> metadata;

    public AzureCosmosDbMongoVCoreDocument(String id, List<Float> embedding, String text, Map<String, String> metadata) {
        this.id = id;
        this.embedding = embedding;
        this.text = text;
        this.metadata = metadata;
    }

    public AzureCosmosDbMongoVCoreDocument() {
    }

    public static AzureCosmosDbMongoVCoreDocumentBuilder builder() {
        return new AzureCosmosDbMongoVCoreDocumentBuilder();
    }

    public String getId() {
        return this.id;
    }

    public List<Float> getEmbedding() {
        return this.embedding;
    }

    public String getText() {
        return this.text;
    }

    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof AzureCosmosDbMongoVCoreDocument)) return false;
        final AzureCosmosDbMongoVCoreDocument other = (AzureCosmosDbMongoVCoreDocument) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$id = this.getId();
        final Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final Object this$embedding = this.getEmbedding();
        final Object other$embedding = other.getEmbedding();
        if (this$embedding == null ? other$embedding != null : !this$embedding.equals(other$embedding)) return false;
        final Object this$text = this.getText();
        final Object other$text = other.getText();
        if (this$text == null ? other$text != null : !this$text.equals(other$text)) return false;
        final Object this$metadata = this.getMetadata();
        final Object other$metadata = other.getMetadata();
        if (this$metadata == null ? other$metadata != null : !this$metadata.equals(other$metadata)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof AzureCosmosDbMongoVCoreDocument;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final Object $embedding = this.getEmbedding();
        result = result * PRIME + ($embedding == null ? 43 : $embedding.hashCode());
        final Object $text = this.getText();
        result = result * PRIME + ($text == null ? 43 : $text.hashCode());
        final Object $metadata = this.getMetadata();
        result = result * PRIME + ($metadata == null ? 43 : $metadata.hashCode());
        return result;
    }

    public String toString() {
        return "AzureCosmosDbMongoVCoreDocument(id=" + this.getId() + ", embedding=" + this.getEmbedding() + ", text=" + this.getText() + ", metadata=" + this.getMetadata() + ")";
    }

    public static class AzureCosmosDbMongoVCoreDocumentBuilder {
        private String id;
        private List<Float> embedding;
        private String text;
        private Map<String, String> metadata;

        AzureCosmosDbMongoVCoreDocumentBuilder() {
        }

        public AzureCosmosDbMongoVCoreDocumentBuilder id(String id) {
            this.id = id;
            return this;
        }

        public AzureCosmosDbMongoVCoreDocumentBuilder embedding(List<Float> embedding) {
            this.embedding = embedding;
            return this;
        }

        public AzureCosmosDbMongoVCoreDocumentBuilder text(String text) {
            this.text = text;
            return this;
        }

        public AzureCosmosDbMongoVCoreDocumentBuilder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public AzureCosmosDbMongoVCoreDocument build() {
            return new AzureCosmosDbMongoVCoreDocument(this.id, this.embedding, this.text, this.metadata);
        }

        public String toString() {
            return "AzureCosmosDbMongoVCoreDocument.AzureCosmosDbMongoVCoreDocumentBuilder(id=" + this.id + ", embedding=" + this.embedding + ", text=" + this.text + ", metadata=" + this.metadata + ")";
        }
    }
}
