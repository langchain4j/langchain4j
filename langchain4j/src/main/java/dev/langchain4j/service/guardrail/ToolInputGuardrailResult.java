package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a {@link ToolInputGuardrail}.
 *
 * @since 1.19.0
 */
@Experimental
public final class ToolInputGuardrailResult implements ToolGuardrailResult<ToolInputGuardrailResult> {

    private static final ToolInputGuardrailResult SUCCESS = new ToolInputGuardrailResult();

    private final Result result;
    private final ToolExecutionRequest rewrittenRequest;
    private final List<Failure> failures;

    private ToolInputGuardrailResult(Result result, ToolExecutionRequest rewrittenRequest, List<Failure> failures) {
        this.result = ensureNotNull(result, "result");
        this.rewrittenRequest = rewrittenRequest;
        this.failures = Optional.ofNullable(failures).orElseGet(List::of);
    }

    private ToolInputGuardrailResult() {
        this(Result.SUCCESS, null, Collections.emptyList());
    }

    ToolInputGuardrailResult(Failure failure, boolean fatal) {
        this(fatal ? Result.FATAL : Result.FAILURE, null, new ArrayList<>(List.of(failure)));
    }

    /**
     * The tool call is allowed to proceed unchanged.
     */
    public static ToolInputGuardrailResult success() {
        return SUCCESS;
    }

    /**
     * The tool call is allowed to proceed, with the given request replacing the original one.
     */
    public static ToolInputGuardrailResult successWith(ToolExecutionRequest rewrittenRequest) {
        return rewrittenRequest == null
                ? success()
                : new ToolInputGuardrailResult(Result.SUCCESS_WITH_RESULT, rewrittenRequest, Collections.emptyList());
    }

    /**
     * The request the tool should be invoked with: the rewritten one when this result carries one,
     * otherwise the original.
     */
    public ToolExecutionRequest executionRequest(ToolInputGuardrailRequest request) {
        return hasRewrittenResult() ? rewrittenRequest : request.executionRequest();
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
        ToolInputGuardrailResult that = (ToolInputGuardrailResult) o;
        return result == that.result
                && Objects.equals(rewrittenRequest, that.rewrittenRequest)
                && Objects.equals(failures, that.failures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, rewrittenRequest, failures);
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
