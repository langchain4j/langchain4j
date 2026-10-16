package dev.langchain4j.model.bedrock;

/**
 * Defines which user messages a Bedrock guardrail evaluates.
 * <p>
 * By default, a guardrail configured via {@link BedrockGuardrailConfiguration} evaluates the entire request.
 * When a placement is set, the selected user messages are wrapped in Bedrock Converse {@code guardContent} blocks,
 * and the guardrail evaluates <b>only</b> those blocks: the system prompt, other user messages, AI messages
 * and tool results are not evaluated. This reduces cost and latency, but content outside the selected messages
 * is not checked.
 * <p>
 * Only text and PNG/JPEG images can be wrapped. If a selected message contains other content
 * (for example, a PDF or a GIF image), the placement is ignored for that request,
 * the guardrail evaluates the entire request, and a warning is logged.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-converse-api.html">Use a guardrail with the Converse API</a>
 */
public enum BedrockGuardContentPlacement {

    /**
     * Wraps supported content blocks in the last user message.
     */
    LAST_USER_MESSAGE,

    /**
     * Wraps supported content blocks in all user messages.
     */
    ALL_USER_MESSAGES
}
