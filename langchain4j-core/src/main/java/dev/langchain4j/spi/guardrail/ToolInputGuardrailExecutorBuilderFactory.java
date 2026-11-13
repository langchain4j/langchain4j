package dev.langchain4j.spi.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailExecutor;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;

/**
 * Represents a factory for creating instances of
 * {@link ToolInputGuardrailExecutor.ToolInputGuardrailExecutorBuilder}.
 *
 * @since 1.24.0
 */
@Experimental
public non-sealed interface ToolInputGuardrailExecutorBuilderFactory
        extends GuardrailExecutorBuilderFactory<
                ToolInputGuardrailsConfig,
                ToolInputGuardrailResult,
                ToolInputGuardrailRequest,
                ToolInputGuardrail,
                ToolInputGuardrailExecutedEvent,
                ToolInputGuardrailExecutor.ToolInputGuardrailExecutorBuilder> {}
