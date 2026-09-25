package dev.langchain4j.model.decision;

import dev.langchain4j.Experimental;

/** Whether a confidence value came from the provider or was derived by an adapter. */
@Experimental
public enum ConfidenceProvenance {
    PROVIDER_REPORTED,
    ADAPTER_DERIVED
}
