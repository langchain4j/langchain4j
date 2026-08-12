package dev.langchain4j.agentic.a2a;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a parameter that will be set as the {@code contextId} on the outgoing A2A {@code Message} envelope,
 * rather than being included as a {@code TextPart} in the message content.
 * <p>
 * The conversation context identifier groups related tasks in an A2A multi-turn conversation.
 * When {@code null}, the A2A server will generate a new context.
 * <p>
 * Example:
 * <pre>
 * {@code
 * public interface MyA2AAgent {
 *
 *     @A2AClientAgent(a2aServerUrl = "http://localhost:8080", outputKey = "response")
 *     String chat(@V("question") String question,
 *                 @A2AContextId String contextId,
 *                 @A2ATaskId String taskId);
 * }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({PARAMETER})
public @interface A2AContextId {}
