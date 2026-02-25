package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.planner.AgenticService;
import java.util.concurrent.Executor;

public interface ParallelMultiInstanceAgentService<T> extends AgenticService<ParallelMultiInstanceAgentService<T>, T> {

    ParallelMultiInstanceAgentService<T> executor(Executor executor);

    ParallelMultiInstanceAgentService<T> itemsProvider(String itemsProvider);
}
