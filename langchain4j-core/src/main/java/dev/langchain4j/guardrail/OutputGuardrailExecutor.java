package dev.langchain4j.guardrail;

import static dev.langchain4j.guardrail.OutputGuardrailResult.successWith;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent.OutputGuardrailExecutedEventBuilder;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrailResult.Failure;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.internal.CancellationChain;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.spi.guardrail.OutputGuardrailExecutorBuilderFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
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
                OutputGuardrailsConfig,
                OutputGuardrailRequest,
                OutputGuardrailResult,
                OutputGuardrail,
                OutputGuardrailExecutedEvent,
                Failure> {

    public static final String MAX_RETRIES_MESSAGE_TEMPLATE = """
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
     * @param request     The {@link OutputGuardrailRequest} to validate
     * @return The {@link OutputGuardrailResult} of the validation
     */
    @Override
    public OutputGuardrailResult execute(OutputGuardrailRequest request) {
        OutputGuardrailResult result = null;
        var accumulatedRequest = request;
        var attempt = 0;
        var maxAttempts = config().maxRetries();

        if (maxAttempts == 0) {
            maxAttempts = 1;
        } else if (maxAttempts < 0) {
            maxAttempts = OutputGuardrailsConfig.MAX_RETRIES_DEFAULT;
        }

        while (attempt < maxAttempts) {
            result = rewriteResult(request, accumulatedRequest, executeGuardrails(accumulatedRequest));

            if (result.isSuccess()) {
                return result;
            }

            // Not successful
            if (!result.isRetry()) {
                // Not any kind of retry, so just stop here
                removeViolatingMessageIfRequested(result, request);
                throw new OutputGuardrailException(result.toString(), result.getFirstFailureException(), result);
            }

            if (++attempt < maxAttempts) {
                // If we get here we know it is some kind of retry
                // We don't want to add intermediary UserMessages to the memory
                var chatMessages = Optional.ofNullable(
                                accumulatedRequest.requestParams().chatMemory())
                        .map(ChatMemory::messages)
                        .orElseGet(ArrayList::new);
                result.getReprompt().map(UserMessage::from).ifPresent(chatMessages::add);

                // Re-execute the request with the appended message
                // But don't add it or the resulting message to the memory
                var response = accumulatedRequest.chatExecutor().execute(chatMessages);
                accumulatedRequest = OutputGuardrailRequest.builder()
                        .responseFromLLM(response)
                        .chatExecutor(accumulatedRequest.chatExecutor())
                        .requestParams(accumulatedRequest.requestParams())
                        .build();
            }
        }

        if (attempt == maxAttempts) {
            var failureMessages = result.failures().stream()
                    .map(GuardrailResult.Failure::message)
                    .collect(Collectors.joining(System.lineSeparator()));

            removeViolatingMessageIfRequested(result, request);
            throw new OutputGuardrailException(MAX_RETRIES_MESSAGE_TEMPLATE.formatted(failureMessages), null, result);
        }

        return result;
    }

    /**
     * Non-blocking counterpart of {@link #execute(OutputGuardrailRequest)}. Runs the same reprompt/retry loop, but
     * the per-attempt model re-call goes through {@link ChatExecutor#executeAsync(java.util.List)} so the calling
     * thread is never blocked while the model produces the reprompted response.
     */
    @Override
    public CompletableFuture<OutputGuardrailResult> executeAsync(OutputGuardrailRequest request) {
        var maxAttempts = config().maxRetries();

        if (maxAttempts == 0) {
            maxAttempts = 1;
        } else if (maxAttempts < 0) {
            maxAttempts = OutputGuardrailsConfig.MAX_RETRIES_DEFAULT;
        }

        // Root a cancellation chain at the caller-facing future so cancelling it aborts the in-flight guardrail
        // validations and the reprompt model call (best-effort), mirroring the model/RAG/tool async paths.
        CompletableFuture<OutputGuardrailResult> result = new CompletableFuture<>();
        CancellationChain chain = new CancellationChain(result);
        attemptAsync(request, request, 0, maxAttempts, chain).whenComplete((attemptResult, error) -> {
            if (error != null) {
                result.completeExceptionally(unwrapCompletionException(error));
            } else {
                result.complete(attemptResult);
            }
        });
        return result;
    }

    private CompletableFuture<OutputGuardrailResult> attemptAsync(
            OutputGuardrailRequest request,
            OutputGuardrailRequest accumulatedRequest,
            int attempt,
            int maxAttempts,
            CancellationChain chain) {

        return executeGuardrailsAsync(accumulatedRequest, chain).thenCompose(rawResult -> {
            var result = rewriteResult(request, accumulatedRequest, rawResult);

            if (result.isSuccess()) {
                return CompletableFuture.completedFuture(result);
            }

            // Not successful
            if (!result.isRetry()) {
                // Not any kind of retry, so just stop here
                removeViolatingMessageIfRequested(result, request);
                throw new OutputGuardrailException(result.toString(), result.getFirstFailureException(), result);
            }

            var nextAttempt = attempt + 1;
            if (nextAttempt < maxAttempts) {
                // If we get here we know it is some kind of retry.
                // We don't want to add intermediary UserMessages to the memory.
                var chatMessages = Optional.ofNullable(
                                accumulatedRequest.requestParams().chatMemory())
                        .map(ChatMemory::messages)
                        .orElseGet(ArrayList::new);
                result.getReprompt().map(UserMessage::from).ifPresent(chatMessages::add);

                // Re-execute the request with the appended message without blocking, but don't add it or the
                // resulting message to the memory.
                return chain.track(accumulatedRequest.chatExecutor().executeAsync(chatMessages))
                        .thenCompose(response -> {
                            var nextRequest = OutputGuardrailRequest.builder()
                                    .responseFromLLM(response)
                                    .chatExecutor(accumulatedRequest.chatExecutor())
                                    .requestParams(accumulatedRequest.requestParams())
                                    .build();
                            return attemptAsync(request, nextRequest, nextAttempt, maxAttempts, chain);
                        });
            }

            var failureMessages = result.failures().stream()
                    .map(GuardrailResult.Failure::message)
                    .collect(Collectors.joining(System.lineSeparator()));

            removeViolatingMessageIfRequested(result, request);
            throw new OutputGuardrailException(MAX_RETRIES_MESSAGE_TEMPLATE.formatted(failureMessages), null, result);
        });
    }

    private void removeViolatingMessageIfRequested(OutputGuardrailResult result, OutputGuardrailRequest request) {
        if (!result.shouldRemoveViolatingMessage()) {
            return;
        }
        ChatMemory memory = request.requestParams().chatMemory();
        if (memory == null) {
            return;
        }
        // Remove the last AiMessage — the one that failed the guardrail
        var messages = new java.util.ArrayList<>(memory.messages());
        var it = messages.listIterator(messages.size());
        while (it.hasPrevious()) {
            ChatMessage msg = it.previous();
            if (msg instanceof AiMessage) {
                it.remove();
                break;
            }
        }
        memory.clear();
        messages.forEach(memory::add);
    }

    private OutputGuardrailResult rewriteResult(
            OutputGuardrailRequest originalRequest,
            OutputGuardrailRequest validatedRequest,
            OutputGuardrailResult result) {
        if (result.isSuccess() && !result.hasRewrittenResult()) {
            String originalText = originalRequest.responseFromLLM().aiMessage().text();
            String validatedText =
                    validatedRequest.responseFromLLM().aiMessage().text();
            if (!originalText.equals(validatedText)) {
                // The text validated by the output guardrail is different form the original one because of a
                // successful reprompt, so we need to create a new success result with the new text
                return successWith(originalRequest.responseFromLLM().aiMessage().withText(validatedText));
            }
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

    @Override
    protected OutputGuardrailExecutedEventBuilder createEmptyObservabilityEventBuilderInstance() {
        return OutputGuardrailExecutedEvent.builder();
    }

    /**
     * Creates a new instance of {@link OutputGuardrailExecutorBuilder}.
     * The builder is used to construct and configure instances of {@link OutputGuardrailExecutorBuilder}.
     * @return A new {@link OutputGuardrailExecutorBuilder} instance.
     */
    public static OutputGuardrailExecutorBuilder builder() {
        return ServiceLoader.load(OutputGuardrailExecutorBuilderFactory.class)
                .findFirst()
                .map(OutputGuardrailExecutorBuilderFactory::getBuilder)
                .orElseGet(OutputGuardrailExecutorBuilder::new);
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
                    OutputGuardrailExecutedEvent,
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
