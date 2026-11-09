package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.AgenticServices;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a supplier of the planner for a planner based agent.
 * It can only declare parameters resolved through a {@link SupplierParameterResolver}, resolved each time a new planner is created.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface PlannerBasedStoryCreator {
 *
 *         @PlannerAgent(
 *                 outputKey = "story",
 *                 subAgents = { CreativeWriter.class, AudienceEditor.class, StyleEditor.class })
 *         String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
 *
 *         @PlannerSupplier
 *         static Planner planner() {
 *             return new SequentialPlanner();
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * Like every helper method, it can be an instance method of a helper object of the agent instead of a static method
 * of the agent type: see the "Helper objects" section of the agentic tutorial and
 * {@link AgenticServices.AgentConfigurator}.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface PlannerSupplier {}
