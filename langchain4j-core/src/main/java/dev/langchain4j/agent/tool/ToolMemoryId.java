package dev.langchain4j.agent.tool;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Memory id passed to the tool internally during execution.
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface ToolMemoryId { }
