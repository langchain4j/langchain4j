package dev.langchain4j.data.embedding;

import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.RelevanceScore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a dense vector embedding of a text.
 * This class encapsulates a float array that captures the "meaning" or semantic information of the text.
 * Texts with similar meanings will have their vectors located close to each other in the embedding space.
 * The embeddings are typically created by embedding models.
 *
 * @see dev.langchain4j.model.embedding.EmbeddingModel
 */
public class Embedding {

    private final float[] vector;

    public Embedding(float[] vector) {
        this.vector = ensureNotNull(vector, "vector");
    }

    public float[] vector() {
        return vector;
    }

    public List<Float> vectorAsList() {
        List<Float> list = new ArrayList<>(vector.length);
        for (float f : vector) {
            list.add(f);
        }
        return list;
    }

    public int dimension() {
        return vector.length;
    }

    /**
     * Return the L2 Norm of the feature vector.
     *
     * <code>{@pre Sqrt(Sum(f[i] ** 2))}</code>
     *
     * @return float the norm.
     */
    public float magnitude() {
        float sumOfSquares = 0;
        for (float v : vector) {
            sumOfSquares += v * v;
        }
        return (float) Math.sqrt(sumOfSquares);
    }

    /**
     * Calculates the cosine similarity between the current instance and a provided {@link Embedding}.
     * <p>
     * Not to be confused with cosine distance (in the range [0, 2]), which quantifies how different two vectors are.
     * <p>
     * Embeddings of all-zeros vectors are considered orthogonal to all other vectors;
     * including other all-zeros vectors.
     *
     * @param embedding The {@link Embedding} with which to calculate the cosine similarity.
     * @return A cosine similarity in the range of [-1, 1], where 1 indicates identical vectors,
     * 0 indicates orthogonality (no similarity), and -1 indicates completely opposite vectors.
     */
    public float cosineSimilarity(Embedding embedding) {
        return (float) CosineSimilarity.between(this, embedding);
    }

    /**
     * Calculates the relevance score between the current instance and a provided {@link Embedding}.
     * This method derives the relevance score from the cosine similarity, effectively scaling
     * the cosine similarity range of [-1, 1] to [0, 1] for ease of use.
     *
     * @param embedding The {@link Embedding} with which to calculate the relevance score.
     * @return A relevance score in the range [0, 1], where 0 indicates no relevance and 1 indicates high relevance.
     */
    public float relevanceScore(Embedding embedding) {
        return (float) RelevanceScore.fromCosineSimilarity(cosineSimilarity(embedding));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Embedding that = (Embedding) o;
        return Arrays.equals(this.vector, that.vector);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vector);
    }

    @Override
    public String toString() {
        return "Embedding {" +
                " vector = " + Arrays.toString(vector) +
                " }";
    }

    public static Embedding from(float[] vector) {
        return new Embedding(vector);
    }

    public static Embedding from(List<? extends Number> vector) {
        float[] array = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            array[i] = vector.get(i).floatValue();
        }
        return new Embedding(array);
    }
}
