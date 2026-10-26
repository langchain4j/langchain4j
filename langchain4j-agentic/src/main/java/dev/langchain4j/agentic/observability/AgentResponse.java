package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.Map;

/**
 * The outcome of an agent invocation, reported to {@link AgentListener#afterAgentInvocation(AgentResponse)}.
 *
 * @param chatRequest the last request sent to the chat model by an AI agent, or {@code null} for the other kinds
 *                    of agents
 * @param chatResponse the last response of the chat model of an AI agent, or {@code null} for the other kinds of
 *                     agents
 * @param decisionResponse the response of the decision model of a decision agent, with its answers and their
 *                         probabilities, or {@code null} for the other kinds of agents
 */
public record AgentResponse(
        AgenticScope agenticScope,
        AgentInstance agent,
        Map<String, Object> inputs,
        Object output,
        ChatRequest chatRequest,
        ChatResponse chatResponse,
        DecisionResponse decisionResponse) {

    AgentResponse(AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs) {
        this(agenticScope, agent, inputs, null, null, null, null);
    }

    AgentResponse(AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs, Object output) {
        this(agenticScope, agent, inputs, output, null, null, null);
    }

    public AgentResponse(
            AgenticScope agenticScope,
            AgentInstance agent,
            Map<String, Object> inputs,
            Object output,
            ChatRequest chatRequest,
            ChatResponse chatResponse) {
        this(agenticScope, agent, inputs, output, chatRequest, chatResponse, null);
    }

    AgentResponse(
            AgenticScope agenticScope,
            AgentInstance agent,
            Map<String, Object> inputs,
            Object output,
            DecisionResponse decisionResponse) {
        this(agenticScope, agent, inputs, output, null, null, decisionResponse);
    }

    public String agentName() {
        return agent.name();
    }

    public String agentId() {
        return agent.agentId();
    }
}
