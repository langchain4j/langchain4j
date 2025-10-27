package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.ScheduledAgentService;
import dev.langchain4j.agentic.workflow.SchedulerService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ScheduledAgentServiceImpl<T> extends AbstractService<T, ScheduledAgentService<T>>
        implements ScheduledAgentService<T> {
    private int maxIterations = Integer.MAX_VALUE;
    private String cronExpression;
    private SchedulerService schedulerService;

    private ScheduledAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    @Override
    public T build() {
        initSchedulerService();
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class},
                new ScheduledInvocationHandler());
    }

    private void initSchedulerService() {
        if (schedulerService == null) {
            schedulerService = new SchedulerServiceImpl();
        }
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

    private class ScheduledInvocationHandler extends AbstractAgentInvocationHandler {

        private ScheduledInvocationHandler() {
            super(ScheduledAgentServiceImpl.this);
        }

        private ScheduledInvocationHandler(DefaultAgenticScope agenticScope) {
            super(ScheduledAgentServiceImpl.this, agenticScope);
        }

        @Override
        protected Object doAgentAction(DefaultAgenticScope agenticScope) {
            schedulerService.start();
            schedulerService.schedule(cronExpression, maxIterations, () -> {
                agentExecutors().forEach(agentExecutor -> agentExecutor.execute(agenticScope));
            });
            return result(agenticScope, output.apply(agenticScope));
        }

        @Override
        protected InvocationHandler createSubAgentWithAgenticScope(DefaultAgenticScope agenticScope) {
            return new ScheduledInvocationHandler(agenticScope);
        }
    }

    public static ScheduledAgentService<UntypedAgent> builder() {
        return new ScheduledAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> ScheduledAgentService<T> builder(Class<T> agentServiceClass) {
        return new ScheduledAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }
}
