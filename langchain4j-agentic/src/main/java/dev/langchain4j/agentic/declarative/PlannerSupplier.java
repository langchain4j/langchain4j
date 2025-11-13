package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a supplier of the planner for a planner based agent.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface PlannerBasedStoryCreator {
 *
 *         @PlannerAgent(
 *                 outputKey = "story",
 *                 subAgents = {
 *                     @SubAgent(type = CreativeWriter.class, outputKey = "story"),
 *                     @SubAgent(type = AudienceEditor.class, outputKey = "story"),
 *                     @SubAgent(type = StyleEditor.class, outputKey = "story")
 *                 })
 *         String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
 *
 *         @PlannerSupplier
 *         static Planner planner() {
 *             return new SequentialPlanner();
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface PlannerSupplier {}
