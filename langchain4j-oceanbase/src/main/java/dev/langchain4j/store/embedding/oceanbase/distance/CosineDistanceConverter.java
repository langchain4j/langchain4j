package dev.langchain4j.store.embedding.oceanbase.distance;

/**
 * Converts cosine distance to similarity score.
 * For cosine distance, similarity = 1 - distance
 */
public class CosineDistanceConverter implements DistanceConverter {
    
    @Override
    public double toSimilarity(double distance) {
        return 1.0 - distance;
    }
}
