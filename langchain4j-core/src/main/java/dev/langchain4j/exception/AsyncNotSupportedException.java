package dev.langchain4j.exception;

/**
 * Signals that an asynchronous SPI method (an {@code *Async} method, or a reactive {@code Publisher}-returning
 * method) is not implemented by a component, and is therefore the internal "this component is not genuinely
 * asynchronous" marker carried by those methods' {@code default} implementations. The default methods deliver it
 * through their return type — a failed {@link java.util.concurrent.CompletableFuture} or a failing
 * {@link java.util.concurrent.Flow.Publisher} — rather than throwing it synchronously.
 * <p>
 * It is a dedicated framework exception so that the asynchronous/reactive orchestrators (for example the RAG
 * pipeline and the tool loop) can match this <b>specific</b> type to decide whether to offload the blocking
 * counterpart or fail loudly — without mistaking an <i>incidental</i> {@link UnsupportedOperationException}
 * thrown from deep inside a genuinely asynchronous component (e.g. {@code List.of(...).add(x)}, or an unsupported
 * store filter) for a "not async" signal.
 * <p>
 * It is a {@link NonRetriableException}: a method that does not implement its asynchronous counterpart will not
 * begin to implement it on a retry, so retrying (with back-off) would only waste time before failing.
 *
 * @since 1.19.0
 */
public class AsyncNotSupportedException extends NonRetriableException {

    public AsyncNotSupportedException(String message) {
        super(message);
    }
}
