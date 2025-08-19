package dev.langchain4j.agentic;

import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;

import java.util.Map;

public interface UntypedAgent extends AgenticScopeAccess {
    @Agent
    Object invoke(Map<String, Object> input);

    ResultWithAgenticScope<String> invokeWithAgenticScope(Map<String, Object> input);
}
