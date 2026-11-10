package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.agentic.AgenticServices;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a definition of the request that will be sent to the supervisor.
 * It can also declare parameters resolved through a {@link SupplierParameterResolver}.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface SupervisorStoryCreator {
 *
 *         @SupervisorAgent(outputKey = "story",
 *                 subAgents = { CreativeWriter.class, StyleReviewLoopAgent.class })
 *         String write(@V("topic") String topic, @V("style") String style);
 *
 *         @SupervisorRequest
 *         static String request(@V("topic") String topic, @V("style") String style) {
 *             return "Write a story about " + topic + " in " + style + " style";
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
public @interface SupervisorRequest {}
