package dev.langchain4j.agentic.declarative;

import dev.langchain4j.agentic.agent.AgentResponse;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method to be executed after an agent invocation.
 * The annotated method must be static, take an {@link AgentResponse} as arguments, and return void.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface CreativeWriter {
 *
 *         @Agent("Generate a story based on the given topic")
 *         String generateStory(@V("topic") String topic);
 *
 *         @AfterAgentInvocation
 *         static void afterAgentInvocation(AgentResponse response) {
 *             System.out.println("Current score is: " + response.agenticScope().readState("score", 0.0));
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface AfterAgentInvocation {
}
