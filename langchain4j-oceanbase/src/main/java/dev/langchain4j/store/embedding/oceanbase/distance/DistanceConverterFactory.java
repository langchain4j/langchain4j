package dev.langchain4j.store.embedding.oceanbase.distance;

/**
 * Factory for creating appropriate distance converters based on the metric type.
 * Implements the Factory Method pattern.
 */
public class DistanceConverterFactory {
    
    /**
     * Returns the appropriate converter for the given distance metric.
     * 
     * @param metric The distance metric name (e.g., "cosine", "euclidean")
     * @return A DistanceConverter appropriate for the given metric
     */
    public static DistanceConverter getConverter(String metric) {
        if (metric == null) {
            return new DefaultDistanceConverter();
        }
        
        metric = metric.toLowerCase();
        
        switch (metric) {
            case "cosine":
                return new CosineDistanceConverter();
            case "euclidean":
                return new EuclideanDistanceConverter();
            default:
                return new DefaultDistanceConverter();
        }
    }
}
