package dev.langchain4j.agentic.cognisphere;

import java.util.Optional;
import java.util.Set;

/**
 * Service Provider Interface for Cognisphere persistence.
 * Implementations must provide ways to store and retrieve Cognisphere instances.
 */
public interface CognispherePersistenceProvider {

    /**
     * Saves or updates a Cognisphere instance.
     *
     * @param cognisphere the Cognisphere to persist
     * @return true if the operation was successful
     */
    boolean save(Cognisphere cognisphere);

    /**
     * Loads a Cognisphere by its ID.
     *
     * @param id the ID of the Cognisphere to load
     * @return an Optional containing the Cognisphere if found, empty otherwise
     */
    Optional<Cognisphere> load(Object id);

    /**
     * Deletes a Cognisphere by its ID.
     *
     * @param id the ID of the Cognisphere to delete
     * @return true if the Cognisphere was found and deleted
     */
    boolean delete(Object id);

    /**
     * Gets all available Cognisphere IDs.
     *
     * @return a Set of all Cognisphere IDs in the persistence store
     */
    Set<Object> getAllIds();
}
