package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as the compensating action for a {@link Tool}.
 * When compensation is enabled with {@code .compensateOnToolErrors(true)}, if a tool fails
 * (also when its error ends the invocation) or the invocation is cancelled, the compensating
 * actions of the tool calls that succeeded in the same invocation are called in reverse order
 * to undo their effects.
 * The annotated method must have the same parameter types as the tool it compensates for,
 * or accept a single {@code ToolExecution} parameter.
 * It usually returns {@code void}; when it returns a {@code CompletableFuture}, a {@code CompletionStage} or another
 * asynchronous type that AI Services support for tools through an adapter, the rollback waits until it completes
 * before it runs the next compensating action.
 * <p>
 * Only {@link Tool @Tool}-annotated methods support compensating actions. Programmatically
 * or dynamically defined tools (e.g. MCP tools, tools registered via {@code ToolSpecification})
 * are not supported.
 *
 * @since 1.17.0
 */
@Experimental
@Retention(RUNTIME)
@Target({METHOD})
public @interface CompensateFor {

    /**
     * The name of the tool that this method compensates for, as it is exposed to the LLM.
     * This is the {@link Tool#name()} when it is explicitly set, otherwise the name of the
     * {@code @Tool}-annotated method (which is used as the tool name by default).
     *
     * @return the name of the tool to compensate for.
     */
    String value();
}
