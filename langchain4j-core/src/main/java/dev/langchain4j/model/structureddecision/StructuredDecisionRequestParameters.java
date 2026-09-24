package dev.langchain4j.model.structureddecision;

import dev.langchain4j.Experimental;

/** Per-call parameters for a {@link StructuredDecisionRequest}. */
@Experimental
public interface StructuredDecisionRequestParameters {

    /** Empty immutable parameters used when a request does not specify overrides. */
    StructuredDecisionRequestParameters EMPTY =
            DefaultStructuredDecisionRequestParameters.builder().build();

    String modelName();

    /**
     * Returns new parameters combining this instance with {@code that}. Values from {@code that} take precedence;
     * neither instance is modified.
     */
    StructuredDecisionRequestParameters overrideWith(StructuredDecisionRequestParameters that);

    static DefaultStructuredDecisionRequestParameters.Builder<?> builder() {
        return new DefaultStructuredDecisionRequestParameters.Builder<>();
    }
}
