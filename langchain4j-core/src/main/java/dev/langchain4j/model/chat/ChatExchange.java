package dev.langchain4j.model.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Objects;

/**
 * A request sent to a chat model and the response it returned. When a response is the result of a tool-calling loop,
 * the request is the last one sent to the model: its messages include the tool calls and tool results of all rounds.
 * The request is {@code null} when the code that produced the response does not know it.
 *
 * @since 1.23.0
 */
@Experimental
public final class ChatExchange {

    private final ChatRequest request;
    private final ChatResponse response;

    /**
     * @param request  the request sent to the model, or {@code null} if it is not known
     * @param response the response of the model
     */
    public ChatExchange(ChatRequest request, ChatResponse response) {
        this.request = request;
        this.response = ensureNotNull(response, "response");
    }

    /**
     * @return the request sent to the model, or {@code null} if it is not known
     */
    public ChatRequest request() {
        return request;
    }

    /**
     * @return the response of the model
     */
    public ChatResponse response() {
        return response;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChatExchange that && Objects.equals(request, that.request) && response.equals(that.response);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, response);
    }

    @Override
    public String toString() {
        return "ChatExchange{request=" + request + ", response=" + response + '}';
    }
}
