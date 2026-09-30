package dev.langchain4j.model.scoring.request;

import dev.langchain4j.Experimental;

/**
 * The per-call parameters of a {@link ScoringRequest}, such as the {@link #modelName()} to use. Provider
 * integrations can extend this interface (via {@link DefaultScoringRequestParameters} and its self-typed builder)
 * to add provider-specific parameters.
 *
 * @see DefaultScoringRequestParameters
 * @since 1.20.0
 */
@Experimental
public interface ScoringRequestParameters {

    /**
     * Empty parameters — nothing is set.
     */
    ScoringRequestParameters EMPTY = DefaultScoringRequestParameters.builder().build();

    /**
     * The name of the model to use for this request, or {@code null} if not set (the model then uses its own
     * configured default).
     */
    String modelName();

    /**
     * Creates a new {@link ScoringRequestParameters} by combining these parameters with the specified ones. Values
     * from {@code that} override these when both set the same parameter. Neither instance is modified.
     *
     * @param that the parameters whose values will override these ones, may be {@code null}.
     * @return a new combined {@link ScoringRequestParameters}.
     */
    ScoringRequestParameters overrideWith(ScoringRequestParameters that);

    static DefaultScoringRequestParameters.Builder<?> builder() {
        return new DefaultScoringRequestParameters.Builder<>();
    }
}
