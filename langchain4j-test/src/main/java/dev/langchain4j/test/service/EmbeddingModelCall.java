package dev.langchain4j.test.service;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;

/**
 * A call to a embedding model made during an AI Service invocation (see {@link ModelCall}).
 */
@Experimental
public final class EmbeddingModelCall extends ModelCall {

    private final EmbeddingRequest request;
    private final EmbeddingResponse response;

    EmbeddingModelCall(ModelProvider modelProvider, EmbeddingRequest request, EmbeddingResponse response, Throwable error) {
        super(modelProvider, error);
        this.request = request;
        this.response = response;
    }

    /**
     * Returns the request sent to the embedding model.
     */
    @Override
    public EmbeddingRequest request() {
        return request;
    }

    /**
     * Returns the response of the embedding model, or {@code null} if the call failed or had not ended when the invocation
     * finished.
     */
    @Override
    public EmbeddingResponse response() {
        return response;
    }
}
