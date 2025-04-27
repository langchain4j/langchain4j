package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import java.util.List;
import java.util.Map;

public class AzureCosmosDbNoSqlMatchedDocument {

    private String id;
    private List<Float> embedding;
    private String text;
    private Map<String, String> metadata;
    private Double score;

    public AzureCosmosDbNoSqlMatchedDocument(String id, List<Float> embedding, String text, Map<String, String> metadata, Double score) {
        this.id = id;
        this.embedding = embedding;
        this.text = text;
        this.metadata = metadata;
        this.score = score;
    }

    public AzureCosmosDbNoSqlMatchedDocument() {
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

    public Double getScore() {
        return this.score;
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

    public void setScore(Double score) {
        this.score = score;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof AzureCosmosDbNoSqlMatchedDocument)) return false;
        final AzureCosmosDbNoSqlMatchedDocument other = (AzureCosmosDbNoSqlMatchedDocument) o;
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
        final Object this$score = this.getScore();
        final Object other$score = other.getScore();
        if (this$score == null ? other$score != null : !this$score.equals(other$score)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof AzureCosmosDbNoSqlMatchedDocument;
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
        final Object $score = this.getScore();
        result = result * PRIME + ($score == null ? 43 : $score.hashCode());
        return result;
    }

    public String toString() {
        return "AzureCosmosDbNoSqlMatchedDocument(id=" + this.getId() + ", embedding=" + this.getEmbedding() + ", text=" + this.getText() + ", metadata=" + this.getMetadata() + ", score=" + this.getScore() + ")";
    }
}
