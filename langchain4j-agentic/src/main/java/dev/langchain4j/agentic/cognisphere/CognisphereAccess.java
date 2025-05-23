package dev.langchain4j.agentic.cognisphere;

/**
 * Allow to access the {@link Cognisphere} of any agent extending it.
 */
public interface CognisphereAccess {

    /**
     * Returns the {@link Cognisphere} with the given id for this AI service or null if such memory doesn't exist.
     *
     * @param memoryId The id of the {@link Cognisphere}.
     * @return The {@link Cognisphere} with the given memoryId or null if such memory doesn't exist.
     */
    Cognisphere getCognisphere(Object memoryId);

    /**
     * Evicts the {@link Cognisphere} with the given id.
     *
     * @param memoryId The id of the {@link Cognisphere} to be evicted.
     * @return true if {@link Cognisphere} with the given id existed, and it was successfully evicted, false otherwise.
     */
    boolean evictCognisphere(Object memoryId);
}
