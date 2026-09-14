package dev.langchain4j.exception;

/**
 * Marks an exception whose content is meant to be seen by the LLM.
 * <p>
 * By default, when a tool throws an exception, LangChain4j decides what the LLM is told about it.
 * By implementing this interface, you take that decision yourself for a particular exception:
 * {@link #messageForLlm()} is sent to the LLM as the result of the tool execution, and the LLM can
 * react to it, for example by trying something else or by explaining the problem to the user.
 * With the handlers that are aware of this interface, exceptions that do not implement it are treated
 * as failures of the application and are not shown to the LLM.
 * <p>
 * Implement it on your own exception:
 * <pre>{@code
 * public class OrderNotFoundException extends RuntimeException implements ToolErrorVisibleToLlm {
 *
 *     private final String orderId;
 *
 *     public OrderNotFoundException(String orderId) {
 *         super("Order " + orderId + " not found");
 *         this.orderId = orderId;
 *     }
 *
 *     @Override
 *     public String messageForLlm() {
 *         return "There is no order with ID " + orderId + ". Ask the user to check the order number.";
 *     }
 * }
 * }</pre>
 * <p>
 * Or, when you do not want to declare an exception class, throw a ready-made one
 * with {@link #of(String)} or {@link #of(String, Throwable)}:
 * <pre>{@code
 * @Tool("Returns the status of an order")
 * String orderStatus(String orderId) {
 *     try {
 *         return orderService.status(orderId);
 *     } catch (SQLException e) {
 *         // the LLM is told only what it needs to know; the cause is not sent to it
 *         log.warn("Could not read the order database", e);
 *         throw ToolErrorVisibleToLlm.of("The order database is temporarily unavailable.", e);
 *     }
 * }
 * }</pre>
 * <p>
 * The marker is looked for on the exception as it was thrown by the tool, and on the error the handler
 * receives after LangChain4j has unwrapped its own wrappers. It is not searched for further down the cause
 * chain: an exception that wraps a marked one is the last word on what the LLM should be told, so wrapping
 * a marked exception deliberately hides it.
 * <p>
 * Every ready-made {@code ToolExecutionErrorHandler} honors this interface, including the one used when
 * no handler is configured, so implementing it is enough. A handler you write yourself decides for itself
 * whether to look at it.
 *
 * <p>
 * Note that {@link ToolErrorVisibleToLlmException} is a plain {@link RuntimeException} and not a
 * {@link ToolExecutionException}: the latter is the wrapper LangChain4j puts around a failing tool, while
 * this one is thrown by the tool itself, so {@code catch (ToolExecutionException e)} does not catch it.
 *
 * @since 1.21.0
 */
public interface ToolErrorVisibleToLlm {

    /**
     * The text that is sent to the LLM as the result of the failed tool execution.
     * <p>
     * Write it for the LLM, not for a log file: say what went wrong and, when it helps,
     * what the LLM could do about it. Do not pass the message of another exception through
     * (for example {@code return cause.getMessage()}): such messages are written for developers
     * and often contain internal details that should not reach the LLM provider.
     * <p>
     * Must not be blank. A blank text is ignored, and the exception is treated as if it did not implement
     * this interface: a handler that fails the invocation fails it, and a handler that sends the message of
     * the exception to the LLM sends that instead.
     */
    String messageForLlm();

    /**
     * Creates an exception that carries the given message to the LLM.
     *
     * @param message the text to send to the LLM, written for the LLM. Must not be blank.
     */
    static ToolErrorVisibleToLlmException of(String message) {
        return new ToolErrorVisibleToLlmException(message);
    }

    /**
     * Creates an exception that carries the given message to the LLM, keeping {@code cause}
     * so that the technical details are still available in your logs.
     *
     * @param message the text to send to the LLM, written for the LLM. Must not be blank.
     * @param cause   the original error. It is not sent to the LLM, and LangChain4j only logs it at
     *                {@code DEBUG}: once the error is handled, the AI Service invocation continues
     *                normally. Log it yourself, before throwing, if you need it in your own logs.
     */
    static ToolErrorVisibleToLlmException of(String message, Throwable cause) {
        return new ToolErrorVisibleToLlmException(message, cause);
    }
}
