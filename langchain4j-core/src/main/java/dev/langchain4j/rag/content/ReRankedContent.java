package dev.langchain4j.rag.content;

import dev.langchain4j.data.segment.TextSegment;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;

/**
 * Represents content that has been re-ranked based on its relevance to a given query.
 * <p>
 * This class extends {@link Content} and introduces a relevance score that can be used
 * to prioritize or filter content based on its importance to the context of a query.
 * </p>
 * <p>
 * This is especially useful in scenarios where multiple contents are retrieved,
 * and additional processing (e.g., re-ranking) is applied to determine the most relevant items.
 * </p>
 */
public class ReRankedContent extends Content{

    private final Double score;

    /**
     * Creates a new instance of {@link ReRankedContent}.
     *
     * @param textSegment A semantically meaningful segment (chunk/piece/fragment) of a larger entity,
     *                    such as a document or chat conversation. Must not be null.
     * @param score       The relevance score assigned to this content, often based on a ranking model.
     *                    The score must be greater than zero, if provided.
     */
    public ReRankedContent(TextSegment textSegment, Double score) {
        super(textSegment);
        if(score != null) {
            ensureGreaterThanZero(score, "score");
        }
        this.score = score;
    }

    /**
     * Retrieves the relevance score associated with this content.
     *
     * @return The relevance score, or {@code null} if no score has been assigned.
     */
    public Double score() {
        return score;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ReRankedContent that = (ReRankedContent) o;
        return Objects.equals(score, that.score);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), score);
    }

    @Override
    public String toString() {
        return "ReRankedContent{" +
                "score=" + score +
                '}';
    }

    /**
     * Static factory method for creating a {@link ReRankedContent} instance.
     *
     * @param textSegment A semantically meaningful segment of text.
     * @param score       The relevance score for the content.
     * @return A new instance of {@link ReRankedContent}.
     */
    public static ReRankedContent from(TextSegment textSegment, Double score) {
        return new ReRankedContent(textSegment, score);
    }

}
