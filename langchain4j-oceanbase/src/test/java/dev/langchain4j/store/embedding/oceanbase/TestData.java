package dev.langchain4j.store.embedding.oceanbase;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import java.util.Map;
import java.util.Random;

/**
 * Utility class for generating test data.
 */
public class TestData {

    private static final Random random = new Random(666);

    /**
     * Creates a random embedding with specified dimension.
     *
     * @param dimension Vector dimension.
     * @return A random embedding.
     */
    public static Embedding randomEmbedding(int dimension) {
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = random.nextFloat();
        }
        return new Embedding(vector);
    }

    /**
     * Creates a random embedding with default dimension (3).
     *
     * @return A random embedding with default dimension.
     */
    public static Embedding randomEmbedding() {
        return randomEmbedding(3);
    }

    /**
     * Creates an array of sample embeddings and their corresponding text segments.
     * The sample data mimics fruits and vegetables with their vector representation.
     *
     * @return An array of sample embeddings.
     */
    public static Embedding[] sampleEmbeddings() {
        return new Embedding[]{
                new Embedding(new float[]{1.2f, 0.7f, 1.1f}),
                new Embedding(new float[]{0.6f, 1.2f, 0.8f}),
                new Embedding(new float[]{1.1f, 1.1f, 0.9f}),
                new Embedding(new float[]{5.3f, 4.8f, 5.4f}),
                new Embedding(new float[]{4.9f, 5.3f, 4.8f}),
                new Embedding(new float[]{5.2f, 4.9f, 5.1f})
        };
    }

    /**
     * Creates an array of sample text segments.
     *
     * @return An array of sample text segments.
     */
    public static TextSegment[] sampleTextSegments() {
        return new TextSegment[]{
                TextSegment.from("苹果", Metadata.from(Map.of("type", "fruit", "color", "red"))),
                TextSegment.from("香蕉", Metadata.from(Map.of("type", "fruit", "color", "yellow"))),
                TextSegment.from("橙子", Metadata.from(Map.of("type", "fruit", "color", "orange"))),
                TextSegment.from("胡萝卜", Metadata.from(Map.of("type", "vegetable", "color", "orange"))),
                TextSegment.from("菠菜", Metadata.from(Map.of("type", "vegetable", "color", "green"))),
                TextSegment.from("西红柿", Metadata.from(Map.of("type", "vegetable", "color", "red")))
        };
    }

    /**
     * Creates a query embedding for testing similarity search.
     *
     * @return A query embedding.
     */
    public static Embedding queryEmbedding() {
        return new Embedding(new float[]{0.9f, 1.0f, 0.9f});
    }
}
