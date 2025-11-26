package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.planner.AgenticService;
import java.util.concurrent.Executor;

public interface ParallelAgentService<T> extends AgenticService<ParallelAgentService<T>, T> {

    ParallelAgentService<T> executor(Executor executor);
}
