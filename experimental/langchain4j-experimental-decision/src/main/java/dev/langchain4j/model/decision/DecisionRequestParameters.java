package dev.langchain4j.model.decision;

import dev.langchain4j.Experimental;
import java.util.Map;

/** Per-call parameters for a {@link DecisionRequest}. */
@Experimental
public interface DecisionRequestParameters {

    /** Empty immutable parameters used when a request does not specify overrides. */
    DecisionRequestParameters EMPTY = DefaultDecisionRequestParameters.builder().build();

    String modelName();

    /** Provider-specific request options. Providers may reject unsupported options. */
    Map<String, Object> additionalProperties();

    /**
     * Returns new parameters combining this instance with {@code that}. Values from {@code that} take precedence;
     * neither instance is modified.
     */
    DecisionRequestParameters overrideWith(DecisionRequestParameters that);

    static DefaultDecisionRequestParameters.Builder<?> builder() {
        return new DefaultDecisionRequestParameters.Builder<>();
    }
}
