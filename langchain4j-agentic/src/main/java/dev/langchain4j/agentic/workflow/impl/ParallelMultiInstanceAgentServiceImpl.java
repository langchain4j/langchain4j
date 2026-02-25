package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.workflow.ParallelMultiInstanceAgentService;
import java.lang.reflect.Method;

public class ParallelMultiInstanceAgentServiceImpl<T>
        extends AbstractServiceBuilder<T, ParallelMultiInstanceAgentService<T>>
        implements ParallelMultiInstanceAgentService<T> {

    private String itemsProvider;

    public ParallelMultiInstanceAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    @Override
    public ParallelMultiInstanceAgentService<T> itemsProvider(String itemsProvider) {
        this.itemsProvider = itemsProvider;
        return this;
    }

    @Override
    public T build() {
        return build(() -> new ParallelMultiInstancePlanner(itemsProvider));
    }

    public static ParallelMultiInstanceAgentServiceImpl<UntypedAgent> builder() {
        return new ParallelMultiInstanceAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> ParallelMultiInstanceAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ParallelMultiInstanceAgentServiceImpl<>(
                agentServiceClass,
                validateAgentClass(
                        agentServiceClass,
                        false,
                        dev.langchain4j.agentic.declarative.ParallelMultiInstanceAgent.class));
    }

    @Override
    public String serviceType() {
        return "ParallelMultiInstance";
    }
}
