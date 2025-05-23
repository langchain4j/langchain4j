package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

public interface PromptAgent {
    @Agent
    String process(@V("request") String request);
}
