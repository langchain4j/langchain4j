package dev.langchain4j.guardrail;

import dev.langchain4j.guardrail.InputGuardrailResult.Failure;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import java.util.List;

/**
 * The {@link GuardrailExecutor} for {@link InputGuardrail}s.
 */
public non-sealed class InputGuardrailExecutor
        extends AbstractGuardrailExecutor<
                InputGuardrailsConfig, InputGuardrailRequest, InputGuardrailResult, InputGuardrail, Failure> {

    protected InputGuardrailExecutor(InputGuardrailsConfig config, List<InputGuardrail> guardrails) {
        super(config, guardrails);
    }

    /**
     * Creates a failure result from some {@link Failure}s.
     * @param failures The failures
     * @return A {@link InputGuardrailResult} containing the failures
     */
    @Override
    protected InputGuardrailResult createFailure(List<Failure> failures) {
        return new InputGuardrailResult(failures, false);
    }

    /**
     * Creates a success result.
     * @return A {@link InputGuardrailResult} representing success
     */
    @Override
    protected InputGuardrailResult createSuccess() {
        return InputGuardrailResult.success();
    }

    @Override
    protected InputGuardrailException createGuardrailException(String message, Throwable cause) {
        return new InputGuardrailException(message, cause);
    }

    /**
     * Execeutes the {@link InputGuardrail}s on the given {@link InputGuardrailRequest}.
     *
     * @param params     The {@link InputGuardrailRequest} to validate
     * @return The {@link InputGuardrailResult} of the validation
     */
    @Override
    public InputGuardrailResult execute(InputGuardrailRequest params) {
        var result = executeGuardrails(params);

        if (!result.isSuccess()) {
            throw new InputGuardrailException(result.toString(), result.getFirstFailureException());
        }

        return result;
    }

    /**
     * Creates and returns a new builder for {@link InputGuardrailExecutor}.
     *
     * This builder allows for constructing and configuring an {@link InputGuardrailExecutor}
     * instance, enabling customization of parameters such as the configuration and input guardrails.
     *
     * @return An {@link InputGuardrailExecutorBuilder} used to create {@link InputGuardrailExecutor} instances
     */
    public static InputGuardrailExecutorBuilder builder() {
        return new InputGuardrailExecutorBuilder();
    }

    /**
     * Builder class for constructing instances of {@link InputGuardrailExecutor}.
     *
     * This builder allows configuration of an {@link InputGuardrailExecutor} by specifying the associated configuration
     * type ({@link InputGuardrailsConfig}) and the input guardrails to be executed.
     *
     * Extends {@link GuardrailExecutorBuilder} for the specific types:
     * - Configuration type: {@link InputGuardrailsConfig}
     * - Result type: {@link InputGuardrailResult}
     * - Parameter type: {@link InputGuardrailRequest}
     * - Guardrail type: {@link InputGuardrail}
     *
     * Provides the {@code build()} method to create an {@link InputGuardrailExecutor} instance.
     */
    public static non-sealed class InputGuardrailExecutorBuilder
            extends GuardrailExecutorBuilder<
                    InputGuardrailsConfig,
                    InputGuardrailResult,
                    InputGuardrailRequest,
                    InputGuardrail,
                    InputGuardrailExecutorBuilder> {
        public InputGuardrailExecutorBuilder() {
            super(InputGuardrailsConfig.builder().build());
        }

        @Override
        public InputGuardrailExecutor build() {
            return new InputGuardrailExecutor(config(), guardrails());
        }
    }
}
