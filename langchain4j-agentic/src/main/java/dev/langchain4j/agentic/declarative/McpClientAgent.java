package dev.langchain4j.agentic.declarative;

import dev.langchain4j.agentic.Agent;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP client agent that wraps a single MCP tool as a non-AI agent.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface McpClientAgent {

    /**
     * Name of the MCP tool to bind and invoke as non-AI agent.
     *
     * @return name of the MCP tool.
     */
    String toolName() default "";

    /**
     * Name of the agent. If not provided, method name will be used.
     *
     * @return name of the agent.
     */
    String name() default "";

    /**
     * Description of the agent. This is an alias of the {@code description} attribute, and it is possible to use either.
     * It should be clear and descriptive to allow language model to understand the agent's purpose and its intended use.
     *
     * @return description of the agent.
     */
    String value() default "";

    /**
     * Description of the agent. This is an alias of the {@code value} attribute, and it is possible to use either.
     * It should be clear and descriptive to allow language model to understand the agent's purpose and its intended use.
     *
     * @return description of the agent.
     */
    String description() default "";

    /**
     * Key of the output variable that will be used to store the result of the agent's invocation.
     *
     * @return name of the output variable.
     */
    String outputKey() default "";

    /**
     * Strongly typed key of the output variable that will be used to store the result of the agent's invocation.
     * It enforces type safety when retrieving the output from the agent's state and can be used in alternative
     * to the {@code outputKey()} attribute. Note that only one of those two attributes can be used at a time.
     *
     * @return class representing the typed output variable.
     */
    Class<? extends TypedKey<?>> typedOutputKey() default Agent.NoTypedKey.class;

    /**
     * If true, the agent will be invoked in an asynchronous manner, allowing the workflow to continue without waiting for the agent's result.
     *
     * @return true if the agent should be invoked in an asynchronous manner, false otherwise.
     */
    boolean async() default false;
}
