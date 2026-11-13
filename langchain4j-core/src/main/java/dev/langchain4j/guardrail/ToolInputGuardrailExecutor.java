package dev.langchain4j.guardrail;

import java.util.List;
import java.util.ServiceLoader;
import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolInputGuardrailResult.Failure;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
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
 * @since 1.24.0
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
     * @since 1.24.0
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
