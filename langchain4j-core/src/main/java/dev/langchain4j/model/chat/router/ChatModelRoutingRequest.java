package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;

/**
 * The input of a {@link ChatModelRouter}: the chat request to route and the available routes.
 *
 * @param chatRequest the chat request to route.
 * @param routes      the available routes, in the order in which they were configured.
 * @since 1.21.0
 */
@Experimental
public record ChatModelRoutingRequest(ChatRequest chatRequest, List<ChatModelRoute> routes) {

    public ChatModelRoutingRequest {
        ensureNotNull(chatRequest, "chatRequest");
        routes = copy(ensureNotEmpty(routes, "routes"));
    }
}
