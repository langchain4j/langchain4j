package dev.langchain4j.agentic.patterns.p2p;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;

public interface P2PAgent extends AgenticScopeAccess {

    String P2P_REQUEST_KEY = "p2pRequest";

    @Agent
    String invoke(@V(P2P_REQUEST_KEY) String p2pRequest);

    ResultWithAgenticScope<String> invokeWithAgenticScope(@V(P2P_REQUEST_KEY) String p2pRequest);
}
