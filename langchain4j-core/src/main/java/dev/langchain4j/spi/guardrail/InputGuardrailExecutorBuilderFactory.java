package dev.langchain4j.spi.guardrail;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;

/**
 * Represents a factory for creating instances of {@link InputGuardrailExecutor.InputGuardrailExecutorBuilder}.
 * This non-sealed interface extends from the sealed interface {@link GuardrailExecutorBuilderFactory} and is specifically tailored
 * for input guardrails. It provides methods to configure and build execution environments for guardrails that operate on inputs,
 * ensuring that they adhere to predefined rules or constraints.
 */
public non-sealed interface InputGuardrailExecutorBuilderFactory
        extends GuardrailExecutorBuilderFactory<
                InputGuardrailsConfig,
                InputGuardrailResult,
                InputGuardrailRequest,
                InputGuardrail,
                InputGuardrailExecutor.InputGuardrailExecutorBuilder> {}
