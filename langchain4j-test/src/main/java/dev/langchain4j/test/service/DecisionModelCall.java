package dev.langchain4j.test.service;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;

/**
 * A call to a decision model made during an AI Service invocation (see {@link ModelCall}).
 */
@Experimental
public final class DecisionModelCall extends ModelCall {

    private final DecisionRequest request;
    private final DecisionResponse response;

    DecisionModelCall(ModelProvider modelProvider, DecisionRequest request, DecisionResponse response, Throwable error) {
        super(modelProvider, error);
        this.request = request;
        this.response = response;
    }

    /**
     * Returns the request sent to the decision model.
     */
    @Override
    public DecisionRequest request() {
        return request;
    }

    /**
     * Returns the response of the decision model, or {@code null} if the call failed or had not ended when the invocation
     * finished.
     */
    @Override
    public DecisionResponse response() {
        return response;
    }
}
