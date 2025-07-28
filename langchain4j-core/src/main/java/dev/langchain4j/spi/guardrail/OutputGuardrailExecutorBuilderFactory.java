package dev.langchain4j.spi.guardrail;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;

/**
 * Represents a factory for creating instances of {@link OutputGuardrailExecutor.OutputGuardrailExecutorBuilder}.
 * This interface extends {@link GuardrailExecutorBuilderFactory} and is specifically tailored for output guardrails,
 * utilizing configurations, results, requests, and guardrails that are specific to the output context.
 */
public non-sealed interface OutputGuardrailExecutorBuilderFactory
        extends GuardrailExecutorBuilderFactory<
                OutputGuardrailsConfig,
                OutputGuardrailResult,
                OutputGuardrailRequest,
                OutputGuardrail,
                OutputGuardrailExecutor.OutputGuardrailExecutorBuilder> {}
