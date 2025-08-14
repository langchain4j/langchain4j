package dev.langchain4j.agentic.workflow;

import java.util.concurrent.Executor;

public interface ParallelAgentService<T> extends WorkflowService<ParallelAgentService<T>, T> {

    ParallelAgentService<T> executor(Executor executor);
}
