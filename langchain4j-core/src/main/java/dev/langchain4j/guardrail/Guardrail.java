package dev.langchain4j.guardrail;

import dev.langchain4j.exception.AsyncNotSupportedException;
import java.util.concurrent.CompletableFuture;

/**
 * A guardrail is a rule that is applied when interacting with an LLM either to the input (the user message) or to the
 * output of the model to ensure that they are safe and meet the expectations of the model.
 *
 * @param <P>
 *            The type of the {@link GuardrailRequest}
 * @param <R>
 *            The type of the {@link GuardrailResult}
 */
public interface Guardrail<P extends GuardrailRequest, R extends GuardrailResult<R>> {

    /**
     * Returns the logical name of this guardrail.
     *
     * Wrappers/decorators can override this method to expose the wrapped guardrail's name.
     *
     * @return the logical guardrail name
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * Validate the interaction between the model and the user in one of the two directions.
     *
     * @param request
     *            The parameters of the request or the response to be validated
     *
     * @return The result of the validation
     */
    R validate(P request);

    /**
     * Non-blocking counterpart of {@link #validate(GuardrailRequest)}, invoked by the asynchronous
     * ({@code CompletableFuture}/{@code CompletionStage}) and reactive ({@code Flow.Publisher}) AI Service modes.
     * <p>
     * Returns a {@link CompletableFuture} (rather than a plain {@link java.util.concurrent.CompletionStage}) because
     * guardrails sit in the cancellable execution path: the AI Service may cancel an in-flight validation when the
     * invocation is cancelled (best-effort, mirroring {@code ToolExecutor}). A guardrail backed by a
     * cancellation-aware async client should honor that cancellation.
     * <p>
     * The default implementation throws {@link UnsupportedOperationException}: a guardrail must opt in to the
     * non-blocking paths rather than have its (potentially blocking) {@link #validate(GuardrailRequest)} silently
     * run on the model-delivery thread. A guardrail that performs blocking I/O (e.g. calling a remote moderation or
     * PII service) must override this method to return a future completed off the calling thread (for instance via
     * an async client, or {@code CompletableFuture.supplyAsync(..., executor)} onto its own executor). A guardrail
     * that does not perform blocking I/O may simply return
     * {@code CompletableFuture.completedFuture(validate(request))}.
     * <p>
     * This mirrors {@code ChatMemoryStore} and {@code ToolExecutor}, whose asynchronous counterparts likewise throw
     * by default.
     *
     * @param request
     *            The parameters of the request or the response to be validated
     * @return A {@link CompletableFuture} that completes with the result of the validation
     * @since 1.18.0
     */
    default CompletableFuture<R> validateAsync(P request) {
        throw new AsyncNotSupportedException(getClass().getName()
                + " does not implement validateAsync(). To use this guardrail with an asynchronous"
                + " (CompletableFuture/CompletionStage) or reactive (Flow.Publisher) AI Service, override"
                + " validateAsync(). If the guardrail does not perform blocking I/O, return"
                + " CompletableFuture.completedFuture(validate(request)).");
    }
}
