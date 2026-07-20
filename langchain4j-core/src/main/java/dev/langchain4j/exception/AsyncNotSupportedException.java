package dev.langchain4j.exception;

/**
 * Signals that an asynchronous SPI method (an {@code *Async} method, or a reactive {@code Publisher}-returning
 * method) is not implemented by a component, and is therefore the internal "this component is not genuinely
 * asynchronous" marker thrown by those methods' {@code default} implementations.
 * <p>
 * It is a dedicated framework exception (a {@link LangChain4jException}) so that the asynchronous/reactive
 * orchestrators (for example the RAG pipeline and the tool loop) can match this <b>specific</b> type to decide
 * whether to offload the blocking counterpart or fail loudly — without mistaking an <i>incidental</i>
 * {@link UnsupportedOperationException} thrown from deep inside a genuinely asynchronous component (e.g.
 * {@code List.of(...).add(x)}, or an unsupported store filter) for a "not async" signal.
 *
 * @since 1.19.0
 */
public class AsyncNotSupportedException extends LangChain4jException {

    public AsyncNotSupportedException(String message) {
        super(message);
    }
}
