package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.buildAgentFeatures;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureOutput;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.parallelExecutor;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.internal.AgentHelpers;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import java.lang.reflect.Method;
import java.util.List;

public class ParallelAgentServiceImpl<T> extends AbstractServiceBuilder<T, ParallelAgentService<T>>
        implements ParallelAgentService<T> {

    public ParallelAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
        configureParallel(AgentHelpers.staticOnly(agentServiceClass));
    }

    @Override
    protected void applyHelperObjects(List<Object> helperObjects) {
        configureParallel(AgentHelpers.instanceOnly(agentServiceClass, helperObjects));
    }

    @Override
    public T build() {
        return build(ParallelPlanner::new);
    }

    public static ParallelAgentServiceImpl<UntypedAgent> builder() {
        return new ParallelAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> ParallelAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ParallelAgentServiceImpl<>(
                agentServiceClass,
                validateAgentClass(agentServiceClass, false, dev.langchain4j.agentic.declarative.ParallelAgent.class));
    }

    @Override
    public String serviceType() {
        return "Parallel";
    }

    private void configureParallel(AgentHelpers helpers) {
        configureOutput(helpers, this);
        buildAgentFeatures(helpers, this);

        parallelExecutor(helpers).ifPresent(this::executor);
    }
}
