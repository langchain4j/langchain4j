package dev.langchain4j.reasoning;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import java.util.Objects;

/**
 * Represents a match from a {@link ReasoningBank} retrieval operation.
 * Contains the matched strategy along with its similarity score and embedding.
 *
 * @since 1.11.0
 */
@Experimental
public class ReasoningMatch {

    private final String id;
    private final ReasoningStrategy strategy;
    private final Embedding embedding;
    private final double score;

    /**
     * Creates a new reasoning match.
     *
     * @param id        The ID of the stored strategy.
     * @param strategy  The matched strategy.
     * @param embedding The embedding of the strategy.
     * @param score     The similarity score (0.0-1.0).
     */
    public ReasoningMatch(String id, ReasoningStrategy strategy, Embedding embedding, double score) {
        this.id = ensureNotNull(id, "id");
        this.strategy = ensureNotNull(strategy, "strategy");
        this.embedding = embedding;
        this.score = score;
    }

    /**
     * Returns the ID of the stored strategy.
     *
     * @return The ID.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the matched strategy.
     *
     * @return The strategy.
     */
    public ReasoningStrategy strategy() {
        return strategy;
    }

    /**
     * Returns the embedding of the strategy.
     *
     * @return The embedding, may be null.
     */
    public Embedding embedding() {
        return embedding;
    }

    /**
     * Returns the similarity score.
     *
     * @return The score between 0.0 and 1.0.
     */
    public double score() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReasoningMatch that = (ReasoningMatch) o;
        return Double.compare(score, that.score) == 0
                && Objects.equals(id, that.id)
                && Objects.equals(strategy, that.strategy)
                && Objects.equals(embedding, that.embedding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, strategy, embedding, score);
    }

    @Override
    public String toString() {
        return "ReasoningMatch{" + "id='" + id + '\'' + ", strategy=" + strategy + ", score=" + score + '}';
    }
}
