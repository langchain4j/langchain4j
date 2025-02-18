package dev.langchain4j.guardrail;

import dev.langchain4j.guardrail.InputGuardrailResult.Failure;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * The {@link GuardrailExecutor} for {@link InputGuardrail}s.
 */
public class InputGuardrailExecutor
        extends AbstractGuardrailExecutor<
                InputGuardrailsConfig, InputGuardrailParams, InputGuardrailResult, InputGuardrail, Failure> {

    public InputGuardrailExecutor(InputGuardrailsConfig config) {
        super(config);
    }

    /**
     * Creates a failure result from some {@link Failure}s.
     * @param failures The failures
     * @return A {@link InputGuardrailResult} containing the failures
     */
    @Override
    protected InputGuardrailResult createFailure(List<@NonNull Failure> failures) {
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

    /**
     * Execeutes the {@link InputGuardrail}s on the given {@link InputGuardrailParams}.
     *
     * @param params     The {@link InputGuardrailParams} to validate
     * @param guardrails The {@link InputGuardrail}s to evaluate against
     * @return The {@link InputGuardrailResult} of the validation
     */
    @Override
    public InputGuardrailResult execute(InputGuardrailParams params, Iterable<InputGuardrail> guardrails) {
        var result = executeGuardrails(params, guardrails);

        if (!result.isSuccess()) {
            throw new GuardrailException(result.toString(), result.getFirstFailureException());
        }

        return result;
    }
}
