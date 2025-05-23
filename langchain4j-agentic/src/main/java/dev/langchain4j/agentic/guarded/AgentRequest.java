package dev.langchain4j.agentic.guarded;

import dev.langchain4j.agentic.ChatState;

import java.util.function.Function;

public record AgentRequest(String agentName, ChatState chatState) {

    static final Function<AgentRequest, AgentDirective> DEFAULT_ACTION = request -> AgentDirective.prompt();

}
