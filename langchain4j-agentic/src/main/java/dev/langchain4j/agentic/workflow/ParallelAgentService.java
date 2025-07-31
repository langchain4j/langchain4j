package dev.langchain4j.agentic.workflow;

import java.util.concurrent.ExecutorService;

public interface ParallelAgentService<T> extends WorkflowService<ParallelAgentService<T>, T> {

    ParallelAgentService<T> executorService(ExecutorService executorService);
}
