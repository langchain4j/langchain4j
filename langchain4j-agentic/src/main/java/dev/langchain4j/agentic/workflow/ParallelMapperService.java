package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.planner.AgenticService;
import java.util.concurrent.Executor;

public interface ParallelMapperService<T> extends AgenticService<ParallelMapperService<T>, T> {

    ParallelMapperService<T> executor(Executor executor);

    ParallelMapperService<T> itemsProvider(String itemsProvider);
}
