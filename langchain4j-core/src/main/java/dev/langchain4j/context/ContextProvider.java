package dev.langchain4j.context;

import dev.langchain4j.Experimental;
import dev.langchain4j.rag.content.Content;

import java.util.List;

/**
 * Provides contextual {@link Content} for a given {@link ContextRequest}.
 * <p>
 * A {@code ContextProvider} represents a single dimension of context, such as:
 * <ul>
 *   <li>Pre-loaded domain knowledge (see {@link StaticContextProvider})</li>
 *   <li>User identity and role information</li>
 *   <li>Session or conversation state</li>
 *   <li>Domain rules and policies</li>
 *   <li>Temporal or workflow state</li>
 * </ul>
 * <p>
 * Multiple {@code ContextProvider}s can be composed via a {@link ContextManager}
 * to assemble multi-dimensional context for each request.
 * <p>
 * Implementations should be stateless or thread-safe.
 *
 * @see StaticContextProvider
 * @see InvocationParameterContextProvider
 * @see ContextManager
 */
@Experimental
public interface ContextProvider {

    /**
     * Provides contextual {@link Content} for the given request.
     * <p>
     * Implementations may return an empty list when no context is applicable,
     * enabling graceful degradation.
     *
     * @param request the context request containing the user's message and invocation metadata
     * @return a list of {@link Content} representing the context; may be empty, must not be {@code null}
     */
    List<Content> provideContext(ContextRequest request);

    /**
     * Returns a human-readable name for this provider, used in logging and observability.
     *
     * @return the name of this context provider
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
