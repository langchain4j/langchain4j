package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a static method that provides input when a declarative A2A client agent is interrupted.
 * <p>
 * The annotated method must take a single
 * {@code dev.langchain4j.agentic.a2a.A2ATaskInterruptedException} parameter and return a
 * {@code String}. The returned text is sent to the same remote task and context.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface A2AInputProvider {}
