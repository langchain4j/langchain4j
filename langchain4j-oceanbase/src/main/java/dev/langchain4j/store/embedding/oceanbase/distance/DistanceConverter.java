package dev.langchain4j.store.embedding.oceanbase.distance;

/**
 * Strategy interface for converting distance values to similarity scores.
 * Different distance metrics require different conversion formulas.
 */
public interface DistanceConverter {
    
    /**
     * Converts a distance value to a similarity score.
     * 
     * @param distance The distance value to convert
     * @return A similarity score between 0 and 1, where 1 means exact match
     */
    double toSimilarity(double distance);
}
