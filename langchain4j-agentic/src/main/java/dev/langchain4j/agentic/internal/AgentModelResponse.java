package dev.langchain4j.agentic.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import java.util.Objects;

/**
 * The response of the model called by one invocation of an AI or decision agent, as reported to the agent's
 * listeners after the invocation. The invoker of the agent creates one instance per invocation and puts it, next to
 * the {@code AgenticScope}, in the {@link LangChain4jManaged} parameters of the invocation, so that the handler
 * of the agent finds it in the invocation context of the AI service events it receives, on whatever thread they
 * arrive, and the invoker reads it once the agent method has returned.
 * <p>
 * The model call of an agent returning a {@code TokenStream} or a future completes on another thread, usually after
 * the invoker has already reported the invocation, so such a response is not reported; the state of this class is
 * guarded by its lock, so that a model call completing while the invoker reads it is never seen half-written.
 */
@Internal
public final class AgentModelResponse implements LangChain4jManaged {

    private Object owner;
    private AiServiceResponseReceivedEvent event;
    private TokenUsage tokenUsage;
    private String modelName;
    private DecisionResponse decisionResponse;

    /**
     * Claims this response for the handler running the agent method of the invocation. An agent invoked directly
     * from inside another agent on the same thread, for instance by one of its tools, finds the response of the
     * enclosing invocation already owned, so its model calls are ignored instead of being summed into it.
     */
    public synchronized void claim(Object handler) {
        if (owner == null) {
            owner = handler;
        }
    }

    public synchronized boolean isOwnedBy(Object handler) {
        return owner == handler;
    }

    /**
     * Records one model call of the invocation: an agent calling tools makes several, and its reported response is
     * the last one with the token usage of all of them, and the model name only when all the calls agree on it.
     */
    public synchronized void onChatResponse(AiServiceResponseReceivedEvent event) {
        ChatResponseMetadata metadata = event.response().metadata();
        if (this.event == null) {
            tokenUsage = metadata.tokenUsage();
            modelName = metadata.modelName();
        } else {
            tokenUsage = TokenUsage.sum(tokenUsage, metadata.tokenUsage());
            modelName = Objects.equals(modelName, metadata.modelName()) ? modelName : null;
        }
        this.event = event;
    }

    public synchronized void onDecisionResponse(DecisionResponse decisionResponse) {
        this.decisionResponse = decisionResponse;
    }

    public synchronized ChatRequest chatRequest() {
        return event == null ? null : event.request();
    }

    public synchronized ChatResponse chatResponse() {
        if (event == null) {
            return null;
        }
        ChatResponse response = event.response();
        return ChatResponse.builder()
                .aiMessage(response.aiMessage())
                .metadata(response.metadata().toBuilder()
                        .tokenUsage(tokenUsage)
                        .modelName(modelName)
                        .build())
                .build();
    }

    public synchronized UserMessage lastUserMessage() {
        return event == null ? null : UserMessage.findLast(event.request().messages()).orElse(null);
    }

    public synchronized DecisionResponse decisionResponse() {
        return decisionResponse;
    }
}
