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
}
