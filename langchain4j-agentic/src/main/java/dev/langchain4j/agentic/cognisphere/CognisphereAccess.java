package dev.langchain4j.agentic.cognisphere;

/**
 * Allow to access the {@link DefaultCognisphere} of any agent extending it.
 */
public interface CognisphereAccess {

    /**
     * Returns the {@link DefaultCognisphere} with the given id for this AI service or null if such memory doesn't exist.
     *
     * @param memoryId The id of the {@link DefaultCognisphere}.
     * @return The {@link DefaultCognisphere} with the given memoryId or null if such memory doesn't exist.
     */
    Cognisphere getCognisphere(Object memoryId);

    /**
     * Evicts the {@link DefaultCognisphere} with the given id.
     *
     * @param memoryId The id of the {@link DefaultCognisphere} to be evicted.
     * @return true if {@link DefaultCognisphere} with the given id existed, and it was successfully evicted, false otherwise.
     */
    boolean evictCognisphere(Object memoryId);
}
