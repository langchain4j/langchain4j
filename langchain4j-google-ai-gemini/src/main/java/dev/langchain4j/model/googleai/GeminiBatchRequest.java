package dev.langchain4j.model.googleai;

import dev.langchain4j.model.batch.BatchRequest;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents a Google Gemini-specific {@link BatchRequest}, including optional metadata
 * like display name and priority.
 *
 * @param <T> The type of the requests in this batch.
 */
public class GeminiBatchRequest<T> extends BatchRequest<T> {
    private final @Nullable String displayName;
    private final @Nullable Long priority;

    /**
     * Creates a new GeminiBatchRequest.
     *
     * @param requests    The list of requests. Must not be null.
     * @param displayName An optional display name for the batch.
     * @param priority    An optional priority for the batch.
     */
    GeminiBatchRequest(final List<T> requests, @Nullable String displayName, @Nullable Long priority) {
        super(requests);
        this.displayName = displayName;
        this.priority = priority;
    }

    /**
     * @return The optional display name of this batch.
     */
    public @Nullable String displayName() {
        return displayName;
    }

    /**
     * @return The optional priority of this batch.
     */
    public @Nullable Long priority() {
        return priority;
    }

    /**
     * Creates a {@link GeminiBatchRequest} from a list of requests.
     */
    public static <T> GeminiBatchRequest<T> from(List<T> requests) {
        return new GeminiBatchRequest<>(requests, null, null);
    }

    /**
     * Creates a {@link GeminiBatchRequest} from requests and a display name.
     */
    public static <T> GeminiBatchRequest<T> from(List<T> requests, String displayName) {
        return new GeminiBatchRequest<>(requests, displayName, null);
    }

    /**
     * Creates a {@link GeminiBatchRequest} from requests, a display name, and a priority.
     */
    public static <T> GeminiBatchRequest<T> from(List<T> requests, String displayName, Long priority) {
        return new GeminiBatchRequest<>(requests, displayName, priority);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeminiBatchRequest<?> that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(this.displayName, that.displayName) && Objects.equals(this.priority, that.priority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), displayName, priority);
    }

    @Override
    public String toString() {
        return "GeminiBatchRequest{" + "requests="
                + requests() + ", displayName='"
                + displayName + '\'' + ", priority="
                + priority + '}';
    }
}
