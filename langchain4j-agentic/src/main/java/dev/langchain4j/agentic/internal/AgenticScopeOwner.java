package dev.langchain4j.agentic.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;

@Internal
public interface AgenticScopeOwner {
    AgenticScopeOwner withAgenticScope(DefaultAgenticScope agenticScope);
    AgenticScopeRegistry registry();
}
