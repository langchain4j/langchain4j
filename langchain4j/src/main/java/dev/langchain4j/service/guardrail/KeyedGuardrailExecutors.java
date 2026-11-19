package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.CompletableFutureUtils.startSafely;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.GuardrailExecutor;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.lang.reflect.Method;

/**
 * Looks up and runs the {@link GuardrailExecutor} for a key (for AI services, the method of the AI service), for one
 * kind of guardrail.
 * <p>
 *     A key without an executor, or a {@code null} key, means there is nothing to run: the result is then the one
 *     given at construction, a success.
 * </p>
 * <p>
 *     The executors are found in one of two ways:
 * </p>
 * <ul>
 *     <li><strong>registered up front</strong>, in a map: the lookup of a key, including its translation by the key
 *     resolver, happens once, and its outcome is cached for the key the caller passes. The key resolver must
 *     therefore always translate the same key into the same key.</li>
 *     <li><strong>with a lookup function</strong>, for keys that hold their executors (tool keys, which are created
 *     when a tool is bound, and tools can be provided anew for every invocation): the function reads the executor
 *     out of the key, and nothing is cached here, so the keys are not retained.</li>
 * </ul>
 *
 * @param <P> The type of {@link GuardrailRequest} to validate
 * @param <R> The type of {@link GuardrailResult} the executors return
 * @param <E> The type of {@link GuardrailExecutor} of the keys
 */
final class KeyedGuardrailExecutors<
        P extends GuardrailRequest<P>, R extends GuardrailResult<R>, E extends GuardrailExecutor<?, P, R, ?, ?>> {

    private final Function<Object, Optional<E>> lookUp;
    private final Supplier<R> noGuardrailsResult;

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
     *                           registered under (for example a {@link Method} into the key a
     *                           framework uses for it). It is only called for a key that is not registered as
     *                           is, never with {@code null}, may return {@code null} for a key that has no
     *                           executor, and is called once per key.
     */
    KeyedGuardrailExecutors(
            Map<Object, E> executors, Supplier<R> noGuardrailsResult, UnaryOperator<Object> keyResolver) {
        this(cachedLookUp(executors, ensureNotNull(keyResolver, "keyResolver")), noGuardrailsResult);
    }

    private KeyedGuardrailExecutors(Function<Object, Optional<E>> lookUp, Supplier<R> noGuardrailsResult) {
        this.lookUp = ensureNotNull(lookUp, "lookUp");
        this.noGuardrailsResult = ensureNotNull(noGuardrailsResult, "noGuardrailsResult");
    }

    /**
     * Creates an instance that finds the executor of a key with a lookup function rather than in executors registered
     * up front, for keys that hold their executors, such as tool keys.
     *
     * @param lookUp             finds the executor of a key. It is never called with {@code null}, and is called on
     *                           every lookup.
     * @param noGuardrailsResult provides the result for a key that has no executor
     */
    static <P extends GuardrailRequest<P>, R extends GuardrailResult<R>, E extends GuardrailExecutor<?, P, R, ?, ?>>
            KeyedGuardrailExecutors<P, R, E> withLookUp(
                    Function<Object, Optional<E>> lookUp, Supplier<R> noGuardrailsResult) {
        return new KeyedGuardrailExecutors<>(lookUp, noGuardrailsResult);
    }

    /**
     * The executor of the key, if any.
     */
    <K> Optional<E> executor(K key) {
        return Optional.ofNullable(key).flatMap(this.lookUp);
    }

    /**
     * Runs the executor of the key.
     */
    <K> R execute(K key, P request) {
        return executor(key).map(executor -> executor.execute(request)).orElseGet(this.noGuardrailsResult);
    }

    /**
     * Non-blocking counterpart of {@link #execute(Object, GuardrailRequest)}.
     */
    <K> CompletableFuture<R> executeAsync(K key, P request) {
        return startSafely(() -> executor(key)
                .map(executor -> executor.executeAsync(request))
                .orElseGet(() -> CompletableFuture.completedFuture(this.noGuardrailsResult.get())));
    }

    /**
     * Whether the key has an executor with at least one guardrail.
     */
    <K> boolean hasGuardrails(K key) {
        return executor(key).map(executor -> !executor.guardrails().isEmpty()).orElse(false);
    }

    private static <E> Function<Object, Optional<E>> cachedLookUp(
            Map<Object, E> executors, UnaryOperator<Object> keyResolver) {
        var registered = Optional.ofNullable(executors).map(Map::copyOf).orElseGet(Map::of);
        var executorsByCallerKey = new ConcurrentHashMap<Object, Optional<E>>();

        return key -> Optional.ofNullable(registered.get(key))
                .or(() -> executorsByCallerKey.computeIfAbsent(
                        key, k -> Optional.ofNullable(keyResolver.apply(k)).map(registered::get)));
    }
}
