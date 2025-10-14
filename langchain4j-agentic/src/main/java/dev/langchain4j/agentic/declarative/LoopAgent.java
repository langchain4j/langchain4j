package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a definition of a loop agent, used to orchestrate the agentic workflow
 * by invoking a series of sub-agents in a loop until a certain condition is met or a maximum number of iterations is reached.
 * Each sub-agent is defined using the {@link SubAgent} annotation, which specifies the sub-agent's type
 * and its output variable name.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface StyleReviewLoopAgentWithCounter {
 *
 *         @LoopAgent(
 *                 description = "Review the given story to ensure it aligns with the specified style",
 *                 outputKey = "story", maxIterations = 5,
 *                 subAgents = {
 *                     @SubAgent(type = StyleScorer.class, outputKey = "score"),
 *                     @SubAgent(type = StyleEditor.class, outputKey = "story")
 *             }
 *         )
 *         String write(@V("story") String story);
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface LoopAgent {

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
    SubAgent[] subAgents();

    /**
     * Maximum number of iterations the loop will execute.
     * If the exit condition is not met within this number of iterations, the loop will terminate.
     *
     * @return maximum number of iterations.
     */
    int maxIterations() default 10;
}
