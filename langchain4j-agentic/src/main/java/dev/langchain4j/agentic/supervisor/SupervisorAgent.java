package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;

public interface SupervisorAgent extends AgenticScopeAccess {
    @Agent
    String invoke(@V("request") String request);

    ResultWithAgenticScope<String> invokeWithAgenticScope(@V("request") String request);
}
