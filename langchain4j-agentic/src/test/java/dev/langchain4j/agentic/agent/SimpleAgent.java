package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SimpleAgent {

    @Agent(value = "Simple agent for bug reproduction", outputKey = "result")
    @UserMessage("You are a helper assistant. {{input}}")
    String execute(@V("input") String input);
}
