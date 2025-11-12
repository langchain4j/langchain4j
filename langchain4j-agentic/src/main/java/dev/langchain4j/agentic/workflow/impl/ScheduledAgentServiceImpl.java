package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.workflow.ScheduledAgentService;
import dev.langchain4j.agentic.workflow.SchedulerService;
import java.lang.reflect.Method;

public class ScheduledAgentServiceImpl<T> extends AbstractServiceBuilder<T, ScheduledAgentService<T>>
        implements ScheduledAgentService<T> {
    private String cronExpression;
    private int maxIterations = Integer.MAX_VALUE;
    private SchedulerService schedulerService = new SchedulerServiceImpl();

    private ScheduledAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    @Override
    public T build() {
        return build(() -> new ScheduledPlanner(cronExpression, maxIterations, schedulerService));
    }

    @Override
    public ScheduledAgentService<T> maxIterations(final int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    @Override
    public ScheduledAgentService<T> cron(final String cronExpression) {
        this.cronExpression = cronExpression;
        return this;
    }

    @Override
    public ScheduledAgentService<T> scheduledService(final SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
        return this;
    }

    public static ScheduledAgentService<UntypedAgent> builder() {
        return new ScheduledAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> ScheduledAgentService<T> builder(Class<T> agentServiceClass) {
        return new ScheduledAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public String serviceType() {
        return "Scheduled";
    }
}
