package dev.langchain4j.model.chat;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Options that accompany a {@link dev.langchain4j.model.chat.request.ChatRequest ChatRequest} through the
 * {@link ChatModel#chat(dev.langchain4j.model.chat.request.ChatRequest, ChatRequestOptions) ChatModel.chat} or
 * {@link StreamingChatModel#chat(dev.langchain4j.model.chat.request.ChatRequest,
 * dev.langchain4j.model.chat.response.StreamingChatResponseHandler, ChatRequestOptions) StreamingChatModel.chat}
 * invocation.
 *
 * <p>These options are <em>not</em> sent to the LLM provider; they are purely
 * for the LangChain4j invocation chain (listeners, hooks, etc.).
 *
 * @since 1.13.0
 */
public class ChatRequestOptions {

    public static final ChatRequestOptions EMPTY = new ChatRequestOptions(Collections.emptyMap());

    private final Map<Object, Object> listenerAttributes;

    private ChatRequestOptions(Map<Object, Object> listenerAttributes) {
        this.listenerAttributes = copy(listenerAttributes);
    }

    public Map<Object, Object> listenerAttributes() {
        return listenerAttributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatRequestOptions that)) return false;
        return Objects.equals(listenerAttributes, that.listenerAttributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listenerAttributes);
    }

    @Override
    public String toString() {
        return "ChatRequestOptions{listenerAttributes=" + listenerAttributes + "}";
    }

    public static class Builder {

        private final Map<Object, Object> listenerAttributes = new LinkedHashMap<>();

        public Builder addListenerAttribute(Object key, Object value) {
            ensureNotNull(key, "key");
            ensureNotNull(value, "value");
            this.listenerAttributes.put(key, value);
            return this;
        }

        public Builder listenerAttributes(Map<Object, Object> attributes) {
            if (attributes != null) {
                this.listenerAttributes.clear();
                this.listenerAttributes.putAll(attributes);
            }
            return this;
        }

        public ChatRequestOptions build() {
            return new ChatRequestOptions(listenerAttributes);
        }
    }
}
