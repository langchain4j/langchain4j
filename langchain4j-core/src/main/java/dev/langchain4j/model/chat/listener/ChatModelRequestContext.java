package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The request context. It contains the {@link ChatModelRequest} and attributes.
 * The attributes can be used to pass data between methods of a {@link ChatModelListener}
 * or between multiple {@link ChatModelListener}s.
 */
@Experimental
public class ChatModelRequestContext {

    private final ChatModelRequest request;
    private final Map<Object, Object> attributes;

    public ChatModelRequestContext(ChatModelRequest request, Map<Object, Object> attributes) {
        this.request = ensureNotNull(request, "request");
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @return The request to the {@link ChatLanguageModel}.
     */
    public ChatModelRequest request() {
        return request;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of a {@link ChatModelListener}
     * or between multiple {@link ChatModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }
}
