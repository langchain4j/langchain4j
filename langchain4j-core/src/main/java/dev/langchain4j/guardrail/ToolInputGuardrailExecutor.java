package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import dev.langchain4j.Experimental;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.guardrail.ToolInputGuardrailResult.Failure;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.internal.CancellationChain;
import dev.langchain4j.internal.Json;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent.ToolInputGuardrailExecutedEventBuilder;
import dev.langchain4j.spi.guardrail.ToolInputGuardrailExecutorBuilderFactory;

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
     * Records on the exception of a guardrail that threw the arguments it validated. A
     * {@link ToolArgumentsException} thrown by a guardrail, for example by
     * {@link ToolInputGuardrailRequest#argumentsAsMap()} for arguments that cannot be parsed, is not a guardrail
     * failure: it is thrown as is, so that the caller handles it like an arguments error of the tool.
     * <p>
     *     A guardrail that rewrites the arguments to something that is not a JSON object is a fatal failure: the
     *     rewritten arguments would reach the tool and be stored in the chat memory, where model providers parse them.
     * </p>
     */
    @Override
    protected ToolInputGuardrailResult validate(ToolInputGuardrailRequest request, ToolInputGuardrail guardrail) {
        try {
            var result = super.validate(request, guardrail);
            return ensureValidRewrite(request, guardrail, result);
        } catch (ToolInputGuardrailException e) {
            throw argumentsError(e)
                    .map(RuntimeException.class::cast)
                    .orElseGet(() -> e.arguments(request.arguments()));
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
                try {
                    return ensureValidRewrite(request, guardrail, result);
                } catch (ToolInputGuardrailException invalidRewrite) {
                    throw new CompletionException(invalidRewrite);
                }
            }
            var cause = unwrapCompletionException(error);
            var argumentsError = argumentsError(cause);
            if (argumentsError.isPresent()) {
                throw new CompletionException(argumentsError.get());
            }
            if (cause instanceof ToolInputGuardrailException e) {
                e.arguments(request.arguments());
            }
            throw (error instanceof CompletionException completion) ? completion : new CompletionException(cause);
        });
    }

    /**
     * Throws a {@link ToolInputGuardrailException} if the guardrail rewrote the arguments to something that is not a
     * JSON object.
     */
    private ToolInputGuardrailResult ensureValidRewrite(
            ToolInputGuardrailRequest request, ToolInputGuardrail guardrail, ToolInputGuardrailResult result) {
        if (result.hasRewrittenResult() && !isJsonObject(result.successfulText())) {
            var message = "The tool input guardrail %s rewrote the arguments of tool '%s' to something that is not a "
                    + "JSON object";
            throw createGuardrailException(message.formatted(guardrail.getClass().getName(), request.toolName()), null)
                    .arguments(request.arguments());
        }
        return result;
    }

    private static boolean isJsonObject(String arguments) {
        try {
            return Json.fromJson(arguments, Map.class) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The {@link ToolArgumentsException} a guardrail threw, which the base executor wrapped in a guardrail exception.
     */
    private static Optional<ToolArgumentsException> argumentsError(Throwable error) {
        return Optional.of(error)
                .filter(ToolInputGuardrailException.class::isInstance)
                .map(Throwable::getCause)
                .filter(ToolArgumentsException.class::isInstance)
                .map(ToolArgumentsException.class::cast);
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

        /**
         * Creates a builder with the default configuration.
         */
        public ToolInputGuardrailExecutorBuilder() {
            super(ToolInputGuardrailsConfig.builder().build());
        }

        @Override
        public ToolInputGuardrailExecutor build() {
            return new ToolInputGuardrailExecutor(config(), guardrails());
        }
    }
}
