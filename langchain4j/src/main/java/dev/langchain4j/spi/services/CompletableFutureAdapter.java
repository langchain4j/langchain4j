package dev.langchain4j.spi.services;

import dev.langchain4j.Internal;

import java.lang.reflect.Type;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * SPI for adapting a single-value asynchronous type (e.g. Mutiny {@code Uni}, Reactor {@code Mono}) to and from
 * {@link CompletableFuture}, which is the canonical asynchronous type used internally by AI Services.
 * <p>
 * It is used in two directions:
 * <ul>
 *   <li>A {@code @Tool} method that returns such a type — {@link #toCompletableFuture(Object)} normalizes the
 *       returned value so the asynchronous tool loop can compose it without blocking.</li>
 *   <li>An AI Service method declared to return such a type — {@link #fromCompletableFuture(Type, CompletableFuture)}
 *       produces an instance of the declared type from the computed result.</li>
 * </ul>
 * <p>
 * {@link CompletableFuture} and {@link CompletionStage} are handled natively and do not
 * require an adapter. Implementations are discovered via the {@link ServiceLoader} mechanism.
 *
 * @since 1.17.0
 */
@Internal
public interface CompletableFutureAdapter {

    /**
     * @param type the declared type (e.g. a {@code @Tool} method's return type, or an AI Service method's
     *             return type), such as {@code Mono<String>}.
     * @return {@code true} if this adapter handles the given type.
     */
    boolean canAdapt(Type type);

    /**
     * Converts a value of the adapted type (e.g. a {@code Mono<T>}) into a {@link CompletableFuture}.
     * Invoked for {@code @Tool} methods returning the adapted type.
     */
    CompletableFuture<?> toCompletableFuture(Object asyncValue);

    /**
     * Produces an instance of the adapted type (e.g. a {@code Mono<T>}) from a {@link CompletableFuture}.
     * Invoked for AI Service methods declared to return the adapted type.
     *
     * @param type   the declared type to produce (e.g. {@code Mono<String>}).
     * @param future the computed result.
     */
    Object fromCompletableFuture(Type type, CompletableFuture<?> future);
}
