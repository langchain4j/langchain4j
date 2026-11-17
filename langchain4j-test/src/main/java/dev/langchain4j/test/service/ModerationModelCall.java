package dev.langchain4j.test.service;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;

/**
 * A call to a moderation model made during an AI Service invocation (see {@link ModelCall}).
 */
@Experimental
public final class ModerationModelCall extends ModelCall {

    private final ModerationRequest request;
    private final ModerationResponse response;

    ModerationModelCall(ModelProvider modelProvider, ModerationRequest request, ModerationResponse response, Throwable error) {
        super(modelProvider, error);
        this.request = request;
        this.response = response;
    }

    /**
     * Returns the request sent to the moderation model.
     */
    @Override
    public ModerationRequest request() {
        return request;
    }

    /**
     * Returns the response of the moderation model, or {@code null} if the call failed or had not ended when the invocation
     * finished.
     */
    @Override
    public ModerationResponse response() {
        return response;
    }
}
