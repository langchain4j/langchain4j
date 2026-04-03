package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.buildAgentFeatures;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureOutput;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.selectMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

public class ParallelAgentServiceImpl<T> extends AbstractServiceBuilder<T, ParallelAgentService<T>> implements ParallelAgentService<T> {

    public ParallelAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
        configureParallel(agentServiceClass);
    }

    @Override
    public T build() {
        return build(ParallelPlanner::new);
    }

    public static ParallelAgentServiceImpl<UntypedAgent> builder() {
        return new ParallelAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> ParallelAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ParallelAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false, dev.langchain4j.agentic.declarative.ParallelAgent.class));
    }

    @Override
    public String serviceType() {
        return "Parallel";
    }

    private void configureParallel(Class<T> agentServiceClass) {
        configureOutput(agentServiceClass, this);
        buildAgentFeatures(agentServiceClass, this);

        selectMethod(
                agentServiceClass,
                method -> method.isAnnotationPresent(ParallelExecutor.class)
                        && Executor.class.isAssignableFrom(method.getReturnType())
                        && method.getParameterCount() == 0)
                .map(method -> {
                    try {
                        return (Executor) method.invoke(null);
                    } catch (Exception e) {
                        throw new RuntimeException("Error invoking executor method: " + method.getName(), e);
                    }
                })
                .ifPresent(this::executor);
    }
}
