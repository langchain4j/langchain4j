package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import java.lang.reflect.Method;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

public class SequentialAgentServiceImpl<T> extends AbstractServiceBuilder<T, SequentialAgentService<T>> implements SequentialAgentService<T> {

    public SequentialAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    @Override
    public T build() {
        return build(SequentialPlanner::new);
    }

    public static SequentialAgentServiceImpl<UntypedAgent> builder() {
        return new SequentialAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> SequentialAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new SequentialAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public String serviceType() {
        return "Sequential";
    }
}
