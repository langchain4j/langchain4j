package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as an exit predicate for a loop in a loop-based agent.
 * The method must be static and return a boolean indicating whether the loop should exit.
 * <p>
 * Example:
 * <pre>
 * {@code
 *      public interface StyleReviewLoopAgentWithCounter {
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
 *
 *         @ExitCondition(testExitAtLoopEnd = true, description = "score greater than 0.8")
 *         static boolean exit(@V("score") double score) {
 *             return score >= 0.8;
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface ExitCondition {

    /**
     * If true, the exit predicate will be tested only at the end of each loop iteration.
     * If false, the exit predicate will be tested after each sub-agent invocation.
     * Default is false.
     *
     * @return whether to test the exit predicate at the end of the loop iteration.
     */
    boolean testExitAtLoopEnd() default false;

    /**
     * Description of the exit condition.
     * It should be clear and descriptive to allow understanding the purpose of the condition.
     *
     * @return description of the exit condition.
     */
    String description() default "<unknown>";
}
