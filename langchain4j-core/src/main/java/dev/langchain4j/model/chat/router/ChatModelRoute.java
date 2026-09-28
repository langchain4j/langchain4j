package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;

/**
 * A route of a {@link RoutingChatModel} or {@link RoutingStreamingChatModel}, as seen by a {@link ChatModelRouter}.
 *
 * @param name        the unique name of the route, for example {@code "simple"} or {@code "complex"}.
 * @param description what kind of requests the route is meant for, or {@code null}. Routers that decide based on
 *                    the content of the request, such as {@link DecisionModelChatModelRouter}, require it.
 * @since 1.21.0
 */
@Experimental
public record ChatModelRoute(String name, String description) {

    public ChatModelRoute {
        ensureNotBlank(name, "name");
    }
}
