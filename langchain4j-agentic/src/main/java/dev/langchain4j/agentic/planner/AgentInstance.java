package dev.langchain4j.agentic.planner;

public interface AgentInstance {
    String uniqueName();
    String[] argumentNames();
    String outputKey();
    String toCard();
}
