package dev.langchain4j.agentic.declarative;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a definition of a sequence agent, used to orchestrate the agentic workflow
 * by invoking a series of sub-agents in a predefined order.
 * Each sub-agent is defined using the {@link SubAgent} annotation, which specifies the sub-agent's type
 * and its output variable name.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface StoryCreatorWithConfigurableStyleEditor {
 *
 *         @SequenceAgent(outputName = "styledStory", subAgents = {
 *                 @SubAgent(type = CreativeWriter.class, outputName = "story"),
 *                 @SubAgent(type = AudienceEditor.class, outputName = "story"),
 *                 @SubAgent(type = StyleEditor.class)
 *         })
 *         String write(@V("topic") String topic, @V("style") String style, @V("audience") String audience);
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface SequenceAgent {

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
     * Array of sub-agents that will be invoked in sequence.
     *
     * @return array of sub-agents.
     */
    SubAgent[] subAgents();
}
