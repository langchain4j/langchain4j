package dev.langchain4j.agentic.workflow;

public interface SchedulerService {
    void start();

    void stop();

    void schedule(String cronExpression, int maxIterations, Runnable task);
}
