package dev.langchain4j.test.service;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A call to a chat model made during an AI Service invocation (see {@link ModelCall}). The calls the AI Service makes to its
 * own chat model are marked with {@link #isOwn()}.
 */
@Experimental
public final class ChatModelCall extends ModelCall {

    private final ChatRequest request;
    private final ChatResponse response;
    private final boolean own;

    ChatModelCall(ModelProvider modelProvider, ChatRequest request, ChatResponse response, Throwable error, boolean own) {
        super(modelProvider, error);
        this.request = request;
        this.response = response;
        this.own = own;
    }

    /**
     * Returns the request sent to the chat model.
     */
    @Override
    public ChatRequest request() {
        return request;
    }

    /**
     * Returns the response of the chat model, or {@code null} if the call failed or had not ended when the invocation
     * finished.
     */
    @Override
    public ChatResponse response() {
        return response;
    }

    /**
     * Returns whether the AI Service made this call itself, to its own chat model (see
     * {@link AiServiceInvocation#ownChatModelCalls()}), rather than a component it runs. A call that returned a
     * response is the AI Service's own when the AI Service received that response. A call that failed is the AI
     * Service's own when the AI Service sent a request with the same messages: in the rare case where a component
     * sends exactly the same messages to a chat model and that call fails, it is also reported as the AI Service's own.
     */
    public boolean isOwn() {
        return own;
    }

    @Override
    public String toString() {
        return "ChatModelCall{modelProvider=" + modelProvider() + ", own=" + own + ", failed=" + hasFailed() + '}';
    }
}
