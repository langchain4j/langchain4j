package dev.langchain4j.model.structureddecision;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.AsyncNotSupported;
import java.util.concurrent.CompletableFuture;

/** A model that evaluates a state against a batch of typed, named questions. */
@Experimental
public interface StructuredDecisionModel {

    StructuredDecisionResponse decide(StructuredDecisionRequest request);

    default CompletableFuture<StructuredDecisionResponse> decideAsync(StructuredDecisionRequest request) {
        return AsyncNotSupported.failedFuture(getClass(), "decideAsync");
    }

    default StructuredDecisionRequestParameters defaultRequestParameters() {
        return StructuredDecisionRequestParameters.EMPTY;
    }
}
