package dev.langchain4j.store.embedding;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class Similarity {

    /**
     * Calculates cosine similarity between two vectors.
     * <p>
     * Cosine similarity measures the cosine of the angle between two vectors, indicating their directional similarity.
     * It produces a value in the range:
     * - -1 indicates vectors are diametrically opposed (opposite directions).
     * - 0 indicates vectors are orthogonal (no directional similarity).
     * - 1 indicates vectors are pointing in the same direction (but not necessarily of the same magnitude).
     * <p>
     * Not to be confused with cosine distance ([0..2]), which quantifies how different two vectors are.
     *
     * @param a first vector
     * @param b second vector
     * @return cosine similarity in the range [-1..1]
     */
    public static double cosine(float[] a, float[] b) {
        ensureNotNull(a, "a");
        ensureNotNull(b, "b");
        if (a.length != b.length) {
            throw illegalArgument("Length of vector a (%s) must be equal to the length of vector b (%s)",
                    a.length, b.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
