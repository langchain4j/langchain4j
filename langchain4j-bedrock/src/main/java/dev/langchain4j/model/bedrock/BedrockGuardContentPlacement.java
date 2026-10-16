package dev.langchain4j.model.bedrock;

/**
 * Defines which user messages a Bedrock guardrail evaluates as input.
 * <p>
 * By default, a guardrail configured via {@link BedrockGuardrailConfiguration} evaluates the text of all user and
 * AI messages in the request. When a placement is set, the selected user messages are wrapped in Bedrock Converse
 * {@code guardContent} blocks, and the guardrail evaluates <b>only</b> those blocks as input:
 * other user messages and AI messages are not evaluated. The model response is still evaluated.
 * This reduces cost and latency, but content outside the selected messages is not checked.
 * For example, with {@link #LAST_USER_MESSAGE} and chat memory, a user message that the guardrail blocked
 * is sent to the model without evaluation on later turns.
 * <p>
 * Bedrock never evaluates tool results, tool definitions, tool calls or a system prompt, with or without a placement.
 * <p>
 * Only text and PNG/JPEG images can be wrapped. If a selected message contains other content
 * (for example, a PDF or a GIF image), the placement is ignored for that request,
 * the guardrail evaluates all user and AI messages as by default, and a warning is logged.
 * The same default applies when the request contains no user message.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-converse-api.html">Use a guardrail with the Converse API</a>
 */
public enum BedrockGuardContentPlacement {

    /**
     * The guardrail evaluates only the last user message.
     */
    LAST_USER_MESSAGE,

    /**
     * The guardrail evaluates only the user messages, all of them.
     */
    ALL_USER_MESSAGES
}
