package dev.langchain4j.spi.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.observability.api.event.ToolOutputGuardrailExecutedEvent;

/**
 * Represents a factory for creating instances of
 * {@link ToolOutputGuardrailExecutor.ToolOutputGuardrailExecutorBuilder}.
 *
 * @since 1.24.0
 */
@Experimental
public non-sealed interface ToolOutputGuardrailExecutorBuilderFactory
        extends GuardrailExecutorBuilderFactory<
                ToolOutputGuardrailsConfig,
                ToolOutputGuardrailResult,
                ToolOutputGuardrailRequest,
                ToolOutputGuardrail,
                ToolOutputGuardrailExecutedEvent,
                ToolOutputGuardrailExecutor.ToolOutputGuardrailExecutorBuilder> {}
