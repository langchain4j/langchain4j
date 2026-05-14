package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as the compensating action for a {@link Tool}.
 * When an AI Service is configured as transactional, if any tool execution fails,
 * all previously executed tools' compensating actions are called in reverse order
 * to undo their effects.
 * The annotated method must have the same parameter types as the tool it compensates for,
 * or accept a single {@code ToolExecution} parameter.
 *
 * @since 1.15.0
 */
@Experimental
@Retention(RUNTIME)
@Target({METHOD})
public @interface CompensateFor {

    /**
     * The name of the tool that this method compensates for.
     * This should match either the {@link Tool#name()} or the method name
     * of the {@code @Tool}-annotated method.
     *
     * @return the name of the tool to compensate for.
     */
    String value();
}
