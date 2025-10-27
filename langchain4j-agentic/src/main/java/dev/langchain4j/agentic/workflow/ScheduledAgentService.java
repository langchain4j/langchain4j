package dev.langchain4j.agentic.workflow;

public interface ScheduledAgentService<T> extends WorkflowService<ScheduledAgentService<T>, T> {
    ScheduledAgentService<T> maxIterations(int maxIterations);

    ScheduledAgentService<T> cron(String cronExpression);

    ScheduledAgentService<T> scheduledService(SchedulerService schedulerService);
}
