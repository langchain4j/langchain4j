package dev.langchain4j.model.chat.router;

import dev.langchain4j.Experimental;
import java.util.concurrent.CompletableFuture;

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
     * @param request the chat request to route and the routes that can handle it.
     * @return the name of one of the routes, or {@code null} to use the default route.
     */
    String route(ChatModelRoutingRequest request);

    /**
     * Non-blocking counterpart of {@link #route(ChatModelRoutingRequest)}, used by the asynchronous and streaming
     * methods of the routing chat models. The default calls {@link #route(ChatModelRoutingRequest)} on the calling
     * thread, which is fine for routers that do not block, such as simple rules. Routers that call a model override
     * it.
     *
     * @param request the chat request to route and the routes that can handle it.
     * @return a future of the name of one of the routes, or of {@code null} to use the default route.
     */
    default CompletableFuture<String> routeAsync(ChatModelRoutingRequest request) {
        try {
            return CompletableFuture.completedFuture(route(request));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
