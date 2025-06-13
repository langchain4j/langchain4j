package dev.langchain4j.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrailResult.Failure;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.memory.ChatMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The {@link GuardrailExecutor} for {@link OutputGuardrail}s.
 * <p>
 *     When executing output guardrails, if any {@link OutputGuardrail} triggers a reprompt or retry,
 *     the new response has to go back through the entire chain of output guardrails to ensure the new response
 *     passes all the output guardrails.
 * </p>
 */
public non-sealed class OutputGuardrailExecutor
        extends AbstractGuardrailExecutor<
                OutputGuardrailsConfig, OutputGuardrailRequest, OutputGuardrailResult, OutputGuardrail, Failure> {

    public static final String MAX_RETRIES_MESSAGE_TEMPLATE =
            """
            Output validation failed. The guardrails have reached the maximum number of retries.
            Guardrail messages:

            %s
            """;

    protected OutputGuardrailExecutor(OutputGuardrailsConfig config, List<OutputGuardrail> guardrails) {
        super(config, guardrails);
    }

    /**
     * Executes the {@link OutputGuardrail}s on the given {@link OutputGuardrailRequest}.
     *
     * @param params     The {@link OutputGuardrailRequest} to validate
     * @return The {@link OutputGuardrailResult} of the validation
     */
    @Override
    public OutputGuardrailResult execute(OutputGuardrailRequest params) {
        OutputGuardrailResult result = null;
        var accumulatedParams = params;
        var attempt = 0;
        var maxAttempts = config().maxRetries();

        if (maxAttempts == 0) {
            maxAttempts = 1;
        } else if (maxAttempts < 0) {
            maxAttempts = OutputGuardrailsConfig.MAX_RETRIES_DEFAULT;
        }

        while (attempt < maxAttempts) {
            result = executeGuardrails(accumulatedParams);

            if (result.isSuccess()) {
                return result;
            }

            // Not successful
            if (!result.isRetry()) {
                // Not any kind of retry, so just stop here
                throw new OutputGuardrailException(result.toString(), result.getFirstFailureException());
            }

            // If we get here we know it is some kind of retry
            // We don't want to add intermediary UserMessages to the memory
            var chatMessages = Optional.ofNullable(
                            accumulatedParams.requestParams().chatMemory())
                    .map(ChatMemory::messages)
                    .orElseGet(ArrayList::new);
            result.getReprompt().map(UserMessage::from).ifPresent(chatMessages::add);

            // Re-execute the request with the appended message
            // But don't add it or the resulting message to the memory
            var response = accumulatedParams.chatExecutor().execute(chatMessages);

            attempt++;
            accumulatedParams = OutputGuardrailRequest.builder()
                    .responseFromLLM(response)
                    .chatExecutor(accumulatedParams.chatExecutor())
                    .requestParams(accumulatedParams.requestParams())
                    .build();
        }

        if (attempt == maxAttempts) {
            var failureMessages = result.failures().stream()
                    .map(GuardrailResult.Failure::message)
                    .collect(Collectors.joining(System.lineSeparator()));

            throw new OutputGuardrailException(MAX_RETRIES_MESSAGE_TEMPLATE.formatted(failureMessages));
        }

        return result;
    }

    /**
     * Creates a failure result from some {@link Failure}s.
     * @param failures The failures
     * @return A {@link OutputGuardrailResult} containing the failures
     */
    @Override
    protected OutputGuardrailResult createFailure(List<Failure> failures) {
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

    @Override
    protected OutputGuardrailException createGuardrailException(String message, Throwable cause) {
        return new OutputGuardrailException(message, cause);
    }

    @Override
    protected OutputGuardrailResult handleFatalResult(
            OutputGuardrailResult accumulatedResult, OutputGuardrailResult result) {
        return accumulatedResult.hasRewrittenResult() ? result.blockRetry() : result;
    }

    /**
     * Creates a new instance of {@link OutputGuardrailExecutorBuilder}.
     * The builder is used to construct and configure instances of {@link OutputGuardrailExecutorBuilder}.
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
     * - Parameter type: {@link OutputGuardrailRequest}
     * - Guardrail type: {@link OutputGuardrail}
     *
     * Provides the {@code build()} method to create an {@link OutputGuardrailExecutor} instance.
     */
    public static non-sealed class OutputGuardrailExecutorBuilder
            extends GuardrailExecutorBuilder<
                    OutputGuardrailsConfig,
                    OutputGuardrailResult,
                    OutputGuardrailRequest,
                    OutputGuardrail,
                    OutputGuardrailExecutorBuilder> {

        protected OutputGuardrailExecutorBuilder() {
            super(OutputGuardrailsConfig.builder().build());
        }

        @Override
        public OutputGuardrailExecutor build() {
            return new OutputGuardrailExecutor(config(), guardrails());
        }
    }
}
