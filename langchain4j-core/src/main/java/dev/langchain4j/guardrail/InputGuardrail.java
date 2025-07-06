package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrailResult.Failure;

/**
 * An input guardrail is a rule that is applied to the input of the model to ensure that the input (i.e. the user
 * message and parameters) is safe and meets the expectations of the model.
 * <p>
 *     Input guardrails are either successful or failed. A successful guardrail means that the input is valid and can be sent to
 *     the model. A failed guardrail means that the input is invalid and cannot be sent to the model.
 * </p>
 * <p>
 *     A failed guardrail will stop further processing of any other input guardrails.
 * </p>
 */
public interface InputGuardrail extends Guardrail<InputGuardrailRequest, InputGuardrailResult> {
    /**
     * Validates the {@code user message} that will be sent to the LLM.
     * <p>
     *
     * @param userMessage
     *            the response from the LLM
     */
    default InputGuardrailResult validate(UserMessage userMessage) {
        return failure("Validation not implemented");
    }

    /**
     * Validates the input that will be sent to the LLM.
     * <p>
     * Unlike {@link #validate(UserMessage)}, this method allows to access the memory and the augmentation result (in
     * the case of a RAG).
     * <p>
     * Implementation must not attempt to write to the memory or the augmentation result.
     *
     * @param params
     *            the parameters, including the user message, the memory, and the augmentation result.
     */
    @Override
    default InputGuardrailResult validate(InputGuardrailRequest params) {
        ensureNotNull(params, "params");
        return validate(params.userMessage());
    }

    /**
     * Produces a successful result without any successful text
     *
     * @return The result of a successful input guardrail validation.
     */
    default InputGuardrailResult success() {
        return InputGuardrailResult.success();
    }

    /**
     * Produces a successful result with specific success text
     *
     * @return The result of a successful input guardrail validation with a specific text.
     *
     * @param successfulText
     *            The text of the successful result.
     */
    default InputGuardrailResult successWith(String successfulText) {
        return InputGuardrailResult.successWith(successfulText);
    }

    /**
     * Produces a non-fatal failure
     *
     * @param message
     *            A message describing the failure.
     *
     * @return The result of a failed input guardrail validation.
     */
    default InputGuardrailResult failure(String message) {
        return new InputGuardrailResult(new Failure(message), false);
    }

    /**
     * Produces a non-fatal failure
     *
     * @param message
     *            A message describing the failure.
     * @param cause
     *            The exception that caused this failure.
     *
     * @return The result of a failed input guardrail validation.
     */
    default InputGuardrailResult failure(String message, Throwable cause) {
        return new InputGuardrailResult(new Failure(message, cause), false);
    }

    /**
     * Produces a fatal failure
     *
     * @param message
     *            A message describing the failure.
     *
     * @return The result of a failed input guardrail validation.
     */
    default InputGuardrailResult fatal(String message) {
        return new InputGuardrailResult(new Failure(message), true);
    }

    /**
     * Produces a non-fatal failure
     *
     * @param message
     *            A message describing the failure.
     * @param cause
     *            The exception that caused this failure.
     *
     * @return The result of a failed input guardrail validation.
     */
    default InputGuardrailResult fatal(String message, Throwable cause) {
        return new InputGuardrailResult(new Failure(message, cause), true);
    }
}
