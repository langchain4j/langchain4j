package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

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
 *            The type of {@link GuardrailParams} to validate
 * @param <R>
 *            The type of {@link GuardrailResult} to return
 * @param <G>
 *            The type of {@link Guardrail}s being executed
 */
public interface GuardrailExecutor<
        C extends GuardrailsConfig,
        P extends GuardrailParams,
        R extends GuardrailResult<R>,
        G extends Guardrail<P, R>> {
    /**
     * The {@link GuardrailsConfig} to use for configuration of the guardrail execution
     * @return The {@link GuardrailsConfig} to use for configuration of the guardrail execution
     */
    C config();

    /**
     * Executes the provided guardrails on the given parameters.
     * @param params The {@link GuardrailParams} to validate
     * @param guardrails The {@link Guardrail}s to evaluate against
     * @return The {@link GuardrailResult} of the validation
     */
    default R execute(P params, G... guardrails) {
        ensureNotNull(params, "params");
        ensureNotNull(guardrails, "guardrails");

        return execute(params, List.of(guardrails));
    }

    /**
     * Executes the provided guardrails on the given parameters.
     * @param params The {@link GuardrailParams} to validate
     * @param guardrails The {@link Guardrail}s to evaluate against
     * @return The {@link GuardrailResult} of the validation
     */
    R execute(P params, Iterable<G> guardrails);
}
