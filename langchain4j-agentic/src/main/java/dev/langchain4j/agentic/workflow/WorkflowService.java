package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.planner.AgenticService;

public interface WorkflowService<T, W> extends AgenticService<T> {

    W build();
}
