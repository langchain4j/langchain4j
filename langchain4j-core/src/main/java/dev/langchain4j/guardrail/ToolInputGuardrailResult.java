package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of the validation of a {@link ToolInputGuardrail}.
 * <p>
 *     A successful result may carry rewritten tool arguments (a JSON string), which replace the arguments of the
 *     tool execution request for the guardrails that follow and for the tool execution itself.
 * </p>
 *
 * @since 1.24.0
 */
@Experimental
public final class ToolInputGuardrailResult implements GuardrailResult<ToolInputGuardrailResult> {
    private static final ToolInputGuardrailResult SUCCESS = new ToolInputGuardrailResult();

    private final Result result;
    private final String successfulText;
    private final List<Failure> failures;

    private ToolInputGuardrailResult(Result result, String successfulText, List<Failure> failures) {
        this.result = ensureNotNull(result, "result");
        this.successfulText = successfulText;
        this.failures = Optional.ofNullable(failures).orElseGet(List::of);
    }

    private ToolInputGuardrailResult() {
        this(Result.SUCCESS, null, Collections.emptyList());
    }

    ToolInputGuardrailResult(List<Failure> failures, boolean fatal) {
        this(fatal ? Result.FATAL : Result.FAILURE, null, failures);
    }

    ToolInputGuardrailResult(Failure failure, boolean fatal) {
        this(new ArrayList<>(List.of(failure)), fatal);
    }

    private ToolInputGuardrailResult(String rewrittenArguments) {
        this(Result.SUCCESS_WITH_RESULT, rewrittenArguments, Collections.emptyList());
    }

    /**
     * Gets a successful tool input guardrail result.
     *
     * @return a successful result without any rewrite
     */
    public static ToolInputGuardrailResult success() {
        return SUCCESS;
    }

    /**
     * Produces a successful result with rewritten tool arguments.
     *
     * @param rewrittenArguments
     *            the new arguments of the tool, as a JSON string. When {@code null}, the result is a plain
     *            {@linkplain #success() success}.
     * @return a successful result carrying the rewritten arguments
     */
    public static ToolInputGuardrailResult successWith(String rewrittenArguments) {
        return (rewrittenArguments == null) ? success() : new ToolInputGuardrailResult(rewrittenArguments);
    }

    @Override
    public Result result() {
        return result;
    }

    /**
     * @return the rewritten tool arguments (a JSON string), or {@code null} if the arguments were not rewritten
     */
    @Override
    public String successfulText() {
        return successfulText;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <F extends GuardrailResult.Failure> List<F> failures() {
        return (List<F>) failures;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return asString();
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolInputGuardrailResult that = (ToolInputGuardrailResult) o;
        return result == that.result
                && Objects.equals(successfulText, that.successfulText)
                && Objects.equals(failures, that.failures);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(result, successfulText, failures);
    }

    /**
     * Represents a tool input guardrail failure.
     * Its message is sent to the LLM verbatim as the content of an error tool result, unless the failure is fatal.
     *
     * @since 1.24.0
     */
    @Experimental
    public static final class Failure implements GuardrailResult.Failure {
        private final String message;
        private final Throwable cause;
        private final Class<? extends Guardrail> guardrailClass;

        Failure(String message, Throwable cause, Class<? extends Guardrail> guardrailClass) {
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
        public Failure withGuardrailClass(Class<? extends Guardrail> guardrailClass) {
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
        public Class<? extends Guardrail> guardrailClass() {
            return guardrailClass;
        }

        @Override
        @JacocoIgnoreCoverageGenerated
        public String toString() {
            return asString();
        }
    }
}
