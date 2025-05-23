package dev.langchain4j.agentic.guarded;

import dev.langchain4j.agentic.ChatState;

import java.util.function.Function;

public record AgentResponse(String agentName, ChatState chatState, Object response) {

    static final Function<AgentResponse, AgentDirective> DEFAULT_ACTION = response -> AgentDirective.terminate();
}
