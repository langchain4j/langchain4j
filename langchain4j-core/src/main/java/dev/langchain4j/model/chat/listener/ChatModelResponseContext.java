package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The response context. It contains {@link ChatModelResponse}, corresponding {@link ChatModelRequest} and attributes.
 * The attributes can be used to pass data between methods of a {@link ChatModelListener}
 * or between multiple {@link ChatModelListener}s.
 */
@Experimental
public class ChatModelResponseContext {

    private final ChatModelResponse response;
    private final ChatModelRequest request;
    private final Map<Object, Object> attributes;

    public ChatModelResponseContext(ChatModelResponse response,
                                    ChatModelRequest request,
                                    Map<Object, Object> attributes) {
        this.response = ensureNotNull(response, "response");
        this.request = ensureNotNull(request, "request");
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @return The response from the {@link ChatLanguageModel}.
     */
    public ChatModelResponse response() {
        return response;
    }

    /**
     * @return The request to the {@link ChatLanguageModel} the response corresponds to.
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
