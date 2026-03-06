package dev.langchain4j.model.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Options for a {@link ChatModel#chat(dev.langchain4j.model.chat.request.ChatRequest, ChatRequestOptions)}
 * or {@link StreamingChatModel#chat(dev.langchain4j.model.chat.request.ChatRequest,
 * dev.langchain4j.model.chat.response.StreamingChatResponseHandler, ChatRequestOptions)} invocation.
 * <p>
 * These options are not sent to the LLM provider. They are only used within the LangChain4j
 * invocation chain and {@link dev.langchain4j.model.chat.listener.ChatModelListener}s.
 *
 * @since 1.13.0
 */
public class ChatRequestOptions {

    /**
     * An empty {@link ChatRequestOptions} instance with no listener attributes.
     */
    public static final ChatRequestOptions EMPTY = new ChatRequestOptions(Collections.emptyMap());

    private final Map<Object, Object> listenerAttributes;

    private ChatRequestOptions(Map<Object, Object> listenerAttributes) {
        this.listenerAttributes = listenerAttributes;
    }

    /**
     * Returns the listener attributes.
     *
     * @return an unmodifiable view of the listener attributes
     */
    public Map<Object, Object> listenerAttributes() {
        return listenerAttributes;
    }

    /**
     * Creates a new {@link Builder}.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Map<Object, Object> listenerAttributes = new HashMap<>();

        private Builder() {}

        /**
         * Sets the listener attributes, replacing any previously set attributes.
         *
         * @param listenerAttributes a {@link Map} of attributes to pass to
         *                           {@link dev.langchain4j.model.chat.listener.ChatModelListener}s
         * @return this {@link Builder}
         */
        public Builder listenerAttributes(Map<Object, Object> listenerAttributes) {
            this.listenerAttributes.clear();
            if (listenerAttributes != null) {
                this.listenerAttributes.putAll(listenerAttributes);
            }
            return this;
        }

        /**
         * Adds a single listener attribute.
         *
         * @param key   the attribute key, must not be null
         * @param value the attribute value, must not be null
         * @return this {@link Builder}
         */
        public Builder listenerAttribute(Object key, Object value) {
            ensureNotNull(key, "key");
            ensureNotNull(value, "value");
            this.listenerAttributes.put(key, value);
            return this;
        }

        /**
         * Builds a new {@link ChatRequestOptions}.
         *
         * @return a new {@link ChatRequestOptions}
         */
        public ChatRequestOptions build() {
            return new ChatRequestOptions(Collections.unmodifiableMap(new HashMap<>(listenerAttributes)));
        }
    }
}
