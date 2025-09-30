package dev.langchain4j.agentic.scope;

import java.util.Optional;
import java.util.Set;

/**
 * Service Provider Interface for AgenticScope persistence.
 * Implementations must provide ways to store and retrieve AgenticScope instances.
 */
public interface AgenticScopeStore {

    /**
     * Saves or updates a AgenticScope instance.
     *
     * @param agenticScope the AgenticScope to persist
     * @return true if the operation was successful
     */
    boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope);

    /**
     * Loads a AgenticScope by its ID.
     *
     * @param key the ID of the AgenticScope to load
     * @return an Optional containing the AgenticScope if found, empty otherwise
     */
    Optional<DefaultAgenticScope> load(AgenticScopeKey key);

    /**
     * Deletes a AgenticScope by its ID.
     *
     * @param key the ID of the AgenticScope to delete
     * @return true if the AgenticScope was found and deleted
     */
    boolean delete(AgenticScopeKey key);

    /**
     * Gets all available AgenticScope .
     *
     * @return a Set of all AgenticScope keys in the persistence store
     */
    Set<AgenticScopeKey> getAllKeys();
}
