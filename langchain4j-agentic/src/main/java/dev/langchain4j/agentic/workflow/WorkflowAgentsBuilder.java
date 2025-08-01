package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.UntypedAgent;

public interface WorkflowAgentsBuilder {
    SequentialAgentService<UntypedAgent> sequenceBuilder();
    <T> SequentialAgentService<T> sequenceBuilder(Class<T> agentServiceClass);

    ParallelAgentService<UntypedAgent> parallelBuilder();
    <T> ParallelAgentService<T> parallelBuilder(Class<T> agentServiceClass);

    LoopAgentService<UntypedAgent> loopBuilder();
    <T> LoopAgentService<T> loopBuilder(Class<T> agentServiceClass);

    ConditionalAgentService<UntypedAgent> conditionalBuilder();
    <T> ConditionalAgentService<T> conditionalBuilder(Class<T> agentServiceClass);
}
