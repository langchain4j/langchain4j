package dev.langchain4j.agentic.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * The {@link PlanningContext} of the planner calling a model on the current thread. A planner puts it in the
 * {@link LangChain4jManaged} parameters around a model call that it cannot report itself, because the call is made
 * by a component with no access to the planning context, like the summarizer of the supervisor context, which runs
 * inside the request transformer of an AI service shared by all the invocations on the same scope.
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
}
