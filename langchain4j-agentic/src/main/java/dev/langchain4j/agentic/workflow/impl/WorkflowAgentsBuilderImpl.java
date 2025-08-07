package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import dev.langchain4j.agentic.workflow.WorkflowAgentsBuilder;

public enum WorkflowAgentsBuilderImpl implements WorkflowAgentsBuilder {

    INSTANCE;

    @Override
    public SequentialAgentService<UntypedAgent> sequenceBuilder() {
        return SequentialAgentServiceImpl.builder();
    }

    @Override
    public <T> SequentialAgentService<T> sequenceBuilder(final Class<T> agentServiceClass) {
        return SequentialAgentServiceImpl.builder(agentServiceClass);
    }

    @Override
    public ParallelAgentService<UntypedAgent> parallelBuilder() {
        return ParallelAgentServiceImpl.builder();
    }

    @Override
    public <T> ParallelAgentService<T> parallelBuilder(final Class<T> agentServiceClass) {
        return ParallelAgentServiceImpl.builder(agentServiceClass);
    }

    @Override
    public LoopAgentService<UntypedAgent> loopBuilder() {
        return LoopAgentServiceImpl.builder();
    }

    @Override
    public <T> LoopAgentService<T> loopBuilder(final Class<T> agentServiceClass) {
        return LoopAgentServiceImpl.builder(agentServiceClass);
    }

    @Override
    public ConditionalAgentService<UntypedAgent> conditionalBuilder() {
        return ConditionalAgentServiceImpl.builder();
    }

    @Override
    public <T> ConditionalAgentService<T> conditionalBuilder(final Class<T> agentServiceClass) {
        return ConditionalAgentServiceImpl.builder(agentServiceClass);
    }
}
