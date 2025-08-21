package dev.langchain4j.agentic.scope;

/**
 * Allow to access the {@link AgenticScope} of any agent extending it.
 */
public interface AgenticScopeAccess {

    /**
     * Returns the {@link AgenticScope} with the given id for this AI service or null if such memory doesn't exist.
     *
     * @param memoryId The id of the {@link AgenticScope}.
     * @return The {@link AgenticScope} with the given memoryId or null if such memory doesn't exist.
     */
    AgenticScope getAgenticScope(Object memoryId);

    /**
     * Evicts the {@link AgenticScope} with the given id.
     *
     * @param memoryId The id of the {@link AgenticScope} to be evicted.
     * @return true if {@link AgenticScope} with the given id existed, and it was successfully evicted, false otherwise.
     */
    boolean evictAgenticScope(Object memoryId);
}
