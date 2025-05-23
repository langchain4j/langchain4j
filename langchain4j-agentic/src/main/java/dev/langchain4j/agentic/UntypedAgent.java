package dev.langchain4j.agentic;

import java.util.Map;

public interface UntypedAgent {
    @Agent
    Object invoke(Map<String, Object> input);
}
