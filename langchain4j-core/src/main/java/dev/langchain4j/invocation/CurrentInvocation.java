package dev.langchain4j.invocation;

import dev.langchain4j.Internal;
import java.util.UUID;

/**
 * Scopes the invocation currently executing on a thread. Asynchronous work enters the scope using its
 * {@link InvocationContext}, rather than inheriting the context of the thread submitting the work.
 */
@Internal
public final class CurrentInvocation implements AutoCloseable {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private final UUID previous;

    private CurrentInvocation(InvocationContext invocationContext) {
        previous = CURRENT.get();
        CURRENT.set(invocationContext.invocationId());
    }

    public static UUID invocationId() {
        return CURRENT.get();
    }

    public static CurrentInvocation enter(InvocationContext invocationContext) {
        return new CurrentInvocation(invocationContext);
    }

    @Override
    public void close() {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
