package dev.langchain4j.model.embedding.onnx.internal;

import dev.langchain4j.data.embedding.Embedding;

public final class VectorUtils {
    private VectorUtils() {}

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

    public static float[] normalize(float[] vector) {
        float sumSquare = 0;
        for (float v : vector) {
            sumSquare += v * v;
        }
        float norm = (float) Math.sqrt(sumSquare);
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }
        return normalized;
    }
}
