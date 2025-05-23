package dev.langchain4j.agentic.planner;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.memory.ChatMemoryAccess;

public interface PlannerAgent extends ChatMemoryAccess {

    @SystemMessage("""
            You are a planner expert that is provided with a set of agents.
            You know nothing about any domain, don't take any assumptions about the user request,
            the only thing that you can do is relying on the provided agents.
            
            Your role is to analyze the user request and decide which of the provided agent to call
            next to address it. You return an agent invocation containing the name of the agent and the arguments
            to pass to it. Generate the agent invocation also considering the past messages.
            If the last response already contains an appropriate answer, simply return an agent invocation with agent name "done".
            
            For each agent it will be provided both the name and description in the format {name: description}.
            """)
    @UserMessage("""
            Decide which agent to invoke next, doing things in small steps and never taking any shortcuts or relying on your own knowledge.
            Ignore the fact the user's request is already clear or explicit.
            Don't try to answer the user request on any intermediary doubt on your own.
            You know nothing except the provided agents and their descriptions.
            You MUST query all necessary agents.

            The comma separated list of available agents is: '{{agents}}'.
            The user request is: '{{request}}'.
            The last received response is: '{{lastResponse}}'.
            """)
    AgentInvocation plan(@MemoryId String userId, @V("agents") String agents, @V("request") String request, @V("lastResponse") String lastResponse);
}
