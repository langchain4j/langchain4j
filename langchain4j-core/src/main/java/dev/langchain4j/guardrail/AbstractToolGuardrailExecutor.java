package dev.langchain4j.guardrail;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import dev.langchain4j.Internal;
import dev.langchain4j.guardrail.config.GuardrailsConfig;
import dev.langchain4j.observability.api.event.GuardrailExecutedEvent;

/**
 * Base class of the {@link GuardrailExecutor}s of tool guardrails, which share the same semantics:
 * <ul>
 *     <li>the guardrails run once, with no retry or reprompt;</li>
 *     <li>a non-fatal failure does not stop the chain and is <strong>returned</strong>, not thrown, so that the
 *     caller can turn it into an error tool result;</li>
 *     <li>a fatal failure stops the chain and is thrown (async: fails the future) as the
 *     {@link GuardrailException} created by {@link #createGuardrailException(String, Throwable)}.</li>
 * </ul>
 *
 * @param <C>
 *            The type of {@link GuardrailsConfig} to use for configuration
 * @param <P>
 *            The type of {@link GuardrailRequest} to validate
 * @param <R>
 *            The type of {@link GuardrailResult} to return
 * @param <G>
 *            The type of {@link Guardrail}s being executed
 * @param <E> The type of {@link GuardrailExecutedEvent} to be fired
 * @param <F>
 *            The type of {@link GuardrailResult.Failure} to return
 */
@Internal
public abstract sealed class AbstractToolGuardrailExecutor<
                C extends GuardrailsConfig,
                P extends GuardrailRequest<P>,
                R extends GuardrailResult<R>,
                G extends Guardrail<P, R>,
                E extends GuardrailExecutedEvent<P, R, G>,
                F extends GuardrailResult.Failure>
        extends AbstractGuardrailExecutor<C, P, R, G, E, F>
        permits ToolInputGuardrailExecutor, ToolOutputGuardrailExecutor {

    protected AbstractToolGuardrailExecutor(C config, List<G> guardrails) {
        super(config, guardrails);
    }

    /**
     * Executes the guardrails on the given request.
     *
     * @param request The request to validate
     * @return The result of the validation. A non-fatal failure is returned, not thrown.
     * @throws GuardrailException If a guardrail returned a fatal failure, or threw
     */
    @Override
    public R execute(P request) {
        var result = executeGuardrails(request);
        throwIfFatal(result);
        return result;
    }

    /**
     * Non-blocking counterpart of {@link #execute(GuardrailRequest)}. A fatal failure completes the future
     * exceptionally with a {@link GuardrailException}.
     */
    @Override
    public CompletableFuture<R> executeAsync(P request) {
        return executeGuardrailsAsyncAndFinish(request, result -> {
            throwIfFatal(result);
            return result;
        });
    }

    protected void throwIfFatal(R result) {
        if (result.isFatal()) {
            throw createGuardrailException(result.toString(), result.getFirstFailureException());
        }
    }
}
