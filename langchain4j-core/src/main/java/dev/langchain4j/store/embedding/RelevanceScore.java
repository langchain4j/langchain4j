package dev.langchain4j.store.embedding;

/**
 * Utility class for converting between cosine similarity and relevance score.
 */
public class RelevanceScore {
    private RelevanceScore() {}

    /**
     * Converts cosine similarity into relevance score.
     *
     * @param cosineSimilarity Cosine similarity in the range [-1..1] where -1 is not relevant and 1 is relevant.
     * @return Relevance score in the range [0..1] where 0 is not relevant and 1 is relevant.
     */
    public static double fromCosineSimilarity(double cosineSimilarity) {
        return (cosineSimilarity + 1) / 2;
    }
}
