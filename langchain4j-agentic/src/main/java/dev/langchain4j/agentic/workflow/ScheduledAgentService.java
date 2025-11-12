package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.planner.AgenticService;

public interface ScheduledAgentService<T> extends AgenticService<ScheduledAgentService<T>, T> {
    ScheduledAgentService<T> maxIterations(int maxIterations);

    ScheduledAgentService<T> cron(String cronExpression);

    ScheduledAgentService<T> scheduledService(SchedulerService schedulerService);
}
