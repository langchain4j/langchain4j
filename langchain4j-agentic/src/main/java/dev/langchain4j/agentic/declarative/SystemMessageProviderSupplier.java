package dev.langchain4j.agentic.declarative;

import dev.langchain4j.agentic.AgenticServices;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a system message provider for an agent.
 * The annotated method must be static on the agent type, accept an {@code Object} parameter (the memoryId),
 * and return a {@code String} (the system message).
 * Besides the memoryId, it can declare parameters resolved through a {@link SupplierParameterResolver}.
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
 * <p>
 * Like every helper method, it can be an instance method of a helper object of the agent instead of a static method
 * of the agent type: see the "Helper objects" section of the agentic tutorial and
 * {@link AgenticServices.AgentConfigurator}.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface SystemMessageProviderSupplier {
}
