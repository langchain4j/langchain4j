package dev.langchain4j.model.chat.router;

import dev.langchain4j.Experimental;

/**
 * Decides which route of a {@link RoutingChatModel} or {@link RoutingStreamingChatModel} handles a chat request.
 * <p>
 * A router can be a simple rule:
 * <pre>{@code
 * ChatModelRouter router = request -> request.chatRequest().messages().size() > 20 ? "complex" : "simple";
 * }</pre>
 * or use a model, such as {@link DecisionModelChatModelRouter}.
 *
 * @since 1.21.0
 */
@Experimental
@FunctionalInterface
public interface ChatModelRouter {

    /**
     * Returns the name of the route that handles the request.
     *
     * @param request the chat request to route and the available routes.
     * @return the name of one of the routes, or {@code null} to use the default route.
     */
    String route(ChatModelRoutingRequest request);
}
