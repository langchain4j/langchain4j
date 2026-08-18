package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The outcome of a {@link ToolGuardrail}.
 * <p>
 * Mirrors {@link dev.langchain4j.guardrail.GuardrailResult} so that tool guardrails read the same way as
 * the model-level ones: a guardrail either succeeds, succeeds with a rewritten request or result, fails
 * (the tool call is turned into an error that the LLM sees and can react to), or fails fatally (the
 * invocation is aborted with a {@link ToolGuardrailException}).
 *
 * @param <GR> the concrete result type
 * @since 1.19.0
 */
@Experimental
public sealed interface ToolGuardrailResult<GR extends ToolGuardrailResult<GR>>
        permits ToolInputGuardrailResult, ToolOutputGuardrailResult {

    enum Result {
        SUCCESS,
        SUCCESS_WITH_RESULT,
        FAILURE,
        FATAL
    }

    /**
     * A single guardrail failure.
     */
    sealed interface Failure permits ToolInputGuardrailResult.Failure, ToolOutputGuardrailResult.Failure {

        Failure withGuardrailClass(Class<? extends ToolGuardrail> guardrailClass);

        String message();

        Throwable cause();

        Class<? extends ToolGuardrail> guardrailClass();

        default String asString() {
            var guardrailName =
                    Optional.ofNullable(guardrailClass()).map(Class::getName).orElse("");
            return "The guardrail %s failed with this message: %s".formatted(guardrailName, message());
        }
    }

    Result result();

    <F extends Failure> List<F> failures();

    default boolean hasRewrittenResult() {
        return result() == Result.SUCCESS_WITH_RESULT;
    }

    default boolean isFatal() {
        return result() == Result.FATAL;
    }

    default boolean isSuccess() {
        var result = result();
        return (result == Result.SUCCESS) || (result == Result.SUCCESS_WITH_RESULT);
    }

    default Throwable getFirstFailureException() {
        return isSuccess()
                ? null
                : failures().stream()
                        .map(Failure::cause)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(null);
    }

    /**
     * Stamps the failure with the guardrail class that produced it.
     */
    @SuppressWarnings("unchecked")
    default GR validatedBy(Class<? extends ToolGuardrail> guardrailClass) {
        ensureNotNull(guardrailClass, "guardrailClass");
        if (!isSuccess()) {
            List<Failure> failures = failures();
            if (failures.size() != 1) {
                throw new IllegalArgumentException("Expected exactly one failure, got " + failures.size());
            }
            failures.set(0, failures.get(0).withGuardrailClass(guardrailClass));
        }
        return (GR) this;
    }

    default String asString() {
        if (isSuccess()) {
            return hasRewrittenResult() ? "Success with a rewritten result" : "Success";
        }
        return failures().stream().map(Failure::asString).collect(Collectors.joining(", "));
    }
}
