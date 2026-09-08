package dev.langchain4j.agentic;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.declarative.TypedKey;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Java methods annotated with {@code @Agent} are considered agents that other agents can invoke.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface Agent {

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
     * Strongly typed key of the output variable that will be used to store
     * the result of this agent's invocation in the {@link AgenticScope}.
     * <p>
     * This attribute provides a type-safe alternative to {@link #outputKey()}.
     * Instead of identifying the output variable using a {@link String},
     * the output is identified by a class implementing {@link TypedKey}.
     * </p>
     *
     * <p>
     * The generic type declared by the {@link TypedKey} represents the type
     * of value associated with the output variable.
     * </p>
     *
     * <p>
     * For example:
     * </p>
     *
     * <pre>{@code
     * public class Category implements TypedKey<RequestCategory> {
     * }
     *
     * @Agent(
     *         name = "categoryRouter",
     *         typedOutputKey = Category.class
     * )
     * RequestCategory classify(String request);
     * }</pre>
     *
     * <p>
     * When the agent completes successfully, its returned
     * {@code RequestCategory} value is stored in the agentic scope using
     * {@code Category.class} as the typed state key.
     * </p>
     *
     * <p>
     * The value can later be retrieved without using a string key and
     * without requiring an explicit type cast:
     * </p>
     *
     * <pre>{@code
     * RequestCategory category =
     *         scope.readState(Category.class);
     * }</pre>
     *
     * <p>
     * This is particularly useful in multi-agent workflows where the output
     * of one agent becomes the input of another agent. The typed key acts as
     * both:
     * </p>
     *
     * <ul>
     *     <li>the identity of the state variable, and</li>
     *     <li>the declaration of the Java type associated with that variable.</li>
     * </ul>
     *
     * <p>
     * Multiple typed keys may use the same Java value type while still
     * representing different state variables. For example:
     * </p>
     *
     * <pre>{@code
     * public class InitialCategory
     *         implements TypedKey<RequestCategory> {
     * }
     *
     * public class ValidatedCategory
     *         implements TypedKey<RequestCategory> {
     * }
     * }</pre>
     *
     * <p>
     * Even though both keys contain {@code RequestCategory} values,
     * {@code InitialCategory.class} and {@code ValidatedCategory.class}
     * identify two independent variables in the agentic scope.
     * </p>
     *
     * <p>
     * {@code typedOutputKey} and {@link #outputKey()} are alternative ways
     * of defining the output variable. Only one of them should be configured
     * for an agent:
     * </p>
     *
     * <ul>
     *     <li>
     *         {@link #outputKey()} identifies the output using a
     *         {@link String}.
     *     </li>
     *     <li>
     *         {@code typedOutputKey} identifies the output using a
     *         {@link TypedKey} implementation and preserves its associated
     *         Java type.
     *     </li>
     * </ul>
     *
     * <p>
     * If no typed output key is configured, {@link NoTypedKey} is used as
     * the default sentinel value, indicating that the agent does not declare
     * its output through a typed key.
     * </p>
     *
     * @return the {@link TypedKey} class identifying the strongly typed
     *         output variable where the result of this agent invocation
     *         will be stored
     *
     * @see TypedKey
     * @see #outputKey()
     */
    Class<? extends TypedKey<?>> typedOutputKey() default NoTypedKey.class;

    /**
     * If true, the agent will be invoked in an asynchronous manner, allowing the workflow to continue without waiting for the agent's result.
     *
     * @return true if the agent should be invoked in an asynchronous manner, false otherwise.
     */
    boolean async() default false;

    /**
     * If true, the agent's execution will be silently skipped when any of its arguments is missing in the agentic scope,
     * instead of making the agentic system's execution fail.
     *
     * @return true if the agent is optional, false otherwise.
     */
    boolean optional() default false;

    /**
     * If true, all previously successful tool invocations with {@code @CompensateFor} actions will be
     * compensated in reverse order when any tool in this agent fails.
     *
     * @return true if cross-agent compensation should be enabled, false otherwise.
     */
    boolean compensateOnError() default false;

    /**
     * Names of other agents participating in the definition of the context of this agent.
     *
     * @return array of names of other agents participating in the definition of the context of this agent.
     */
    String[] summarizedContext() default {};

    class NoTypedKey implements TypedKey<Void> {}
}
