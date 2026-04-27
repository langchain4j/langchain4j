package dev.langchain4j.model.chat.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.Map;

/**
 * The chat model request context.
 * It contains the {@link ChatRequest}, {@link ModelProvider} and attributes.
 * The attributes can be used to pass data between methods of a {@link ChatModelListener}
 * or between multiple {@link ChatModelListener}s.
 */
public class ChatModelRequestContext {

    private static final ThreadLocal<ChatModelRequestContext> CURRENT = new ThreadLocal<>();

    private final ChatRequest chatRequest;
    private final ModelProvider modelProvider;
    private final Map<Object, Object> attributes;

    public ChatModelRequestContext(
            ChatRequest chatRequest, ModelProvider modelProvider, Map<Object, Object> attributes) {
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.modelProvider = modelProvider;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    public ChatRequest chatRequest() {
        return chatRequest;
    }

    public ModelProvider modelProvider() {
        return modelProvider;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of a {@link ChatModelListener}
     * or between multiple {@link ChatModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }

    /**
     * Returns the current thread-local {@link ChatModelRequestContext}, if any.
     * This enables thread-safe access to the request context from within tools and prompts.
     *
     * @return the current context, or {@code null} if not set for the current thread
     */
    public static ChatModelRequestContext current() {
        return CURRENT.get();
    }

    /**
     * Sets the current thread-local {@link ChatModelRequestContext}.
     * This is intended for internal use by the framework.
     *
     * @param context the context to set as current
     */
    public static void setCurrent(ChatModelRequestContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }
}
