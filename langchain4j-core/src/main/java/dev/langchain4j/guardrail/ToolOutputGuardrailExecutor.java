package dev.langchain4j.guardrail;

import java.util.List;
import java.util.ServiceLoader;
import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult.Failure;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.observability.api.event.ToolOutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolOutputGuardrailExecutedEvent.ToolOutputGuardrailExecutedEventBuilder;
import dev.langchain4j.spi.guardrail.ToolOutputGuardrailExecutorBuilderFactory;

/**
 * The {@link GuardrailExecutor} for {@link ToolOutputGuardrail}s.
 * <p>
 *     It runs the guardrails once, with no retry or reprompt. A non-fatal failure does not stop the chain, and
 *     {@code execute(...)} returns it instead of throwing, so the caller can turn it into an
 *     error tool result. A fatal failure stops the chain and throws a {@link ToolOutputGuardrailException}.
 * </p>
 *
 * @since 1.24.0
 */
@Experimental
public non-sealed class ToolOutputGuardrailExecutor
        extends AbstractToolGuardrailExecutor<
                ToolOutputGuardrailsConfig,
                ToolOutputGuardrailRequest,
                ToolOutputGuardrailResult,
                ToolOutputGuardrail,
                ToolOutputGuardrailExecutedEvent,
                Failure> {

    protected ToolOutputGuardrailExecutor(ToolOutputGuardrailsConfig config, List<ToolOutputGuardrail> guardrails) {
        super(config, guardrails);
    }

    @Override
    protected ToolOutputGuardrailResult createFailure(List<Failure> failures) {
        return new ToolOutputGuardrailResult(failures, false);
    }

    @Override
    protected ToolOutputGuardrailResult createSuccess() {
        return ToolOutputGuardrailResult.success();
    }

    @Override
    protected ToolOutputGuardrailException createGuardrailException(String message, Throwable cause) {
        return new ToolOutputGuardrailException(message, cause);
    }

    @Override
    protected ToolOutputGuardrailExecutedEventBuilder createEmptyObservabilityEventBuilderInstance() {
        return ToolOutputGuardrailExecutedEvent.builder();
    }

    /**
     * Chains a rewrite of the contents and error flag, not only of the text, to the next guardrail.
     */
    @Override
    protected ToolOutputGuardrailRequest rewriteRequest(
            ToolOutputGuardrailRequest request, ToolOutputGuardrailResult result) {
        return result.apply(request);
    }

    /**
     * Composes the results like {@link AbstractGuardrailExecutor#composeResult(GuardrailResult, GuardrailResult)},
     * additionally making sure that a text rewrite following a contents rewrite does not lose the latter: the
     * composed result is the contents rewrite with the text replaced.
     */
    @Override
    protected ToolOutputGuardrailResult composeResult(
            ToolOutputGuardrailResult oldResult, ToolOutputGuardrailResult newResult) {
        return (oldResult.isSuccess() && newResult.isSuccess())
                ? newResult.composedAfter(oldResult)
                : super.composeResult(oldResult, newResult);
    }

    /**
     * Creates and returns a new builder for {@link ToolOutputGuardrailExecutor}.
     *
     * @return A {@link ToolOutputGuardrailExecutorBuilder} used to create {@link ToolOutputGuardrailExecutor} instances
     */
    public static ToolOutputGuardrailExecutorBuilder builder() {
        return ServiceLoader.load(ToolOutputGuardrailExecutorBuilderFactory.class)
                .findFirst()
                .map(ToolOutputGuardrailExecutorBuilderFactory::getBuilder)
                .orElseGet(ToolOutputGuardrailExecutorBuilder::new);
    }

    /**
     * Builder class for constructing instances of {@link ToolOutputGuardrailExecutor}.
     *
     * @since 1.24.0
     */
    @Experimental
    public static non-sealed class ToolOutputGuardrailExecutorBuilder
            extends GuardrailExecutorBuilder<
                    ToolOutputGuardrailsConfig,
                    ToolOutputGuardrailResult,
                    ToolOutputGuardrailRequest,
                    ToolOutputGuardrail,
                    ToolOutputGuardrailExecutedEvent,
                    ToolOutputGuardrailExecutorBuilder> {

        public ToolOutputGuardrailExecutorBuilder() {
            super(ToolOutputGuardrailsConfig.builder().build());
        }

        @Override
        public ToolOutputGuardrailExecutor build() {
            return new ToolOutputGuardrailExecutor(config(), guardrails());
        }
    }
}
