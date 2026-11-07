package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.AgenticServices;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a static method that returns the McpClient instance for declarative MCP client agents.
 * It can only declare parameters resolved through a {@link SupplierParameterResolver}.
 * <p>
 * Like every helper method, it can be an instance method of a helper object of the agent instead of a static method
 * of the agent type: see the "Helper objects" section of the agentic tutorial and
 * {@link AgenticServices.AgentConfigurator}.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface McpClientSupplier {
}
