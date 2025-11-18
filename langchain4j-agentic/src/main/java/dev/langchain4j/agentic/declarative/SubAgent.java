package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Defines a sub-agent of a workflow-based or supervisor agent.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface SubAgent {

    /**
     * The class of the sub-agent.
     *
     * @return the class of the sub-agent
     */
    Class<?> type() default Object.class;

    /**
     * Names of other agents participating in the definition of the context of this agent.
     *
     * @return array of names of other agents participating in the definition of the context of this agent.
     */
    String[] summarizedContext() default {};
}
