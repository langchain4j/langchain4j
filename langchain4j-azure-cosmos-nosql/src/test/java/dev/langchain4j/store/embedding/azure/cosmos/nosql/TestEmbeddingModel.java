package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simple test embedding model that generates random embeddings for testing purposes.
 * This avoids issues with native library dependencies in test environments.
 */
public class TestEmbeddingModel implements EmbeddingModel {

    private final int dimension;
    private final Random random;

    public TestEmbeddingModel() {
        this(384); // Default dimension similar to AllMiniLmL6V2
    }

    public TestEmbeddingModel(int dimension) {
        this.dimension = dimension;
        this.random = new Random(42); // Fixed seed for reproducible tests
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }

    @Override
    public Response<Embedding> embed(String text) {
        Random textRandom = new Random(text.hashCode());
        float[] vector = new float[dimension];

        for (int i = 0; i < dimension; i++) {
            vector[i] = (textRandom.nextFloat() - 0.5f) * 2.0f;
        }

        float norm = 0.0f;
        for (float value : vector) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }

        // Convert float[] to List<Float>
        List<Float> vectorList = new ArrayList<>(dimension);
        for (float v : vector) {
            vectorList.add(v);
        }

        return Response.from(Embedding.from(vectorList));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings =
                textSegments.stream().map(segment -> embed(segment).content()).toList();
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
