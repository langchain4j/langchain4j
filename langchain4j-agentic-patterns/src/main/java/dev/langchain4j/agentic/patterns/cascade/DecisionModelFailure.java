package dev.langchain4j.agentic.patterns.cascade;

import dev.langchain4j.Experimental;

/** Behavior when the decision model fails to grade a cascade tier. */
@Experimental
public enum DecisionModelFailure {
    /** Propagate the failure (the default). */
    THROW,
    /** Return the current tier's answer without a successful grade. */
    ACCEPT,
    /** Reject the current answer and continue to the next tier. */
    REJECT
}
