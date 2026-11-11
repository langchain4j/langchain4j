package dev.langchain4j.test.service;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;

/**
 * A call to a scoring model made during an AI Service invocation (see {@link ModelCall}).
 */
@Experimental
public final class ScoringModelCall extends ModelCall {

    private final ScoringRequest request;
    private final ScoringResponse response;

    ScoringModelCall(ModelProvider modelProvider, ScoringRequest request, ScoringResponse response, Throwable error) {
        super(modelProvider, error);
        this.request = request;
        this.response = response;
    }

    /**
     * Returns the request sent to the scoring model.
     */
    @Override
    public ScoringRequest request() {
        return request;
    }

    /**
     * Returns the response of the scoring model, or {@code null} if the call failed or had not ended when the invocation
     * finished.
     */
    @Override
    public ScoringResponse response() {
        return response;
    }
}
