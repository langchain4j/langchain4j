package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.CompletableFutureUtils.startSafely;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.GuardrailExecutor;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Looks up and runs the {@link GuardrailExecutor} of a key, for one kind of guardrail (for example the input guardrails
 * of tools).
 * <p>
 *     The executor of a key is found with a lookup function, for keys that hold their executors (tool keys, which are
 *     created when a tool is bound; tools can be provided anew for every invocation). Nothing is cached here, so the
 *     keys are not retained. The lookup function decides which keys are valid: it is called with every key, including
 *     {@code null}, and may throw for a key it rejects. A key without an executor means there is nothing to run: the
 *     result is then the one given at construction, a success.
 * </p>
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
     * @param lookUp             finds the executor of a key. It is called on every lookup, with every key, including
     *                           {@code null}, and may throw for a key it rejects.
     * @param noGuardrailsResult provides the result for a key that has no executor
     */
    KeyedGuardrailExecutors(Function<Object, Optional<E>> lookUp, Supplier<R> noGuardrailsResult) {
        this.lookUp = ensureNotNull(lookUp, "lookUp");
        this.noGuardrailsResult = ensureNotNull(noGuardrailsResult, "noGuardrailsResult");
    }

    /**
     * The executor of the key, if any.
     */
    Optional<E> executor(Object key) {
        return this.lookUp.apply(key);
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
     * Non-blocking counterpart of {@link #execute(Object, GuardrailRequest)}: an exception thrown by the lookup or while
     * starting the executor fails the returned future.
     */
    CompletableFuture<R> executeAsync(Object key, P request) {
        return startSafely(() -> executor(key)
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
}
