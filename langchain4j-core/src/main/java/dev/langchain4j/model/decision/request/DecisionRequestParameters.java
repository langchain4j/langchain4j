package dev.langchain4j.model.decision.request;

import dev.langchain4j.Experimental;

/**
 * The per-call parameters of a {@link DecisionRequest}, such as the {@link #modelName()} to use. Provider
 * integrations can extend this interface (via {@link DefaultDecisionRequestParameters} and its self-typed builder)
 * to add provider-specific parameters.
 *
 * @see DefaultDecisionRequestParameters
 * @since 1.21.0
 */
@Experimental
public interface DecisionRequestParameters {

    /**
     * Empty parameters: nothing is set.
     */
    DecisionRequestParameters EMPTY = DefaultDecisionRequestParameters.builder().build();

    /**
     * The name of the model to use for this request, or {@code null} if not set (the model then uses its own
     * configured default).
     */
    String modelName();

    /**
     * Creates new parameters by combining these parameters with the specified ones. Values from {@code that}
     * override these when both set the same parameter. Neither instance is modified.
     *
     * @param that the parameters whose values will override these ones, may be {@code null}.
     * @return new combined parameters.
     */
    DecisionRequestParameters overrideWith(DecisionRequestParameters that);

    static DefaultDecisionRequestParameters.Builder<?> builder() {
        return new DefaultDecisionRequestParameters.Builder<>();
    }
}
