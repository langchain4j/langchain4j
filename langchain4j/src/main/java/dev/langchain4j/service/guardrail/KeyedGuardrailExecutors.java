package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.CompletableFutureUtils.failOnThrow;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.GuardrailExecutor;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Looks up and runs the {@link GuardrailExecutor} registered for a method key of an AI service, for one kind of
 * guardrail.
 * <p>
 *     The executors are registered up front, under the method keys of the
 *     {@link dev.langchain4j.spi.classloading.ClassMetadataProviderFactory ClassMetadataProviderFactory} that scanned
 *     the AI service. A caller may identify a method by one of those keys, or by its {@link Method}: a key that is
 *     registered as is is used directly; for a {@link Method} that is not, the key resolver finds the key it is
 *     registered under. The resolver is called once per {@link Method}, and its answer is cached, so it must always
 *     translate the same method into the same key.
 * </p>
 * <p>
 *     A key without an executor, or a {@code null} key, means there is nothing to run: the result is then the one
 *     given at construction, a success.
 * </p>
 *
 * @param <P> The type of {@link GuardrailRequest} to validate
 * @param <R> The type of {@link GuardrailResult} the executors return
 * @param <E> The type of {@link GuardrailExecutor} of the keys
 */
final class KeyedGuardrailExecutors<
        P extends GuardrailRequest<P>, R extends GuardrailResult<R>, E extends GuardrailExecutor<?, P, R, ?, ?>> {

    private final Map<Object, E> registered;
    private final Function<Method, Optional<?>> keyResolver;
    private final Supplier<R> noGuardrailsResult;
    private final Map<Method, Optional<E>> executorsByMethod = new ConcurrentHashMap<>();

    /**
     * @param executors          the executor of each key. {@code null} means none.
     * @param noGuardrailsResult provides the result for a key that has no executor
     * @param keyResolver        finds the key a {@link Method} is registered under, when the method is not registered
     *                           as is. It is never called with {@code null}, and is called once per method.
     */
    KeyedGuardrailExecutors(
            Map<Object, E> executors, Supplier<R> noGuardrailsResult, Function<Method, Optional<?>> keyResolver) {
        this.registered = Optional.ofNullable(executors).map(Map::copyOf).orElseGet(Map::of);
        this.noGuardrailsResult = ensureNotNull(noGuardrailsResult, "noGuardrailsResult");
        this.keyResolver = ensureNotNull(keyResolver, "keyResolver");
    }

    /**
     * The executor of the key, if any.
     */
    Optional<E> executor(Object key) {
        return Optional.ofNullable(key)
                .flatMap(k -> Optional.ofNullable(this.registered.get(k)).or(() -> resolvedExecutor(k)));
    }

    /**
     * Runs the executor of the key.
     */
    R execute(Object key, P request) {
        return executor(key)
                .map(executor -> executor.execute(request))
                .orElseGet(this.noGuardrailsResult);
    }

    /**
     * Non-blocking counterpart of {@link #execute(Object, GuardrailRequest)}: anything thrown by the lookup or while
     * starting the executor fails the returned future.
     */
    CompletableFuture<R> executeAsync(Object key, P request) {
        return failOnThrow(() -> executor(key)
                .map(executor -> executor.executeAsync(request))
                .orElseGet(() -> CompletableFuture.completedFuture(this.noGuardrailsResult.get())));
    }

    /**
     * Whether the key has an executor with at least one guardrail.
     */
    boolean hasGuardrails(Object key) {
        return executor(key)
                .map(executor -> !executor.guardrails().isEmpty())
                .orElse(false);
    }

    /**
     * The number of keys with a registered executor.
     */
    int registeredCount() {
        return this.registered.size();
    }

    private Optional<E> resolvedExecutor(Object key) {
        return (key instanceof Method method)
                ? this.executorsByMethod.computeIfAbsent(method, this::resolveExecutor)
                : Optional.empty();
    }

    private Optional<E> resolveExecutor(Method method) {
        return this.keyResolver.apply(method).map(this.registered::get);
    }
}
