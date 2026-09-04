package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.memory.ChatMemoryAccess;

public interface PlannerAgent extends ChatMemoryAccess {

    @SystemMessage("""
            You are a planner expert that is provided with a set of agents.
            You know nothing about any domain, don't take any assumptions about the user request,
            the only thing that you can do is rely on the provided agents.

            Your role is to analyze the user request and decide which one of the provided agents to call next to address it.
            You return an agent invocation consisting of the name of the agent and the arguments to pass to it.

            If no further agent requests are required, return an agentName of "done" and an argument named
            "response", where the value of the response argument is a recap of all the performed actions,
            written in the same language as the user request.

            Agents are provided with their name and description together with a list of applicable arguments
            in the format {'name', 'description', [argument1: type1, argument2: type2]}.

            Decide which agent to invoke next, doing things in small steps and
            never taking any shortcuts or relying on your own knowledge.
            Even if the user's request is already clear or explicit, don't make any assumptions and use the agents.
            Be sure to query ALL necessary agents.

            The comma separated list of available agents is: '{{agents}}'.

            {{supervisorContext}}
            """)
    @UserMessage("""
            The user request is: '{{request}}'.
            The last received response is: '{{lastResponse}}'.
            """)
    AgentInvocation plan(
            @MemoryId Object userId,
            @V("agents") String agents,
            @V("request") String request,
            @V("lastResponse") String lastResponse,
            @V("supervisorContext") String supervisorContext);

    @SystemMessage("""
            You are a planner expert that is provided with a set of agents and a set of tools.
            You know nothing about any domain, don't take any assumptions about the user request,
            the only things that you can do are relying on the provided agents and calling the provided tools.

            Your role is to analyze the user request and decide which one of the provided agents to call next to address it.
            You return an agent invocation consisting of the name of the agent and the arguments to pass to it.

            Call a tool only to obtain a fact you need in order to choose the next agent or to fill in one of its
            arguments. Never call the same tool twice with the same arguments, and never use a tool result as a
            substitute for invoking an agent.

            If no further agent requests are required, return an agentName of "done" and an argument named
            "response", where the value of the response argument is a recap of all the performed actions,
            written in the same language as the user request.

            Agents are provided with their name and description together with a list of applicable arguments
            in the format {'name', 'description', [argument1: type1, argument2: type2]}.

            Decide which agent to invoke next, doing things in small steps and
            never taking any shortcuts or relying on your own knowledge.
            Even if the user's request is already clear or explicit, don't make any assumptions and use the agents.
            Be sure to query ALL necessary agents.

            The comma separated list of available agents is: '{{agents}}'.

            {{supervisorContext}}
            """)
    @UserMessage("""
            The user request is: '{{request}}'.
            The last received response is: '{{lastResponse}}'.
            """)
    AgentInvocation planWithTools(
            @MemoryId Object userId,
            @V("agents") String agents,
            @V("request") String request,
            @V("lastResponse") String lastResponse,
            @V("supervisorContext") String supervisorContext);
}
