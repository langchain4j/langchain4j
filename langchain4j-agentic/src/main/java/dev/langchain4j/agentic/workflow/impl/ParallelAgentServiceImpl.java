package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import java.lang.reflect.Method;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

public class ParallelAgentServiceImpl<T> extends AbstractServiceBuilder<T, ParallelAgentService<T>> implements ParallelAgentService<T> {

    public ParallelAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    @Override
    public T build() {
        return build(ParallelPlanner::new);
    }

    public static ParallelAgentServiceImpl<UntypedAgent> builder() {
        return new ParallelAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> ParallelAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ParallelAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public String serviceType() {
        return "Parallel";
    }
}
