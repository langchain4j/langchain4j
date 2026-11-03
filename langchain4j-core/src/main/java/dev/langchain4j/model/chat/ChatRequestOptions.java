package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static dev.langchain4j.internal.Utils.copy;

/**
 * Options that accompany a {@link ChatRequest} through the
 * {@link ChatModel#chat(ChatRequest, ChatRequestOptions)} or
 * {@link StreamingChatModel#chat(ChatRequest, ChatRequestOptions, StreamingChatResponseHandler)}
 * invocation.
 *
 * <p>These options are <em>not</em> sent to the LLM provider; they are purely
 * for the LangChain4j invocation chain (listeners, hooks, etc.).
 * A model that implements {@code chat(ChatRequest)} instead of {@code doChat} is called without them, while its
 * listeners still get them. So an option added here must never change the request sent to the provider: such an
 * option would be silently ignored by these models.
 *
 * @since 1.13.0
 */
public class ChatRequestOptions {

    public static final ChatRequestOptions EMPTY = new ChatRequestOptions(Collections.emptyMap(), null);

    private final Map<Object, Object> listenerAttributes;
    private final UUID invocationId;

    private ChatRequestOptions(Map<Object, Object> listenerAttributes, UUID invocationId) {
        this.listenerAttributes = copy(listenerAttributes);
        this.invocationId = invocationId;
    }

    public Map<Object, Object> listenerAttributes() {
        return listenerAttributes;
    }

    /**
     * Returns the {@link InvocationContext#invocationId()} of the AI Service invocation this call belongs to,
     * which {@link ChatModelListener}s get as {@code invocationId()}, or {@code null} if it was not set.
     *
     * @since 1.24.0
     */
    @Experimental
    public UUID invocationId() {
        return invocationId;
    }

    /**
     * Whether a call with these options may fall back to the model's {@code chat} method without options, when the
     * model does not implement {@code doChat}. {@link #EMPTY} means that the call already comes from that method,
     * so falling back to it again would loop.
     */
    boolean allowFallbackToChatWithoutOptions() {
        return this != EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatRequestOptions that)) return false;
        return Objects.equals(listenerAttributes, that.listenerAttributes)
                && Objects.equals(invocationId, that.invocationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listenerAttributes, invocationId);
    }

    @Override
    public String toString() {
        return "ChatRequestOptions{listenerAttributes=" + listenerAttributes + ", invocationId=" + invocationId + "}";
    }

    public static class Builder {

        private Map<Object, Object> listenerAttributes;
        private UUID invocationId;

        public Builder listenerAttributes(Map<Object, Object> listenerAttributes) {
            this.listenerAttributes = listenerAttributes;
            return this;
        }

        public Builder addListenerAttribute(Object key, Object value) {
            if (this.listenerAttributes == null) {
                this.listenerAttributes = new LinkedHashMap<>();
            }
            this.listenerAttributes.put(key, value);
            return this;
        }

        /**
         * Sets the {@link InvocationContext#invocationId()} of the AI Service invocation this call belongs to.
         * {@link ChatModelListener}s get it as {@code invocationId()}. When it is not set, they get the id of the
         * AI Service invocation whose work (a tool, a guardrail, retrieval, ...) makes the call, if any.
         *
         * @since 1.24.0
         */
        @Experimental
        public Builder invocationId(UUID invocationId) {
            this.invocationId = invocationId;
            return this;
        }

        public ChatRequestOptions build() {
            return new ChatRequestOptions(listenerAttributes, invocationId);
        }
    }
}
