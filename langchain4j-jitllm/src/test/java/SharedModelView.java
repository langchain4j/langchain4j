import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;

/**
 * A non-{@link AutoCloseable} view of a model that the test class owns.
 *
 * <h2>The problem it solves</h2>
 *
 * <p>The inherited suites drive their cases with {@code @ParameterizedTest @MethodSource("models")},
 * and JUnit closes {@code AutoCloseable} arguments after <b>each</b> invocation unless the test opts
 * out. These ITs deliberately share <b>one static model</b> — loading a multi-gigabyte model per case
 * is not an option — so the first parameterized case closed the shared model and every later case
 * failed with "model is closed".
 *
 * <p>This is a <b>test-fixture lifecycle problem, not a production one</b>. {@code close()} on the
 * real model means what it says: it releases the engine model. The fixture's mistake was handing the
 * same owned resource to a framework that assumes each argument is the caller's to close.
 *
 * <h2>What this is, and is not</h2>
 *
 * <p>A delegating view that is <b>not</b> {@code AutoCloseable}, so JUnit leaves it alone. It lives
 * in test sources only: it is not published, and there is no production "borrowed model" mode. The
 * real model is closed once, by the class that created it, in {@code @AfterAll}.
 */
final class SharedModelView {

    private SharedModelView() {}

    /** A view of a chat model the test class owns and will close itself. */
    static ChatModel of(ChatModel delegate) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                return delegate.chat(chatRequest);
            }

            @Override
            public List<ChatModelListener> listeners() {
                return delegate.listeners();
            }

            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return delegate.defaultRequestParameters();
            }

            @Override
            public String toString() {
                return "shared view of " + delegate;
            }
        };
    }

    /** A view of a streaming chat model the test class owns and will close itself. */
    static StreamingChatModel of(StreamingChatModel delegate) {
        return of(delegate, null);
    }

    /**
     * As above, counting requests in flight so suite cleanup can wait for them.
     *
     * @param inFlight incremented before the call and decremented when it returns, or {@code null}
     */
    static StreamingChatModel of(StreamingChatModel delegate, java.util.concurrent.atomic.AtomicInteger inFlight) {
        return new StreamingChatModel() {
            @Override
            public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                if (inFlight != null) {
                    inFlight.incrementAndGet();
                }
                try {
                    delegate.chat(chatRequest, handler);
                } finally {
                    if (inFlight != null) {
                        inFlight.decrementAndGet();
                    }
                }
            }

            @Override
            public List<ChatModelListener> listeners() {
                return delegate.listeners();
            }

            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return delegate.defaultRequestParameters();
            }

            @Override
            public String toString() {
                return "shared view of " + delegate;
            }
        };
    }
}
