package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Utility class for calculating cosine similarity between two vectors.
 */
public class CosineSimilarity {
    private CosineSimilarity() {}

    /**
     * A small value to avoid division by zero.
     */
    public static final float EPSILON = 1e-8f;

    /**
     * Calculates cosine similarity between two vectors.
     * <p>
     * Cosine similarity measures the cosine of the angle between two vectors, indicating their directional similarity.
     * It produces a value in the range:
     * <p>
     * -1 indicates vectors are diametrically opposed (opposite directions).
     * <p>
     * 0 indicates vectors are orthogonal (no directional similarity).
     * <p>
     * 1 indicates vectors are pointing in the same direction (but not necessarily of the same magnitude).
     * <p>
     * Not to be confused with cosine distance ([0..2]), which quantifies how different two vectors are.
     * <p>
     * Embeddings of all-zeros vectors are considered orthogonal to all other vectors;
     * including other all-zeros vectors.
     *
     * @param embeddingA first embedding vector
     * @param embeddingB second embedding vector
     * @return cosine similarity in the range [-1..1]
     */
    public static double between(Embedding embeddingA, Embedding embeddingB) {
        ensureNotNull(embeddingA, "embeddingA");
        ensureNotNull(embeddingB, "embeddingB");

        float[] vectorA = embeddingA.vector();
        float[] vectorB = embeddingB.vector();

        if (vectorA.length != vectorB.length) {
            throw illegalArgument("Length of vector a (%s) must be equal to the length of vector b (%s)",
                    vectorA.length, vectorB.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        // Avoid division by zero.
        return dotProduct / Math.max(Math.sqrt(normA) * Math.sqrt(normB), EPSILON);
    }

    /**
     * Converts relevance score into cosine similarity.
     *
     * @param relevanceScore Relevance score in the range [0..1] where 0 is not relevant and 1 is relevant.
     * @return Cosine similarity in the range [-1..1] where -1 is not relevant and 1 is relevant.
     */
    public static double fromRelevanceScore(double relevanceScore) {
        return relevanceScore * 2 - 1;
    }
}
