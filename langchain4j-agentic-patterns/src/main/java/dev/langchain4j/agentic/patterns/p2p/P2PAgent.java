package dev.langchain4j.agentic.patterns.p2p;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;

public interface P2PAgent extends AgenticScopeAccess {
    @Agent
    String invoke(@V("p2pRequest") String p2pRequest);

    ResultWithAgenticScope<String> invokeWithAgenticScope(@V("p2pRequest") String p2pRequest);
}
