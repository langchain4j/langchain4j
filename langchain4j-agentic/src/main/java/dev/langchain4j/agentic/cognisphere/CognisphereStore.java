package dev.langchain4j.agentic.cognisphere;

import java.util.Optional;
import java.util.Set;

/**
 * Service Provider Interface for Cognisphere persistence.
 * Implementations must provide ways to store and retrieve Cognisphere instances.
 */
public interface CognisphereStore {

    /**
     * Saves or updates a Cognisphere instance.
     *
     * @param cognisphere the Cognisphere to persist
     * @return true if the operation was successful
     */
    boolean save(DefaultCognisphere cognisphere);

    /**
     * Loads a Cognisphere by its ID.
     *
     * @param key the ID of the Cognisphere to load
     * @return an Optional containing the Cognisphere if found, empty otherwise
     */
    Optional<DefaultCognisphere> load(CognisphereKey key);

    /**
     * Deletes a Cognisphere by its ID.
     *
     * @param key the ID of the Cognisphere to delete
     * @return true if the Cognisphere was found and deleted
     */
    boolean delete(CognisphereKey key);

    /**
     * Gets all available Cognisphere .
     *
     * @return a Set of all Cognisphere keys in the persistence store
     */
    Set<CognisphereKey> getAllKeys();
}
