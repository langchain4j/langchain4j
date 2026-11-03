package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatRequestOptions;
import java.util.UUID;

/**
 * Resolves the {@link InvocationContext#invocationId()} that the listeners of a chat model call get.
 */
@Internal
public final class InvocationChatRequestOptions {

    private InvocationChatRequestOptions() {}

    /**
     * @return the invocation id that the listeners of a chat model call get: the one in the given options (which can
     *         be {@code null}) if any, otherwise the {@linkplain CurrentInvocation current} one, otherwise {@code null}.
     */
    public static UUID invocationIdOf(ChatRequestOptions options) {
        return options != null && options.invocationId() != null
                ? options.invocationId()
                : CurrentInvocation.invocationId();
    }
}
