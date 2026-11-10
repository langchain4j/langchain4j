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
 * <p>
 * The method is invoked when the client is first needed, which is normally the first invocation of the agent, and
 * not when the agentic system is built, because creating an MCP client normally connects to the MCP server. Its
 * parameters are however resolved when the agent is built, like those of the other supplier methods, and its declared
 * return type is checked at that moment too: it must be the McpClient, a subtype, or a supertype such as Object, in
 * which case the instance it returns is checked when the client is created. The method runs on the thread that first
 * needs the client, and the client it returns is then used by every invocation of the agent, so it must not depend on
 * per-request or per-user state. When it fails, it is invoked again the next time the client is needed, and a
 * {@code DefaultMcpClient} that fails to connect stops its transport, so this method can reuse one or create it inside.
 * The agent never closes the client.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface McpClientSupplier {
}
