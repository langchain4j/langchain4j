package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a {@link ToolOutputGuardrail}.
 *
 * @since 1.19.0
 */
@Experimental
public final class ToolOutputGuardrailResult implements ToolGuardrailResult<ToolOutputGuardrailResult> {

    private static final ToolOutputGuardrailResult SUCCESS = new ToolOutputGuardrailResult();

    private final Result result;
    private final ToolExecutionResult rewrittenResult;
    private final List<Failure> failures;

    private ToolOutputGuardrailResult(Result result, ToolExecutionResult rewrittenResult, List<Failure> failures) {
        this.result = ensureNotNull(result, "result");
        this.rewrittenResult = rewrittenResult;
        this.failures = Optional.ofNullable(failures).orElseGet(List::of);
    }

    private ToolOutputGuardrailResult() {
        this(Result.SUCCESS, null, Collections.emptyList());
    }

    ToolOutputGuardrailResult(Failure failure, boolean fatal) {
        this(fatal ? Result.FATAL : Result.FAILURE, null, new ArrayList<>(List.of(failure)));
    }

    /**
     * The tool result is handed back to the LLM unchanged.
     */
    public static ToolOutputGuardrailResult success() {
        return SUCCESS;
    }

    /**
     * The given result replaces the one the tool produced.
     */
    public static ToolOutputGuardrailResult successWith(ToolExecutionResult rewrittenResult) {
        return rewrittenResult == null
                ? success()
                : new ToolOutputGuardrailResult(Result.SUCCESS_WITH_RESULT, rewrittenResult, Collections.emptyList());
    }

    /**
     * The result to hand back to the LLM: the rewritten one when this result carries one, otherwise the
     * one the tool produced.
     */
    public ToolExecutionResult executionResult(ToolOutputGuardrailRequest request) {
        return hasRewrittenResult() ? rewrittenResult : request.executionResult();
    }

    @Override
    public Result result() {
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <F extends ToolGuardrailResult.Failure> List<F> failures() {
        return (List<F>) failures;
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolOutputGuardrailResult that = (ToolOutputGuardrailResult) o;
        return result == that.result
                && Objects.equals(rewrittenResult, that.rewrittenResult)
                && Objects.equals(failures, that.failures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, rewrittenResult, failures);
    }

    public static final class Failure implements ToolGuardrailResult.Failure {

        private final String message;
        private final Throwable cause;
        private final Class<? extends ToolGuardrail> guardrailClass;

        Failure(String message, Throwable cause, Class<? extends ToolGuardrail> guardrailClass) {
            this.message = ensureNotNull(message, "message");
            this.cause = cause;
            this.guardrailClass = guardrailClass;
        }

        Failure(String message) {
            this(message, null, null);
        }

        Failure(String message, Throwable cause) {
            this(message, cause, null);
        }

        @Override
        public Failure withGuardrailClass(Class<? extends ToolGuardrail> guardrailClass) {
            ensureNotNull(guardrailClass, "guardrailClass");
            return new Failure(this.message, this.cause, guardrailClass);
        }

        @Override
        public String message() {
            return message;
        }

        @Override
        public Throwable cause() {
            return cause;
        }

        @Override
        public Class<? extends ToolGuardrail> guardrailClass() {
            return guardrailClass;
        }

        @Override
        public String toString() {
            return asString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Failure failure = (Failure) o;
            return Objects.equals(message, failure.message)
                    && Objects.equals(cause, failure.cause)
                    && Objects.equals(guardrailClass, failure.guardrailClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(message, cause, guardrailClass);
        }
    }
}
