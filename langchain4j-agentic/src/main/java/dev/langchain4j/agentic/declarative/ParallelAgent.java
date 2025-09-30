package dev.langchain4j.agentic.declarative;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a definition of a parallel agent, used to orchestrate the agentic workflow
 * by invoking a series of sub-agents in parallel.
 * Each sub-agent is defined using the {@link SubAgent} annotation, which specifies the sub-agent's type
 * and its output variable name.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface EveningPlannerAgent {
 *
 *         @ParallelAgent(outputName = "plans", subAgents = {
 *                 @SubAgent(type = FoodExpert.class, outputName = "meals"),
 *                 @SubAgent(type = MovieExpert.class, outputName = "movies")
 *         })
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
     * Name of the output variable that will hold the result of the agent invocation.
     *
     * @return name of the output variable.
     */
    String outputName() default "";

    /**
     * Array of sub-agents that will be invoked in parallel.
     *
     * @return array of sub-agents.
     */
    SubAgent[] subAgents();
}
