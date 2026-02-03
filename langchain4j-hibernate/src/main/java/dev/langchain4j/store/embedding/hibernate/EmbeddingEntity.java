package dev.langchain4j.store.embedding.hibernate;

import jakarta.persistence.Id;
import java.util.UUID;
import org.hibernate.annotations.Array;

public class EmbeddingEntity {
    @Id
    private UUID id;

    @EmbeddingText
    private String text;

    @Embedding
    @Array(length = 0) // The length is overridden by the dynamic builder
    private float[] embedding;

    @TextMetadata
    private String metadata;
}
