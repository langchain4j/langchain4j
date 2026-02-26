package dev.langchain4j.store.embedding.typed;

import dev.langchain4j.store.embedding.hibernate.Embedding;
import dev.langchain4j.store.embedding.hibernate.MetadataAttribute;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.Array;

@Entity
@Table(name = "books")
public class BookEntity {
    @Id
    private Long id;

    private String title;
    private String content;

    @MetadataAttribute
    @Embedded
    private BookDetailsEmbeddable details = new BookDetailsEmbeddable();

    @MetadataAttribute
    @ManyToOne(fetch = FetchType.LAZY)
    private AuthorEntity author;

    @Embedding
    @Array(length = 384)
    private float[] embedding;

    @UnmappedMetadata
    private Map<String, Object> metadata;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(final String content) {
        this.content = content;
    }

    public BookDetailsEmbeddable getDetails() {
        return details;
    }

    public void setDetails(final BookDetailsEmbeddable details) {
        this.details = details;
    }

    public AuthorEntity getAuthor() {
        return author;
    }

    public void setAuthor(final AuthorEntity author) {
        this.author = author;
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
