package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The result of the validation of an {@link InputGuardrail}
 *
 * @param result
 *            The result of the input guardrail validation
 * @param failures
 *            The list of failures, empty if the validation succeeded
 */
public record InputGuardrailResult(Result result, @Nullable String successfulText, @Nullable List<Failure> failures)
        implements GuardrailResult<InputGuardrailResult> {

    private static final InputGuardrailResult SUCCESS = new InputGuardrailResult();

    public InputGuardrailResult {
        ensureNotNull(result, "result");
        failures = Optional.ofNullable(failures).orElseGet(List::of);
    }

    private InputGuardrailResult() {
        this(Result.SUCCESS, null, Collections.emptyList());
    }

    InputGuardrailResult(@Nullable List<@NonNull Failure> failures, boolean fatal) {
        this(fatal ? Result.FATAL : Result.FAILURE, null, failures);
    }

    InputGuardrailResult(Failure failure, boolean fatal) {
        this(new ArrayList<>(List.of(failure)), fatal);
    }

    private InputGuardrailResult(String successfulText) {
        this(Result.SUCCESS_WITH_RESULT, successfulText, Collections.emptyList());
    }

    /**
     * Gets a successful input guardrail result
     */
    public static InputGuardrailResult success() {
        return SUCCESS;
    }

    /**
     * Produces a successful result with specific success text
     *
     * @return The result of a successful input guardrail validation with a specific text.
     *
     * @param successfulText
     *            The text of the successful result.
     */
    public static InputGuardrailResult successWith(String successfulText) {
        return (successfulText == null) ? success() : new InputGuardrailResult(successfulText);
    }

    @Override
    public String toString() {
        return asString();
    }

    /**
     * Gets the {@link UserMessage} computed from the combination of the original {@link UserMessage} in the {@link InputGuardrailRequest}
     * and this result
     * @param params The input guardrail params
     * @return A {@link UserMessage} computed from the combination of the original {@link UserMessage} in the {@link InputGuardrailRequest}
     *      * and this result
     */
    public UserMessage userMessage(InputGuardrailRequest params) {
        return hasRewrittenResult() ? params.rewriteUserMessage(successfulText()) : params.userMessage();
    }

    /**
     * Represents an input guardrail failure
     *
     * @param message
     *            The failure message
     * @param cause
     *            The cause of the failure
     * @param guardrailClass
     *            The class that produced the failure
     */
    public record Failure(
            String message, @Nullable Throwable cause, @Nullable Class<? extends Guardrail> guardrailClass)
            implements GuardrailResult.Failure {

        public Failure {
            ensureNotNull(message, "message");
        }

        public Failure(String message) {
            this(message, null);
        }

        public Failure(String message, @Nullable Throwable cause) {
            this(message, cause, null);
        }

        /**
         * Adds a guardrail class name to a failure
         *
         * @param guardrailClass
         *            The guardrail class
         */
        public Failure withGuardrailClass(Class<? extends Guardrail> guardrailClass) {
            ensureNotNull(guardrailClass, "guardrailClass");
            return new Failure(this.message, this.cause, guardrailClass);
        }

        @Override
        public String toString() {
            return asString();
        }
    }
}
