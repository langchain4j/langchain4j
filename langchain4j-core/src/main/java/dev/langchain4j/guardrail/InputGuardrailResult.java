package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import dev.langchain4j.data.message.UserMessage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The result of the validation of an {@link InputGuardrail}
 */
public final class InputGuardrailResult implements GuardrailResult<InputGuardrailResult> {
    private static final InputGuardrailResult SUCCESS = new InputGuardrailResult();

    private final Result result;
    private final @Nullable String successfulText;
    private final List<@NonNull Failure> failures;

    private InputGuardrailResult(Result result, @Nullable String successfulText, @Nullable List<@NonNull Failure> failures) {
        this.result = ensureNotNull(result, "result");
        this.successfulText = successfulText;
        this.failures = Optional.ofNullable(failures).orElseGet(List::of);
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
    public Result result() {
        return result;
    }

    @Override
    public String successfulText() {
        return successfulText;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <F extends GuardrailResult.Failure> List<@NonNull F> failures() {
        return (List<F>) failures;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InputGuardrailResult that = (InputGuardrailResult) o;
        return result == that.result &&
               Objects.equals(successfulText, that.successfulText) &&
               Objects.equals(failures, that.failures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, successfulText, failures);
    }

    /**
     * Represents an input guardrail failure
     */
    public static final class Failure implements GuardrailResult.Failure {
        private final String message;
        private final @Nullable Throwable cause;
        private final @Nullable Class<? extends Guardrail> guardrailClass;

        Failure(String message, @Nullable Throwable cause, @Nullable Class<? extends Guardrail> guardrailClass) {
            this.message = ensureNotNull(message, "message");
            this.cause = cause;
            this.guardrailClass = guardrailClass;
        }

        Failure(String message) {
            this(message, null, null);
        }

        Failure(String message, @Nullable Throwable cause) {
            this(message, cause, null);
        }

        /**
         * Adds a guardrail class name to a failure
         *
         * @param guardrailClass
         *            The guardrail class
         */
        @Override
        public Failure withGuardrailClass(Class<? extends Guardrail> guardrailClass) {
            ensureNotNull(guardrailClass, "guardrailClass");
            return new Failure(this.message, this.cause, guardrailClass);
        }

        @Override
        public String message() {
            return message;
        }

        @Override
        @Nullable
        public Throwable cause() {
            return cause;
        }

        @Override
        @Nullable
        public Class<? extends Guardrail> guardrailClass() {
            return guardrailClass;
        }

        @Override
        public String toString() {
            return asString();
        }
    }
}
