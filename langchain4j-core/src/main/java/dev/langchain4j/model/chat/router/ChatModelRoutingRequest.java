package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.Objects;

/**
 * The input of a {@link ChatModelRouter}: the chat request to route, the routes that can handle it and the options of
 * the call.
 *
 * @since 1.21.0
 */
@Experimental
public final class ChatModelRoutingRequest {

    private final ChatRequest chatRequest;
    private final List<ChatModelRoute> routes;
    private final ChatRequestOptions options;

    private ChatModelRoutingRequest(Builder builder) {
        this.chatRequest = ensureNotNull(builder.chatRequest, "chatRequest");
        this.routes = copy(ensureNotEmpty(builder.routes, "routes"));
        this.options = getOrDefault(builder.options, ChatRequestOptions.EMPTY);
    }

    /**
     * The chat request to route.
     */
    public ChatRequest chatRequest() {
        return chatRequest;
    }

    /**
     * The routes that can handle the request, in the order in which they were configured: all routes or, if the
     * request needs a capability (such as a JSON schema response format), the routes whose model supports it.
     */
    public List<ChatModelRoute> routes() {
        return routes;
    }

    /**
     * The options of the call, for example listener attributes identifying the user or the tenant.
     * {@link ChatRequestOptions#EMPTY} if the call has none.
     */
    public ChatRequestOptions options() {
        return options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatModelRoutingRequest that)) return false;
        return Objects.equals(chatRequest, that.chatRequest)
                && Objects.equals(routes, that.routes)
                && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatRequest, routes, options);
    }

    @Override
    public String toString() {
        return "ChatModelRoutingRequest{routes=" + routes + ", options=" + options + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ChatRequest chatRequest;
        private List<ChatModelRoute> routes;
        private ChatRequestOptions options;

        public Builder chatRequest(ChatRequest chatRequest) {
            this.chatRequest = chatRequest;
            return this;
        }

        public Builder routes(List<ChatModelRoute> routes) {
            this.routes = routes;
            return this;
        }

        public Builder options(ChatRequestOptions options) {
            this.options = options;
            return this;
        }

        public ChatModelRoutingRequest build() {
            return new ChatModelRoutingRequest(this);
        }
    }
}
