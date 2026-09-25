package dev.langchain4j.model.decision;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.AsyncNotSupported;
import java.util.concurrent.CompletableFuture;

/** A model that evaluates a state against a batch of typed, named questions. */
@Experimental
public interface DecisionModel {

    DecisionResponse decide(DecisionRequest request);

    default CompletableFuture<DecisionResponse> decideAsync(DecisionRequest request) {
        return AsyncNotSupported.failedFuture(getClass(), "decideAsync");
    }

    default DecisionRequestParameters defaultRequestParameters() {
        return DecisionRequestParameters.EMPTY;
    }
}
