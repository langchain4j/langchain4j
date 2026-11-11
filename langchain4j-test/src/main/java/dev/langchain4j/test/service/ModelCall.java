package dev.langchain4j.test.service;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;

/**
 * A call to a model made during an AI Service invocation: by the AI Service itself to its chat model (see
 * {@link ChatModelCall#isOwn()}), or by the components it runs, for example a query transformer or a judge calling a
 * chat model, an embedding model used for retrieval, or a scoring model that re-ranks the retrieved contents.
 * <p>
 * There is one subclass per type of model, with the request and the response of that type:
 * {@link ChatModelCall}, {@link EmbeddingModelCall}, {@link ScoringModelCall}, {@link ModerationModelCall} and
 * {@link DecisionModelCall}.
 * <p>
 * Only calls whose listeners LangChain4j notifies are captured: the calls that go through the default methods of
 * the model interfaces ({@code chat}, {@code embed}, {@code score}, {@code moderate}, {@code decide}), which most
 * models of LangChain4j do. The calls of a model that bypasses them are not captured, for example a chat model that
 * overrides {@code chat(...)} instead of implementing {@code doChat(...)}, or {@code VertexAiScoringModel}. See
 * {@link AiServiceInvocation#modelCalls()}.
 */
@Experimental
public abstract sealed class ModelCall
        permits ChatModelCall, EmbeddingModelCall, ScoringModelCall, ModerationModelCall, DecisionModelCall {

    private final ModelProvider modelProvider;
    private final Throwable error;

    ModelCall(ModelProvider modelProvider, Throwable error) {
        this.modelProvider = modelProvider;
        this.error = error;
    }

    /**
     * Returns the provider of the model, or {@code null} if the model does not report it.
     */
    public ModelProvider modelProvider() {
        return modelProvider;
    }

    /**
     * Returns the request sent to the model. Each subclass returns the request type of its model, for example
     * {@link ChatModelCall#request()} returns a {@code ChatRequest}.
     */
    public abstract Object request();

    /**
     * Returns the response of the model, or {@code null} if the call failed or had not ended when the invocation
     * finished. Each subclass returns the response type of its model, for example {@link ChatModelCall#response()}
     * returns a {@code ChatResponse}.
     */
    public abstract Object response();

    /**
     * Returns the error the call failed with, or {@code null} if it did not fail.
     */
    public Throwable error() {
        return error;
    }

    /**
     * Returns whether the call failed, that is whether {@link #error()} is not {@code null}. A call that had not ended
     * when the invocation finished has not failed: it has neither a response nor an error.
     */
    public boolean hasFailed() {
        return error != null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{modelProvider=" + modelProvider + ", failed=" + hasFailed() + '}';
    }
}
