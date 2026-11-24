package dev.langchain4j.agentic.a2a;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a parameter that will be set as the {@code tenant} on the outgoing A2A {@code MessageSendParams},
 * rather than being included as a {@code TextPart} in the message content.
 * <p>
 * The tenant identifier scopes the message to a specific tenant in a multi-tenant A2A deployment.
 * When {@code null}, no tenant will be sent and the server will apply its default tenant resolution.
 * <p>
 * Example:
 * <pre>
 * {@code
 * public interface MyA2AAgent {
 *
 *     @A2AClientAgent(a2aServerUrl = "http://localhost:8080", outputKey = "response")
 *     String chat(@V("question") String question,
 *                 @A2AContextId String contextId,
 *                 @A2ATenantId String tenant);
 * }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({PARAMETER})
public @interface A2ATenantId {}
