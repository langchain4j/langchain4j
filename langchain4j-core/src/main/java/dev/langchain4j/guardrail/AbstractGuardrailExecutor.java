package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.guardrail.GuardrailResult.Failure;
import dev.langchain4j.guardrail.config.GuardrailsConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Abstract base class for {@link GuardrailExecutor}s.
 * @param <C>
 *            The type of {@link GuardrailsConfig} to use for configuration
 * @param <P>
 *            The type of {@link GuardrailParams} to validate
 * @param <R>
 *            The type of {@link GuardrailResult} to return
 * @param <G>
 *            The type of {@link Guardrail}s being executed
 * @param <F>
 *            The type of {@link Failure} to return
 */
public abstract class AbstractGuardrailExecutor<
                C extends GuardrailsConfig,
                P extends GuardrailParams<P>,
                R extends GuardrailResult<R>,
                G extends Guardrail<P, R>,
                F extends Failure>
        implements GuardrailExecutor<C, P, R, G> {

    private final C config;
    private final List<G> guardrails;

    protected AbstractGuardrailExecutor(C config, @Nullable List<G> guardrails) {
        ensureNotNull(config, "config");
        this.config = config;
        this.guardrails = Optional.ofNullable(guardrails).orElseGet(List::of);
    }

    protected AbstractGuardrailExecutor(C config, G... guardrails) {
        this(config, List.of(guardrails));
    }

    /**
     * Creates a failure result from some {@link Failure}s.
     * @param failures The failures
     * @return A {@link GuardrailResult} containing the failures
     */
    protected abstract R createFailure(List<@NonNull F> failures);

    /**
     * Creates a success result.
     * @return A {@link GuardrailResult} representing success
     */
    protected abstract R createSuccess();

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
     * @param params The {@link GuardrailParams} to validate
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
            throw new GuardrailException(e.getMessage(), e);
        }
    }

    /**
     * Handles a fatal result.
     * @param result The fatal result
     * @return The fatal result, possibly wrapped/modified in some way
     */
    protected R handleFatalResult(R result) {
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
                    return handleFatalResult(result);
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
}
