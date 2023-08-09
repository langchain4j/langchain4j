package dev.langchain4j.store.embedding;

public class RelevanceScore {

    /**
     * Calculates the relevance score between two vectors using cosine similarity.
     *
     * @param a first vector
     * @param b second vector
     * @return score in the range [0, 1], where 0 indicates no relevance and 1 indicates full relevance
     */
    public static double cosine(float[] a, float[] b) {
        double cosineSimilarity = Similarity.cosine(a, b);
        return 1 - (1 - cosineSimilarity) / 2;
    }
}
