package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a parameter as the A2A message task id.
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface A2ATaskId {}
