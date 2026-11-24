package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * The result of a {@link ChatModelRouter}: the route that handles the request, or the default route of the routing
 * chat model.
 * <pre>{@code
 * ChatModelRouter router = request -> request.chatRequest().messages().size() > 20
 *         ? ChatModelRoutingResult.route("complex")
 *         : ChatModelRoutingResult.defaultRoute();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public final class ChatModelRoutingResult {

    private static final ChatModelRoutingResult DEFAULT_ROUTE = new ChatModelRoutingResult(null);

    private final String routeName;

    private ChatModelRoutingResult(String routeName) {
        this.routeName = routeName;
    }

    /**
     * Routes the request to the route with the given name.
     */
    public static ChatModelRoutingResult route(String routeName) {
        return new ChatModelRoutingResult(ensureNotBlank(routeName, "routeName"));
    }

    /**
     * Routes the request to the default route of the routing chat model, for example when a router that calls a model
     * is unsure.
     */
    public static ChatModelRoutingResult defaultRoute() {
        return DEFAULT_ROUTE;
    }

    /**
     * The name of the selected route, or {@code null} if the default route is used.
     */
    public String routeName() {
        return routeName;
    }

    /**
     * Whether the default route of the routing chat model is selected, in which case {@link #routeName()} is
     * {@code null}.
     */
    public boolean isDefaultRoute() {
        return routeName == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatModelRoutingResult that)) return false;
        return Objects.equals(routeName, that.routeName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(routeName);
    }

    @Override
    public String toString() {
        return isDefaultRoute() ? "ChatModelRoutingResult{defaultRoute}" : "ChatModelRoutingResult{route=" + routeName + '}';
    }
}
