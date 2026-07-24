package dev.langchain4j.context;

import dev.langchain4j.Experimental;

/**
 * Orchestrates context resolution from multiple {@link ContextProvider}s.
 * <p>
 * The {@code ContextManager} is the central concept in the Context-Augmented Generation (CAG) pattern.
 * Its responsibility is to assemble and normalize context from multiple sources,
 * not to interpret it (that is the LLM's job).
 *
 * @see DefaultContextManager
 * @see ContextProvider
 * @see ContextResult
 */
@Experimental
public interface ContextManager {

    /**
     * Resolves context for the given request by consulting the registered {@link ContextProvider}s.
     *
     * @param request the context request
     * @return the resolved context
     */
    ContextResult resolveContext(ContextRequest request);
}
