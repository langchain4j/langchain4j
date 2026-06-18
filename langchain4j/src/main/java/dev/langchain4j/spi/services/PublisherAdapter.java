package dev.langchain4j.spi.services;

import dev.langchain4j.Internal;
import dev.langchain4j.service.AiServiceStreamingEvent;
import java.lang.reflect.Type;
import java.util.ServiceLoader;
import java.util.concurrent.Flow;

/**
 * SPI for adapting the canonical reactive stream used internally by non-blocking streaming AI Services —
 * a {@link Flow.Publisher} — to a third-party reactive type (e.g. Reactor {@code Flux}, Mutiny {@code Multi}).
 * <p>
 * It is one-directional: an AI Service method declared to return such a type is satisfied by
 * {@link #fromPublisher(Type, Flow.Publisher)}, which wraps the framework-produced {@link Flow.Publisher}
 * into an instance of the declared type.
 * <p>
 * The element type of the supplied publisher matches the element type of the declared return type: it is
 * either {@link AiServiceStreamingEvent} (the rich event stream) or {@link String} (text-only — the text of
 * each partial response). Implementations must not assume one or the other; they only convert the container.
 * <p>
 * {@link Flow.Publisher} of {@link AiServiceStreamingEvent} and of {@link String} are handled natively and do
 * not require an adapter. Implementations are discovered via the {@link ServiceLoader} mechanism.
 *
 * @since 1.17.0
 */
@Internal
public interface PublisherAdapter {

    /**
     * @param type the AI Service method's declared return type, such as {@code Flux<String>} or
     *             {@code Multi<StreamingEvent>}.
     * @return {@code true} if this adapter handles the given type.
     */
    boolean canAdapt(Type type);

    /**
     * Produces an instance of the adapted type (e.g. a {@code Flux<String>}) from the framework-produced
     * {@link Flow.Publisher}.
     *
     * @param type      the declared type to produce (e.g. {@code Flux<String>}).
     * @param publisher the framework-produced reactive stream, whose element type matches the element type of
     *                  {@code type} (either {@link StreamingEvent} or {@link String}).
     */
    Object fromPublisher(Type type, Flow.Publisher<?> publisher);
}
