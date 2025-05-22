package dev.langchain4j.store.embedding.oceanbase.distance;

/**
 * Converts euclidean distance to similarity score.
 * For euclidean distance, similarity = 1 / (1 + distance)
 */
public class EuclideanDistanceConverter implements DistanceConverter {
    
    @Override
    public double toSimilarity(double distance) {
        return 1.0 / (1.0 + distance);
    }
}
