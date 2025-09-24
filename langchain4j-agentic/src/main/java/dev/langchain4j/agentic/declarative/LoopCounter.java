package dev.langchain4j.agentic.declarative;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a parameter that will receive the current loop iteration count in a loop-based agent.
 * The parameter must be of type int or Integer.
 * <p>
 * Example:
 * <pre>
 * {@code
 *      public interface StyleReviewLoopAgentWithCounter {
 *
 *         @LoopAgent(
 *                 description = "Review the given story to ensure it aligns with the specified style",
 *                 outputName = "story", maxIterations = 5,
 *                 subAgents = {
 *                     @SubAgent(type = StyleScorer.class, outputName = "score"),
 *                     @SubAgent(type = StyleEditor.class, outputName = "story")
 *             }
 *         )
 *         String write(@V("story") String story, @LoopCounter int iteration);
 *
 *         @ExitCondition
 *         static boolean exit(@V("score") double score, @LoopCounter int loopCounter) {
 *             return loopCounter <= 3 ? score >= 0.8 : score >= 0.6;
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({PARAMETER})
public @interface LoopCounter {
}
