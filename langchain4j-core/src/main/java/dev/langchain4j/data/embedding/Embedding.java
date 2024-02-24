package dev.langchain4j.data.embedding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a dense vector embedding of a text.
 * This class encapsulates a float array that captures the "meaning" or semantic information of the text.
 * Texts with similar meanings will have their vectors located close to each other in the embedding space.
 * The embeddings are typically created by embedding models.
 * @see dev.langchain4j.model.embedding.EmbeddingModel
 */
public class Embedding {

    private final float[] vector;

    /**
     * Creates a new Embedding.
     * @param vector the vector, takes ownership of the array.
     */
    public Embedding(float[] vector) {
        this.vector = ensureNotNull(vector, "vector");
    }

    /**
     * Returns the vector.
     * @return the vector.
     */
    public float[] vector() {
        return vector;
    }

    /**
     * Returns a copy of the vector as a list.
     * @return the vector as a list.
     */
    public List<Float> vectorAsList() {
        List<Float> list = new ArrayList<>(vector.length);
        for (float f : vector) {
            list.add(f);
        }
        return list;
    }

    /**
     * Normalize vector
     */
    public void normalize() {
        double norm = 0.0;
        for (float f : vector) {
            norm += f * f;
        }
        norm = Math.sqrt(norm);

        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    /**
     * Returns the dimension of the vector.
     * @return the dimension of the vector.
     */
    public int dimension() {
        return vector.length;
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

    /**
     * Creates a new Embedding from the given vector.
     * @param vector the vector, takes ownership of the array.
     * @return the new Embedding.
     */
    public static Embedding from(float[] vector) {
        return new Embedding(vector);
    }

    /**
     * Creates a new Embedding from the given vector.
     * @param vector the vector.
     * @return the new Embedding.
     */
    public static Embedding from(List<Float> vector) {
        float[] array = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            array[i] = vector.get(i);
        }
        return new Embedding(array);
    }
}
