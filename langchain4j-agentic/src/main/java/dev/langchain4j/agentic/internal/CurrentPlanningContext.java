package dev.langchain4j.agentic.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import java.util.Map;

/**
 * The {@link PlanningContext} of the planner calling a model on the current thread. A planner puts it in the
 * {@link LangChain4jManaged} parameters around a model call that it cannot report itself, because the call is made
 * by a component with no access to the planning context: an AI service shared by all the invocations on the same
 * scope, which reports through its {@link #modelResponseListener() listener}, or the summarizer of the supervisor
 * context, which runs inside the request transformer of that AI service.
 */
@Internal
public record CurrentPlanningContext(PlanningContext planningContext) implements LangChain4jManaged {

    /**
     * Reports the response of a model to the planning context of the current thread, if there is one.
     */
    public static void reportChatModelResponse(ChatResponse chatResponse) {
        CurrentPlanningContext current = LangChain4jManaged.current(CurrentPlanningContext.class);
        if (current != null) {
            current.planningContext.reportChatModelResponse(chatResponse);
        }
    }

    /**
     * Returns the listener that a planner registers on an AI service that it calls with its planning context in the
     * managed parameters. It reports each response that the AI service receives from its model to the planning
     * context found in the invocation context of the event, if there is one, as soon as the response is received:
     * so also a response that the AI service then fails to parse.
     */
    public static AiServiceResponseReceivedListener modelResponseListener() {
        return event -> {
            Map<?, ?> managedParameters = event.invocationContext().managedParameters();
            if (managedParameters != null
                    && managedParameters.get(CurrentPlanningContext.class) instanceof CurrentPlanningContext current) {
                current.planningContext.reportChatModelResponse(event.response());
            }
        };
    }
}
