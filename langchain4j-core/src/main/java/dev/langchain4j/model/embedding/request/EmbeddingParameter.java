package dev.langchain4j.model.embedding.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * A typed, stable identifier for a single {@link EmbeddingRequestParameters} value.
 * <p>
 * Parameters are identified by tokens (rather than plain strings) so that:
 * <ul>
 *     <li>the value type is known at compile time (see {@link #type()});</li>
 *     <li>provider modules can declare their own parameters (e.g. an OpenAI {@code USER} token) without
 *         touching {@code langchain4j-core};</li>
 *     <li>an {@link dev.langchain4j.model.embedding.EmbeddingModel} can declare exactly which parameters it
 *         honors via {@link dev.langchain4j.model.embedding.EmbeddingModel#supportedParameters()}. Any parameter
 *         present in a request but absent from that set is rejected, so a newly introduced parameter is
 *         <b>opt-in</b>: an implementation that has not explicitly declared support for it will fail fast
 *         instead of silently ignoring it.</li>
 * </ul>
 * Two tokens are considered equal when their {@link #name()} is equal; the {@code name} is therefore the
 * globally unique identity of a parameter and must not collide across providers.
 *
 * @param <T> the type of the value this parameter holds.
 * @since 1.18.0
 */
@Experimental
public final class EmbeddingParameter<T> {

    private final String name;
    private final Class<T> type;

    /**
     * @param name a globally unique, human-readable name for this parameter (e.g. {@code "dimensions"}).
     * @param type the type of the value this parameter holds.
     */
    public EmbeddingParameter(String name, Class<T> type) {
        this.name = ensureNotBlank(name, "name");
        this.type = ensureNotNull(type, "type");
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    /**
     * Casts the given value to this parameter's {@link #type()}.
     *
     * @param value the value to cast, may be {@code null}.
     * @return the value, typed.
     */
    public T cast(Object value) {
        return type.cast(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingParameter<?> that = (EmbeddingParameter<?>) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
