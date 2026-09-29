package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * A route of a {@link RoutingChatModel} or {@link RoutingStreamingChatModel}, as seen by a {@link ChatModelRouter}.
 *
 * @since 1.21.0
 */
@Experimental
public final class ChatModelRoute {

    private final String name;
    private final String description;

    /**
     * @param name        the unique name of the route, for example {@code "simple"} or {@code "complex"}.
     * @param description what kind of requests the route is meant for, or {@code null}. Routers that decide based on
     *                    the content of the request, such as {@link DecisionModelChatModelRouter}, require it.
     */
    public ChatModelRoute(String name, String description) {
        this.name = ensureNotBlank(name, "name");
        this.description = description;
    }

    /**
     * The unique name of the route.
     */
    public String name() {
        return name;
    }

    /**
     * What kind of requests the route is meant for, or {@code null}.
     */
    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatModelRoute that)) return false;
        return Objects.equals(name, that.name) && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description);
    }

    @Override
    public String toString() {
        return "ChatModelRoute{name=" + name + ", description=" + description + '}';
    }
}
