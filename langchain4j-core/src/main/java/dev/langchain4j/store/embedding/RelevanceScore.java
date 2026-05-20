package dev.langchain4j.store.embedding;

/**
 * Utility class for converting between various distance/similarity metrics and
 * relevance score.
 */
public class RelevanceScore {
    private RelevanceScore() {
    }

    /**
     * Converts cosine similarity into relevance score.
     *
     * @param cosineSimilarity Cosine similarity in the range [-1..1] where -1 is
     *                         not relevant and 1 is relevant.
     * @return Relevance score in the range [0..1] where 0 is not relevant and 1 is
     *         relevant.
     */
    public static double fromCosineSimilarity(double cosineSimilarity) {
        return (cosineSimilarity + 1) / 2;
    }

    /**
     * Converts L2 distance into relevance score.
     *
     * @param l2Distance L2 distance in the range [0..+∞) where 0 is most relevant.
     * @return Relevance score in the range [0..1] where 0 is not relevant and 1 is
     *         relevant.
     */
    public static double fromL2Distance(double l2Distance) {
        return 1.0 / (1.0 + l2Distance);
    }
}