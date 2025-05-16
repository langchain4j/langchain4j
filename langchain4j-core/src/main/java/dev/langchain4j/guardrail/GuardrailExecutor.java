package dev.langchain4j.guardrail;

import dev.langchain4j.guardrail.config.GuardrailsConfig;
import java.util.List;

/**
 * Represents a mechanism to execute a set of guardrails on given parameters.
 * This interface defines the contract for validating interactions (input or output)
 * using multiple guardrails.
 *
 * @param <C>
 *            The type of {@link GuardrailsConfig} to use for configuration
 * @param <P>
 *            The type of {@link GuardrailRequest} to validate
 * @param <R>
 *            The type of {@link GuardrailResult} to return
 * @param <G>
 *            The type of {@link Guardrail}s being executed
 */
public sealed interface GuardrailExecutor<
                C extends GuardrailsConfig,
                P extends GuardrailRequest,
                R extends GuardrailResult<R>,
                G extends Guardrail<P, R>>
        permits AbstractGuardrailExecutor {

    /**
     * The {@link GuardrailsConfig} to use for configuration of the guardrail execution
     * @return The {@link GuardrailsConfig} to use for configuration of the guardrail execution
     */
    C config();

    /**
     * Retrieves the guardrails associated with the implementation.
     * @return The guardrails which can be used for validating inputs or outputs against predefined rules.
     */
    List<G> guardrails();

    /**
     * Executes the provided guardrails on the given parameters.
     * @param params The {@link GuardrailRequest} to validate
     * @return The {@link GuardrailResult} of the validation
     */
    R execute(P params);
}
