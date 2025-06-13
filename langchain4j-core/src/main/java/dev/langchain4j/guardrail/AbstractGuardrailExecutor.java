package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.guardrail.GuardrailResult.Failure;
import dev.langchain4j.guardrail.config.GuardrailsConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for {@link GuardrailExecutor}s.
 * @param <C>
 *            The type of {@link GuardrailsConfig} to use for configuration
 * @param <P>
 *            The type of {@link GuardrailRequest} to validate
 * @param <R>
 *            The type of {@link GuardrailResult} to return
 * @param <G>
 *            The type of {@link Guardrail}s being executed
 * @param <F>
 *            The type of {@link Failure} to return
 */
@Internal
public abstract sealed class AbstractGuardrailExecutor<
                C extends GuardrailsConfig,
                P extends GuardrailRequest<P>,
                R extends GuardrailResult<R>,
                G extends Guardrail<P, R>,
                F extends Failure>
        implements GuardrailExecutor<C, P, R, G> permits InputGuardrailExecutor, OutputGuardrailExecutor {

    private final C config;
    private final List<G> guardrails;

    protected AbstractGuardrailExecutor(C config, List<G> guardrails) {
        ensureNotNull(config, "config");
        this.config = config;
        this.guardrails = Optional.ofNullable(guardrails).orElseGet(List::of);
    }

    /**
     * Creates a failure result from some {@link Failure}s.
     * @param failures The failures
     * @return A {@link GuardrailResult} containing the failures
     */
    protected abstract R createFailure(List<F> failures);

    /**
     * Creates a success result.
     * @return A {@link GuardrailResult} representing success
     */
    protected abstract R createSuccess();

    /**
     * Creates a {@link GuardrailException} using the provided message and optional cause.
     *
     * @param message The detailed message for the exception.
     * @param cause   The underlying cause of the exception, or null if no cause is available.
     * @return A new instance of {@link GuardrailException} constructed with the provided message and cause.
     */
    protected abstract GuardrailException createGuardrailException(String message, Throwable cause);

    @Override
    public C config() {
        return this.config;
    }

    @Override
    public List<G> guardrails() {
        return this.guardrails;
    }

    /**
     * Validates a guardrail against a set of params.
     * <p>
     *     If any kind of {@link Exception} is thrown during validation, it will be wrapped in a {@link GuardrailException}.
     * </p>
     * @param params The {@link GuardrailRequest} to validate
     * @param guardrail The {@link Guardrail} to evaluate against
     * @throws GuardrailException If any kind of {@link Exception} is thrown during validation
     * @return The {@link GuardrailResult} of the validation
     */
    protected R validate(P params, G guardrail) {
        ensureNotNull(params, "params");
        ensureNotNull(guardrail, "guardrail");

        try {
            return guardrail.validate(params).validatedBy(guardrail.getClass());
        } catch (Exception e) {
            throw createGuardrailException(e.getMessage(), e);
        }
    }

    /**
     * Handles a fatal result.
     * @param accumulatedResult The accumulated result
     * @param result The fatal result
     * @return The fatal result, possibly wrapped/modified in some way
     */
    protected R handleFatalResult(R accumulatedResult, R result) {
        return result;
    }

    protected R executeGuardrails(P params) {
        ensureNotNull(params, "params");

        var accumulatedResult = createSuccess();
        var accumulatedParams = params;

        for (var guardrail : this.guardrails) {
            if (guardrail != null) {
                var result = validate(accumulatedParams, guardrail);

                if (result.isFatal()) {
                    // Fatal result, so stop right here and don't do any more processing
                    return handleFatalResult(accumulatedResult, result);
                }

                if (result.hasRewrittenResult()) {
                    accumulatedParams = accumulatedParams.withText(result.successfulText());
                }

                accumulatedResult = composeResult(accumulatedResult, result);
            }
        }

        return accumulatedResult;
    }

    protected R composeResult(R oldResult, R newResult) {
        if (oldResult.isSuccess()) {
            return newResult;
        }

        if (newResult.isSuccess()) {
            return oldResult;
        }

        var failures = new ArrayList<F>(oldResult.failures());
        failures.addAll(newResult.failures());

        return createFailure(failures);
    }

    /**
     * A generic abstract builder class for creating instances of {@link GuardrailExecutor}.
     *
     * @param <C>
     *            The type of {@link GuardrailsConfig} to use for configuration
     * @param <P>
     *            The type of {@link GuardrailRequest} to validate
     * @param <R>
     *            The type of {@link GuardrailResult} to return
     * @param <G>
     *            The type of {@link Guardrail}s being executed
     *
     * This class is sealed to restrict subclassing to only specific permitted classes, such as
     * {@link InputGuardrailExecutor.InputGuardrailExecutorBuilder} and
     * {@link OutputGuardrailExecutor.OutputGuardrailExecutorBuilder}.
     *
     * It provides methods to configure and manage the guardrails and their associated configurations,
     * eventually culminating in the construction of a specific {@link GuardrailExecutor}.
     */
    public abstract static sealed class GuardrailExecutorBuilder<
                    C extends GuardrailsConfig,
                    R extends GuardrailResult<R>,
                    P extends GuardrailRequest<P>,
                    G extends Guardrail<P, R>,
                    B extends GuardrailExecutorBuilder<C, R, P, G, B>>
            permits InputGuardrailExecutor.InputGuardrailExecutorBuilder,
                    OutputGuardrailExecutor.OutputGuardrailExecutorBuilder {

        private final C defaultConfig;
        private C config;
        private List<G> guardrails = new ArrayList<>();

        protected GuardrailExecutorBuilder(C defaultConfig) {
            this.defaultConfig = ensureNotNull(defaultConfig, "defaultConfig");
        }

        /**
         * Constructs and returns an instance of {@link GuardrailExecutor}.
         *
         * This method finalizes the building process, using the configuration and guardrails
         * provided, to create a fully-formed {@link GuardrailExecutor} instance. The returned
         * instance enables execution of guardrails on given parameters.
         *
         * @return A fully initialized instance of {@link GuardrailExecutor}, ready to validate
         *         interactions based on the configured guardrails and parameters.
         */
        public abstract GuardrailExecutor<C, P, R, G> build();

        /**
         * Retrieves the current configuration instance used by this builder.
         *
         * @return The configuration set in the builder.
         */
        protected C config() {
            return (this.config != null) ? this.config : this.defaultConfig;
        }

        /**
         * Retrieves the list of guardrails configured in the builder.
         * Guardrails are validation rules applied to interactions with the model, ensuring that inputs or outputs
         * meet required conditions for safety and correctness.
         *
         * @return A list containing the configured guardrails.
         */
        protected List<G> guardrails() {
            return this.guardrails;
        }

        /**
         * Sets the configuration for the guardrail executor builder.
         *
         * @param config The configuration instance to be set, which implements {@link GuardrailsConfig}.
         *               This can be null if no specific configuration is required.
         * @return The updated instance of the builder, allowing for method chaining.
         */
        public B config(C config) {
            this.config = config;
            return (B) this;
        }

        /**
         * Updates the list of guardrails for the builder. The provided guardrails will replace
         * the current list of guardrails in the builder. If the provided list is null, all
         * existing guardrails will be cleared.
         *
         * @param guardrails A list of guardrails to be set for the builder. It can be null,
         *                   in which case the current list of guardrails will be cleared.
         * @return The updated instance of the builder, allowing for method chaining.
         */
        public B guardrails(List<G> guardrails) {
            this.guardrails.clear();

            if (guardrails != null) {
                this.guardrails.addAll(guardrails);
            }

            return (B) this;
        }

        /**
         * Updates the builder with the specified guardrails. This method accepts
         * a variadic array of guardrails, which will be used to replace the current
         * set of guardrails in the builder. If the input is null, the existing
         * guardrails will remain unchanged.
         *
         * @param guardrails An optional array of guardrails to be set for the builder.
         *                   Null values are accepted and will not clear existing guardrails.
         * @return The updated instance of the builder, allowing for method chaining.
         */
        public B guardrails(G... guardrails) {
            Optional.ofNullable(guardrails).map(List::of).ifPresent(this::guardrails);

            return (B) this;
        }
    }
}
