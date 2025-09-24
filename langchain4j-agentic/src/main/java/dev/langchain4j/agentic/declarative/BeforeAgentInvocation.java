package dev.langchain4j.agentic.declarative;

import dev.langchain4j.agentic.agent.AgentRequest;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a hook to be executed before an agent invocation.
 * The annotated method must be static, take an {@link AgentRequest} as arguments, and return void.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface CreativeWriter {
 *
 *         @Agent("Generate a story based on the given topic")
 *         String generateStory(@V("topic") String topic);
 *
 *         @BeforeAgentInvocation
 *         static void beforeAgentInvocation(AgentRequest request) {
 *             System.out.println("Invoked with topic: " + request.inputs().get("topic"));
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface BeforeAgentInvocation {
}
