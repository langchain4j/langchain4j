package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as an agent loaded from an {@link dev.langchain4j.agentic.planner.AgentsRegistry}.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface RegistryAgent {

    /**
     * Name of the agent in the registry.
     *
     * @return name of the agent in the registry.
     */
    String value();
}
