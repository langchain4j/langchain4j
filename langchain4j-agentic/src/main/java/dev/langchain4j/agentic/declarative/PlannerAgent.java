package dev.langchain4j.agentic.declarative;

import dev.langchain4j.agentic.Agent;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a definition of an agent based on a custom planner.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface PlannerBasedStoryCreator {
 *
 *         @PlannerAgent( outputKey = "story",
 *                        subAgents = { CreativeWriter.class, AudienceEditor.class, StyleEditor.class})
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
public @interface PlannerAgent {

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
     * Strongly typed key of the output variable that will be used to store the result of the agent's invocation.
     * It enforces type safety when retrieving the output from the agent's state and can be used in alternative
     * to the {@code outputKey()} attribute. Note that only one of those two attributes can be used at a time.
     *
     * @return class representing the typed output variable.
     */
    Class<? extends TypedKey<?>> typedOutputKey() default Agent.NoTypedKey.class;

    /**
     * Array of sub-agents that will be invoked in sequence.
     *
     * @return array of sub-agents.
     */
    Class<?>[] subAgents();
}
