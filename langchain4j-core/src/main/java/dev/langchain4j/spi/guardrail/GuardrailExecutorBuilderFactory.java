package dev.langchain4j.spi.guardrail;

import dev.langchain4j.guardrail.AbstractGuardrailExecutor.GuardrailExecutorBuilder;
import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailExecutor;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.config.GuardrailsConfig;

/**
 * Represents a factory for creating instances of {@link GuardrailExecutorBuilder}.
 * This interface is sealed and can only be extended by specific implementations like
 * {@code InputGuardrailExecutorBuilderFactory} and {@code OutputGuardrailExecutorBuilderFactory}.
 *
 * @param <C> the type of guardrails configuration, extending from {@link GuardrailsConfig}
 * @param <R> the type of guardrail result, extending from {@link GuardrailResult}
 * @param <P> the type of guardrail request, extending from {@link GuardrailRequest}
 * @param <G> the type of guardrail, extending from {@link Guardrail}
 * @param <B> the type of builder for creating {@link GuardrailExecutor}, extending from {@link GuardrailExecutorBuilder}
 */
public sealed interface GuardrailExecutorBuilderFactory<
                C extends GuardrailsConfig,
                R extends GuardrailResult<R>,
                P extends GuardrailRequest<P>,
                G extends Guardrail<P, R>,
                B extends GuardrailExecutorBuilder<C, R, P, G, B>>
        permits InputGuardrailExecutorBuilderFactory, OutputGuardrailExecutorBuilderFactory {

    /**
     * Retrieves a builder for creating instances of {@link GuardrailExecutor}.
     * @return A new instance of type {@link B}, which is a builder extending from {@link GuardrailExecutorBuilder}.
     */
    B getBuilder();
}
