package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a definition of the request that will be sent to the supervisor.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface SupervisorStoryCreator {
 *
 *         @SupervisorAgent(outputKey = "story", subAgents = {
 *                 @SubAgent(type = CreativeWriter.class, outputKey = "story"),
 *                 @SubAgent(type = StyleReviewLoopAgent.class, outputKey = "story")
 *         })
 *         String write(@V("topic") String topic, @V("style") String style);
 *
 *         @SupervisorRequest
 *         static String request(@V("topic") String topic, @V("style") String style) {
 *             return "Write a story about " + topic + " in " + style + " style";
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface SupervisorRequest {}
