package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Map;

public record AgentResponse(AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs, Object output,
                            ChatRequest chatRequest, ChatResponse chatResponse) {

    AgentResponse(AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs, Object output) {
        this(agenticScope, agent, inputs, output, null, null);
    }

    public String agentName() {
        return agent.name();
    }

    public String agentId() {
        return agent.agentId();
    }
}
