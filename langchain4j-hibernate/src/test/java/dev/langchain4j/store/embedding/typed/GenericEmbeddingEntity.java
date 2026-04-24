package dev.langchain4j.store.embedding.typed;

import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.Embedding;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Array;

@Entity
@Table(name = "generic_embedding_entity")
public class GenericEmbeddingEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @EmbeddedText
    private String text;

    @Embedding
    @Array(length = 384)
    private float[] embedding;

    @UnmappedMetadata
    private String metadata;
}
