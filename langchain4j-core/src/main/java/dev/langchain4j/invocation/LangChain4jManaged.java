package dev.langchain4j.invocation;

import dev.langchain4j.Internal;

/**
 * A marker interface for components that are managed by LangChain4j framework.
 * <p>
 * Implementing this interface indicates that the component is internally managed by LangChain4j,
 * and doesn't require to be instatiated or passed around by the user.
 *
 * @since 1.8.0
 */
@Internal
public interface LangChain4jManaged {
}
