package dev.langchain4j.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrailResult.Failure;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The {@link GuardrailExecutor} for {@link OutputGuardrail}s.
 */
public final class OutputGuardrailExecutor
        extends AbstractGuardrailExecutor<
                OutputGuardrailsConfig, OutputGuardrailParams, OutputGuardrailResult, OutputGuardrail, Failure> {

    public static final String MAX_RETRIES_MESSAGE_TEMPLATE =
            """
            Output validation failed. The guardrails have reached the maximum number of retries.
            Guardrail messages:

            %s""";

    public OutputGuardrailExecutor(OutputGuardrailsConfig config, @Nullable List<OutputGuardrail> guardrails) {
        super(config, guardrails);
    }

    /**
     * Creates a failure result from some {@link Failure}s.
     * @param failures The failures
     * @return A {@link OutputGuardrailResult} containing the failures
     */
    @Override
    protected OutputGuardrailResult createFailure(List<@NonNull Failure> failures) {
        return OutputGuardrailResult.failure(failures);
    }

    /**
     * Creates a success result.
     * @return A {@link OutputGuardrailResult} representing success
     */
    @Override
    protected OutputGuardrailResult createSuccess() {
        return OutputGuardrailResult.success();
    }

    /**
     * Executes the {@link OutputGuardrail}s on the given {@link OutputGuardrailParams}.
     *
     * @param params     The {@link OutputGuardrailParams} to validate
     * @return The {@link OutputGuardrailResult} of the validation
     */
    @Override
    public OutputGuardrailResult execute(OutputGuardrailParams params) {
        OutputGuardrailResult result = null;
        var accumulatedParams = params;
        var attempt = 0;
        var maxAttempts =
                (config().maxRetries() > 0) ? config().maxRetries() : OutputGuardrailsConfig.MAX_RETRIES_DEFAULT;

        while (attempt < maxAttempts) {
            result = executeGuardrails(accumulatedParams);

            if (result.isSuccess()) {
                return result;
            }

            // Not successful
            if (!result.isRetry()) {
                // Not any kind of retry, so just stop here
                throw new GuardrailException(result.toString(), result.getFirstFailureException());
            }

            // If we get here we know it is some kind of retry
            result.getReprompt().map(UserMessage::from).ifPresent(accumulatedParams.chatMemory()::add);

            // Re-execute the request
            var response = accumulatedParams.chatExecutor().get();

            // Add response to the chat memory
            params.chatMemory().add(response.aiMessage());
            attempt++;
            accumulatedParams = new OutputGuardrailParams(
                    response,
                    accumulatedParams.chatExecutor(),
                    accumulatedParams.chatMemory(),
                    accumulatedParams.augmentationResult(),
                    accumulatedParams.userMessageTemplate(),
                    accumulatedParams.variables());
        }

        if (attempt == maxAttempts) {
            var failureMessages = result.failures().stream()
                    .map(Failure::message)
                    .collect(Collectors.joining(System.lineSeparator()));

            throw new GuardrailException(MAX_RETRIES_MESSAGE_TEMPLATE.formatted(failureMessages));
        }

        return result;
    }

    @Override
    protected OutputGuardrailResult handleFatalResult(OutputGuardrailResult result) {
        return result.hasRewrittenResult() ? result.blockRetry() : result;
    }

    /**
     * Creates a new instance of {@link OutputGuardrailExecutorBuilder}.
     * The builder is used to construct and configure instances of {@link OutputGuardrailExecutor}.
     *
     * @return A new {@link OutputGuardrailExecutorBuilder} instance.
     */
    public static OutputGuardrailExecutorBuilder builder() {
        return new OutputGuardrailExecutorBuilder();
    }

    /**
     * Builder class for constructing instances of {@link OutputGuardrailExecutor}.
     *
     * This builder allows configuration of an {@link OutputGuardrailExecutor} by specifying the associated configuration
     * type ({@link OutputGuardrailsConfig}) and the output guardrails to be executed.
     *
     * Extends {@link GuardrailExecutorBuilder} for the specific types:
     * - Configuration type: {@link OutputGuardrailsConfig}
     * - Result type: {@link OutputGuardrailResult}
     * - Parameter type: {@link OutputGuardrailParams}
     * - Guardrail type: {@link OutputGuardrail}
     *
     * Provides the {@code build()} method to create an {@link OutputGuardrailExecutor} instance.
     */
    public static final class OutputGuardrailExecutorBuilder
            extends GuardrailExecutorBuilder<
                    OutputGuardrailsConfig,
                    OutputGuardrailResult,
                    OutputGuardrailParams,
                    OutputGuardrail,
                    OutputGuardrailExecutorBuilder> {
        public OutputGuardrailExecutorBuilder() {
            super(OutputGuardrailsConfig.builder().build());
        }

        @Override
        public OutputGuardrailExecutor build() {
            return new OutputGuardrailExecutor(config(), guardrails());
        }
    }
}
