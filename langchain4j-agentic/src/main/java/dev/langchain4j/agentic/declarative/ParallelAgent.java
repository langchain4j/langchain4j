package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a definition of a parallel agent, used to orchestrate the agentic workflow
 * by invoking a series of sub-agents in parallel.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface EveningPlannerAgent {
 *
 *         @ParallelAgent( outputKey = "plans",
 *                 subAgents = { FoodExpert.class, MovieExpert.class })
 *         List<EveningPlan> plan(@V("mood") String mood);
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ParallelAgent {

    /**
     * Name of the agent. If not provided, method name will be used.
     *
     * @return name of the agent.
     */
    String name() default "";

    /**
     * Description of the agent.
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
     * Array of sub-agents that will be invoked in parallel.
     *
     * @return array of sub-agents.
     */
    Class<?>[] subAgents();
}
