package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a static method that returns the McpClient instance for declarative MCP client agents.
 * It can only declare parameters resolved through a {@link SupplierParameterResolver}.
 * <p>
 * The method is invoked when the client is first needed, which is normally the first invocation of the agent, and
 * not when the agentic system is built, because creating an MCP client normally connects to the MCP server. Its
 * parameters are however resolved when the agent is built, like those of the other supplier methods, and its declared
 * return type is checked at that moment too: it must be the McpClient, a subtype, or a supertype such as Object, in
 * which case the instance it returns is checked when the client is created.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface McpClientSupplier {
}
