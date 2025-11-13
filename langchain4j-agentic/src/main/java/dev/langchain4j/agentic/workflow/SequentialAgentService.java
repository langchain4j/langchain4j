package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.planner.AgenticService;

public interface SequentialAgentService<T> extends AgenticService<SequentialAgentService<T>, T> {}
