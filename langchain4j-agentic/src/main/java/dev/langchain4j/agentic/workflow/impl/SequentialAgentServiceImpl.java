package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.internal.AgentHelpers;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import java.lang.reflect.Method;
import java.util.List;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.buildAgentFeatures;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureOutput;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

public class SequentialAgentServiceImpl<T> extends AbstractServiceBuilder<T, SequentialAgentService<T>> implements SequentialAgentService<T> {

    public SequentialAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        this(agentServiceClass, agenticMethod, List.of());
    }

    /**
     * @param helperObjects the helper objects of the agent, whose instance helper methods configure it together
     *        with the static methods of its type
     */
    public SequentialAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod, List<Object> helperObjects) {
        super(agentServiceClass, agenticMethod);
        configureSequential(AgentHelpers.of(agentServiceClass, helperObjects));
    }

    @Override
    public T build() {
        return build(SequentialPlanner::new);
    }

    public static SequentialAgentServiceImpl<UntypedAgent> builder() {
        return new SequentialAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> SequentialAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new SequentialAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false, dev.langchain4j.agentic.declarative.SequenceAgent.class));
    }

    @Override
    public String serviceType() {
        return "Sequential";
    }

    private void configureSequential(AgentHelpers helpers) {
        configureOutput(helpers, this);
        buildAgentFeatures(helpers, this);
    }
}
