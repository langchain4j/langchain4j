package dev.langchain4j.model.embedding.onnx.internal;

import dev.langchain4j.data.embedding.Embedding;

public class VectorUtils {

    public static float magnitudeOf(Embedding embedding) {
        return magnitudeOf(embedding.vector());
    }

    public static float magnitudeOf(float[] vector) {
        float sumOfSquares = 0.0f;
        for (float v : vector) {
            sumOfSquares += v * v;
        }
        return (float) Math.sqrt(sumOfSquares);
    }

    /**
     * Scales the vector to unit length, so that cosine similarity between two such vectors is their dot product.
     */
    public static float[] normalize(float[] vector) {
        float norm = magnitudeOf(vector);
        float[] normalizedVector = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalizedVector[i] = vector[i] / norm;
        }
        return normalizedVector;
    }
}
