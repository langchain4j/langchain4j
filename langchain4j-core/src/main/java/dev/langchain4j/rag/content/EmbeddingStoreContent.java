package dev.langchain4j.rag.content;

import dev.langchain4j.data.segment.TextSegment;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;

/**
 * Represents content retrieved from an embedding store.
 * It contains a {@link TextSegment}, a relevance score (derived from the cosine distance),
 * and an embedding identifier.
 */
public class EmbeddingStoreContent extends Content{

    private final Double score;
    private final String embeddingId;

    /**
     * Creates a new instance.
     * @param textSegment A semantically meaningful segment (chunk/piece/fragment) of a larger entity such as a document or chat conversation.
     *                    {@link dev.langchain4j.data.segment.TextSegment}
     * @param score The relevance score (derivative of cosine distance) of this embedding compared to
     *              a reference embedding during a search.
     * @param embeddingId The ID of the embedding assigned when adding this embedding to the store.
     */
    public EmbeddingStoreContent(TextSegment textSegment, Double score, String embeddingId) {
        super(textSegment);
        if(score != null) {
            ensureGreaterThanZero(score, "score");
        }
        this.score = score;
        this.embeddingId = embeddingId;
    }

    /**
     * Retrieves the relevance score associated with this content.
     *
     * @return The relevance score, or {@code null} if not specified.
     */
    public Double score() {
        return score;
    }

    /**
     * Retrieves the embedding identifier associated with this content.
     *
     * @return The embedding ID, or {@code null} if not specified.
     */
    public String embeddingId() {
        return embeddingId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        EmbeddingStoreContent that = (EmbeddingStoreContent) o;
        return Objects.equals(score, that.score) && Objects.equals(embeddingId, that.embeddingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), score, embeddingId);
    }

    @Override
    public String toString() {
        return "EmbeddingStoreContent{" +
                "score=" + score +
                ", embeddingId='" + embeddingId + '\'' +
                '}';
    }

    public static EmbeddingStoreContent from(TextSegment textSegment, Double score, String embeddingId) {
        return new EmbeddingStoreContent(textSegment, score, embeddingId);
    }

}
