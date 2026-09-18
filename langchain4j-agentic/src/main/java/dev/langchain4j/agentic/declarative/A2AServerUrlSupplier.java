package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a static method that returns the A2A server URL for declarative A2A client agents.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface A2AServerUrlSupplier {
}
