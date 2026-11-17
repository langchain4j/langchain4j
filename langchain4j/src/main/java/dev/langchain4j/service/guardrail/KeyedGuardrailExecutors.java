package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.GuardrailExecutor;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Looks up and runs the {@link GuardrailExecutor} registered for a key (for AI services, the method of the AI
 * service), for one kind of guardrail.
 * <p>
 *     A key without an executor, or a {@code null} key, means there is nothing to run: the result is then the one
 *     given at construction, a success.
 * </p>
 * <p>
 *     The lookup of a key, including its translation by the key resolver, happens once: its outcome is cached for the
 *     key the caller passes. The key resolver must therefore always translate the same key into the same key.
 * </p>
 *
 * @param <P> The type of {@link GuardrailRequest} to validate
 * @param <R> The type of {@link GuardrailResult} the executors return
 * @param <E> The type of {@link GuardrailExecutor} registered for the keys
 */
final class KeyedGuardrailExecutors<
        P extends GuardrailRequest<P>, R extends GuardrailResult<R>, E extends GuardrailExecutor<?, P, R, ?, ?>> {

    private final Map<Object, E> executors;
    private final Map<Object, Optional<E>> executorsByCallerKey = new ConcurrentHashMap<>();
    private final Supplier<R> noGuardrailsResult;
    private final UnaryOperator<Object> keyResolver;

    /**
     * @param executors          the executor of each key. {@code null} means none.
     * @param noGuardrailsResult provides the result for a key that has no executor
     */
    KeyedGuardrailExecutors(Map<Object, E> executors, Supplier<R> noGuardrailsResult) {
        this(executors, noGuardrailsResult, UnaryOperator.identity());
    }

    /**
     * @param executors          the executor of each key. {@code null} means none.
     * @param noGuardrailsResult provides the result for a key that has no executor
     * @param keyResolver        translates the key a caller identifies the target with into the key the executors are
     *                           registered under (for example a {@link java.lang.reflect.Method} into the key a
     *                           framework uses for it). It is never called with {@code null}, may return
     *                           {@code null} for a key that has no executor, and is called once per key.
     */
    KeyedGuardrailExecutors(
            Map<Object, E> executors, Supplier<R> noGuardrailsResult, UnaryOperator<Object> keyResolver) {
        this.noGuardrailsResult = ensureNotNull(noGuardrailsResult, "noGuardrailsResult");
        this.keyResolver = ensureNotNull(keyResolver, "keyResolver");
        this.executors = Optional.ofNullable(executors).map(Map::copyOf).orElseGet(Map::of);
    }

    /**
     * The executor registered for the key, if any. The key is first translated by the key resolver.
     */
    <K> Optional<E> executor(K key) {
        return Optional.ofNullable(key).flatMap(k -> this.executorsByCallerKey.computeIfAbsent(k, this::lookUp));
    }

    /**
     * Runs the executor registered for the key.
     */
    <K> R execute(K key, P request) {
        return executor(key).map(executor -> executor.execute(request)).orElseGet(this.noGuardrailsResult);
    }

    /**
     * Non-blocking counterpart of {@link #execute(Object, GuardrailRequest)}.
     */
    <K> CompletableFuture<R> executeAsync(K key, P request) {
        return executor(key)
                .map(executor -> executor.executeAsync(request))
                .orElseGet(() -> CompletableFuture.completedFuture(this.noGuardrailsResult.get()));
    }

    /**
     * Whether the key has an executor with at least one guardrail.
     */
    <K> boolean hasGuardrails(K key) {
        return executor(key).map(executor -> !executor.guardrails().isEmpty()).orElse(false);
    }

    /**
     * The number of keys that have an executor.
     */
    int size() {
        return this.executors.size();
    }

    private Optional<E> lookUp(Object key) {
        return Optional.ofNullable(this.keyResolver.apply(key)).map(this.executors::get);
    }
}
