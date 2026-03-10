package dev.langchain4j.agentic.declarative;

import dev.langchain4j.agentic.observability.AgentListener;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a supplier of a listener for this agent.
 * The method must be static and return an instance of {@link AgentListener}.
 * <p>
 * Example:
 * <pre>
 * {@code
 *    public interface CreativeWriter {
 *         @Agent(description = "Generate a story based on the given topic", outputKey = "story")
 *         String generateStory(@V("topic") String topic);
 *
 *         @AgentListenerSupplier
 *         static AgentListener listener() {
 *             return new AgentListener() {
 *                 @Override
 *                 public void beforeAgentInvocation(AgentRequest request) {
 *                     requestedTopic = (String) request.inputs().get("topic");
 *                 }
 *             };
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface AgentListenerSupplier {
}
