package dev.langchain4j.store.embedding.typed;

import dev.langchain4j.store.embedding.hibernate.EmbeddingVector;
import dev.langchain4j.store.embedding.hibernate.MetadataAttribute;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.annotations.Array;

@Entity
@Table(name = "articles")
public class ArticleEntity {

    public enum Status {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }

    @Id
    private Long id;

    @MetadataAttribute
    @Enumerated(EnumType.STRING)
    private Status status;

    @MetadataAttribute
    private LocalDate publishedOn;

    @MetadataAttribute
    private Instant updatedAt;

    @EmbeddingVector
    @Array(length = 3)
    private float[] embedding;

    @UnmappedMetadata
    private Map<String, Object> metadata;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }

    public LocalDate getPublishedOn() {
        return publishedOn;
    }

    public void setPublishedOn(final LocalDate publishedOn) {
        this.publishedOn = publishedOn;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(final float[] embedding) {
        this.embedding = embedding;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(final Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
