package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.Agent;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as an A2A client agent.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface A2AClientAgent {

    /**
     * URL of the A2A server to which the requests will be sent.
     * If not provided, a method annotated with {@link A2AServerUrlSupplier} must be present
     * on the same interface.
     *
     * @return URL of the A2A server.
     */
    String a2aServerUrl() default "";

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

    /**
     * Tenant identifier to include in every A2A message sent by this agent.
     * <p>
     * When set, the tenant is sent automatically via {@code MessageSendParams}
     * without exposing it to the LLM as a tool argument.
     * If left empty, the tenant is extracted from the agent card URL path
     * (pattern {@code /.well-known/{tenant}/agent-card.json}).
     *
     * @return the tenant identifier, or empty to auto-detect from the URL.
     */
    String tenant() default "";
}
