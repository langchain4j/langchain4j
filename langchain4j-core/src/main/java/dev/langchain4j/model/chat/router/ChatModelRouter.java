package dev.langchain4j.model.chat.router;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.AsyncNotSupported;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Decides which route of a {@link RoutingChatModel} or {@link RoutingStreamingChatModel} handles a chat request.
 * <p>
 * A router can be a simple rule:
 * <pre>{@code
 * ChatModelRouter router = request -> ChatModelRoutingResult.route(
 *         request.chatRequest().messages().size() > 20 ? "complex" : "simple");
 * }</pre>
 * or use a model, such as {@link DecisionModelChatModelRouter}.
 *
 * @since 1.21.0
 */
@Experimental
@FunctionalInterface
public interface ChatModelRouter {

    /**
     * Selects the route that handles the request.
     *
     * @param request the chat request to route and the routes that can handle it. Never {@code null}.
     * @return one of the routes ({@link ChatModelRoutingResult#route(String)}), or the default route
     *         ({@link ChatModelRoutingResult#defaultRoute()}). Never {@code null}.
     */
    ChatModelRoutingResult route(ChatModelRoutingRequest request);

    /**
     * Non-blocking counterpart of {@link #route(ChatModelRoutingRequest)}, used by the asynchronous and streaming
     * methods of the routing chat models. Routers that can route without blocking, such as routers that call a model
     * asynchronously, override it.
     * <p>
     * The default returns a future failed with an {@link dev.langchain4j.exception.AsyncNotSupportedException}: the
     * routing chat models then call {@link #route(ChatModelRoutingRequest)} on an executor, so that a router that
     * blocks (for example, one that looks up the user in a database) never blocks the calling thread, which may be an
     * event loop.
     *
     * @param request the chat request to route and the routes that can handle it. Never {@code null}.
     * @return a future of the result, see {@link #route(ChatModelRoutingRequest)}.
     */
    default CompletableFuture<ChatModelRoutingResult> routeAsync(ChatModelRoutingRequest request) {
        return AsyncNotSupported.failedFuture(getClass(), "routeAsync");
    }

    /**
     * Checks that this router can route between the given routes, for example that every route has a description.
     * Called when a {@link RoutingChatModel} or {@link RoutingStreamingChatModel} is created, so misconfigurations
     * fail early. The default does nothing.
     *
     * @param routes all routes, in the order in which they were configured.
     * @throws IllegalArgumentException if the router cannot route between these routes.
     */
    default void validate(List<ChatModelRoute> routes) {}
}
