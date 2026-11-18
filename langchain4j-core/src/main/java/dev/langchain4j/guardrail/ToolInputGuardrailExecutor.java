package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolInputGuardrailResult.Failure;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.internal.CancellationChain;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent.ToolInputGuardrailExecutedEventBuilder;
import dev.langchain4j.spi.guardrail.ToolInputGuardrailExecutorBuilderFactory;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The {@link GuardrailExecutor} for {@link ToolInputGuardrail}s.
 * <p>
 *     It runs the guardrails once, with no retry or reprompt. A non-fatal failure does not stop the chain, and
 *     {@code execute(...)} returns it instead of throwing, so the caller can turn it into an
 *     error tool result. A fatal failure stops the chain and throws a {@link ToolInputGuardrailException}.
 * </p>
 *
 * @since 1.25.0
 */
@Experimental
public non-sealed class ToolInputGuardrailExecutor
        extends AbstractToolGuardrailExecutor<
                ToolInputGuardrailsConfig,
                ToolInputGuardrailRequest,
                ToolInputGuardrailResult,
                ToolInputGuardrail,
                ToolInputGuardrailExecutedEvent,
                Failure> {

    protected ToolInputGuardrailExecutor(ToolInputGuardrailsConfig config, List<ToolInputGuardrail> guardrails) {
        super(config, guardrails);
    }

    @Override
    protected ToolInputGuardrailResult createFailure(List<Failure> failures) {
        return new ToolInputGuardrailResult(failures, false);
    }

    /**
     * Keeps the rewritten arguments on a failed result, so a rejected call is reported with the arguments as the
     * guardrails that ran before the failing one left them.
     */
    @Override
    protected ToolInputGuardrailResult composeResult(
            ToolInputGuardrailResult oldResult, ToolInputGuardrailResult newResult) {
        var composed = super.composeResult(oldResult, newResult);
        var latestArguments = newResult.rewrittenArguments().or(oldResult::rewrittenArguments);

        return composed.isSuccess()
                ? composed
                : latestArguments.map(composed::withRewrittenArguments).orElse(composed);
    }

    /**
     * Records on the exception of a guardrail that threw the arguments it validated.
     */
    @Override
    protected ToolInputGuardrailResult validate(ToolInputGuardrailRequest request, ToolInputGuardrail guardrail) {
        try {
            return super.validate(request, guardrail);
        } catch (ToolInputGuardrailException e) {
            throw e.arguments(request.arguments());
        }
    }

    /**
     * Non-blocking counterpart of {@link #validate(ToolInputGuardrailRequest, ToolInputGuardrail)}.
     */
    @Override
    protected CompletableFuture<ToolInputGuardrailResult> validateAsync(
            ToolInputGuardrailRequest request, ToolInputGuardrail guardrail, CancellationChain chain) {
        return super.validateAsync(request, guardrail, chain).handle((result, error) -> {
            if (error == null) {
                return result;
            }
            var cause = unwrapCompletionException(error);
            if (cause instanceof ToolInputGuardrailException e) {
                e.arguments(request.arguments());
            }
            throw (error instanceof CompletionException completion) ? completion : new CompletionException(cause);
        });
    }

    /**
     * Records on the exception of a fatal result the arguments the failing guardrail validated: the ones rewritten by
     * the guardrails before it, otherwise the ones of the request.
     */
    @Override
    protected void throwIfFatal(ToolInputGuardrailRequest request, ToolInputGuardrailResult result) {
        if (result.isFatal()) {
            throw createGuardrailException(result.toString(), result.getFirstFailureException())
                    .arguments(result.rewrittenArguments().orElseGet(request::arguments));
        }
    }

    /**
     * Keeps the arguments rewritten by the guardrails before a fatal one, for {@link #throwIfFatal}.
     */
    @Override
    protected ToolInputGuardrailResult handleFatalResult(
            ToolInputGuardrailResult accumulatedResult, ToolInputGuardrailResult result) {
        return accumulatedResult
                .rewrittenArguments()
                .map(result::withRewrittenArguments)
                .orElse(result);
    }

    @Override
    protected ToolInputGuardrailResult createSuccess() {
        return ToolInputGuardrailResult.success();
    }

    @Override
    protected ToolInputGuardrailException createGuardrailException(String message, Throwable cause) {
        return new ToolInputGuardrailException(message, cause);
    }

    @Override
    protected ToolInputGuardrailExecutedEventBuilder createEmptyObservabilityEventBuilderInstance() {
        return ToolInputGuardrailExecutedEvent.builder();
    }

    /**
     * Creates and returns a new builder for {@link ToolInputGuardrailExecutor}.
     *
     * @return A {@link ToolInputGuardrailExecutorBuilder} used to create {@link ToolInputGuardrailExecutor} instances
     */
    public static ToolInputGuardrailExecutorBuilder builder() {
        return ServiceLoader.load(ToolInputGuardrailExecutorBuilderFactory.class)
                .findFirst()
                .map(ToolInputGuardrailExecutorBuilderFactory::getBuilder)
                .orElseGet(ToolInputGuardrailExecutorBuilder::new);
    }

    /**
     * Builder class for constructing instances of {@link ToolInputGuardrailExecutor}.
     *
     * @since 1.25.0
     */
    @Experimental
    public static non-sealed class ToolInputGuardrailExecutorBuilder
            extends GuardrailExecutorBuilder<
                    ToolInputGuardrailsConfig,
                    ToolInputGuardrailResult,
                    ToolInputGuardrailRequest,
                    ToolInputGuardrail,
                    ToolInputGuardrailExecutedEvent,
                    ToolInputGuardrailExecutorBuilder> {

        public ToolInputGuardrailExecutorBuilder() {
            super(ToolInputGuardrailsConfig.builder().build());
        }

        @Override
        public ToolInputGuardrailExecutor build() {
            return new ToolInputGuardrailExecutor(config(), guardrails());
        }
    }
}
