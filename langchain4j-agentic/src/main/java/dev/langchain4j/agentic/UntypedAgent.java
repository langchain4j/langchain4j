package dev.langchain4j.agentic;

import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;

import java.util.Map;

public interface UntypedAgent extends AgenticScopeAccess {
    @Agent
    Object invoke(@V("input") Map<String, Object> input);

    ResultWithAgenticScope<String> invokeWithAgenticScope(Map<String, Object> input);
}
