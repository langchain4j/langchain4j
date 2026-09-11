package dev.langchain4j.exception;

/**
 * Marks an exception whose content is meant to be seen by the LLM.
 * <p>
 * By default, when a tool throws an exception, LangChain4j decides what the LLM is told about it.
 * By implementing this interface, you take that decision yourself for a particular exception:
 * {@link #messageForLlm()} is sent to the LLM as the result of the tool execution, and the LLM can
 * react to it, for example by trying something else or by explaining the problem to the user.
 * Exceptions that do not implement this interface are treated as failures of the application
 * and are not shown to the LLM.
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
 *         // the LLM is told what it needs to know, the cause is kept for your logs
 *         throw ToolErrorVisibleToLlm.of("The order database is temporarily unavailable.", e);
 *     }
 * }
 * }</pre>
 * <p>
 * For this to have an effect, the AI Service must use a tool execution error handler that is aware of
 * this interface: {@code ToolExecutionErrorHandler.failUnlessVisibleToLlm()} or
 * {@code ToolExecutionErrorHandler.sendExceptionMessageToLlmFor(Class...)}.
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
     * Must not be blank. If it is, the AI Service invocation fails instead,
     * as if the exception did not implement this interface.
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
     * @param cause   the original error. It is not sent to the LLM.
     */
    static ToolErrorVisibleToLlmException of(String message, Throwable cause) {
        return new ToolErrorVisibleToLlmException(message, cause);
    }
}
