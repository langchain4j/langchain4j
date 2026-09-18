package dev.langchain4j.agentic.declarative;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a system message provider for an agent.
 * The annotated method must be static, accept an {@code Object} parameter (the memoryId),
 * and return a {@code String} (the system message).
 * <p>
 * This provides a declarative alternative to programmatically calling
 * {@link dev.langchain4j.agentic.agent.AgentBuilder#systemMessageProvider(java.util.function.Function)},
 * and is useful when the system message needs to be dynamically computed at runtime.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface DynamicAgent {
 *
 *         @Agent("An agent with a dynamic system message")
 *         String chat(@V("request") String request);
 *
 *         @SystemMessageProviderSupplier
 *         static String systemMessageProvider(Object memoryId) {
 *             return "You are an assistant for user " + memoryId;
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface SystemMessageProviderSupplier {
}
