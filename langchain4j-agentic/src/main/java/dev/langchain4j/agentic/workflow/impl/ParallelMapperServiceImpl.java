package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.workflow.ParallelMapperService;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

public class ParallelMapperServiceImpl<T>
        extends AbstractServiceBuilder<T, ParallelMapperService<T>>
        implements ParallelMapperService<T> {

    public static final String SERVICE_TYPE = "ParallelMapper";

    private String itemsProvider;

    public ParallelMapperServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    @Override
    public ParallelMapperService<T> itemsProvider(String itemsProvider) {
        this.itemsProvider = itemsProvider;
        return this;
    }

    @Override
    public T build() {
        boolean isArrayResult = isArrayResult();
        Class<? extends Object[]> arrayclass = isArrayResult ? (Class<? extends Object[]>) agenticMethod.getReturnType() : null;
        return build(() -> new ParallelMapperPlanner(itemsProvider(), isArrayResult, arrayclass));
    }

    private String itemsProvider() {
        if (itemsProvider != null && !itemsProvider.isBlank()) {
            return itemsProvider;
        }
        if (agenticMethod == null) {
            throw new AgenticSystemConfigurationException(
                    "It is mandatory to declare an itemsProvider using an untyped parallel mapper.");
        }

        AgentArgument itemsArgument = null;
        List<AgentArgument> agentArguments = argumentsFromMethod(agenticMethod);
        for (AgentArgument agentArgument : agentArguments) {
            if (Collection.class.isAssignableFrom(agentArgument.rawType()) || agentArgument.rawType().isArray()) {
                if (itemsArgument != null) {
                    throw new AgenticSystemConfigurationException(
                            "Multiple collection arguments found in class " + agentServiceClass.getName() +
                                    ", please disambiguate specifying the itemsProvider.");
                }
                itemsArgument = agentArgument;
            }
        }

        if (itemsArgument == null) {
            throw new AgenticSystemConfigurationException(
                    "Class " + agentServiceClass.getName() + " doesn't have a collection argument on which to iterate.");
        }
        return itemsArgument.name();
    }

    private boolean isArrayResult() {
        if (agenticMethod == null) {
            return false;
        }
        Class returnType = agenticMethod.getReturnType();
        if (returnType.isArray()) {
            return true;
        }
        if (Collection.class.isAssignableFrom(returnType)) {
            return false;
        }
        throw new AgenticSystemConfigurationException(
                "The return type of " + agentServiceClass.getName() + " must be either a collection or an array.");
    }

    public static ParallelMapperServiceImpl<UntypedAgent> builder() {
        return new ParallelMapperServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> ParallelMapperServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ParallelMapperServiceImpl<>(
                agentServiceClass,
                validateAgentClass(
                        agentServiceClass,
                        false,
                        ParallelMapperAgent.class));
    }

    @Override
    public String serviceType() {
        return SERVICE_TYPE;
    }
}
