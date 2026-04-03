package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.buildAgentFeatures;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureOutput;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.predicateMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.AGENTIC_SCOPE_ARG_NAME;
import static dev.langchain4j.agentic.internal.AgentUtil.LOOP_COUNTER_ARG_NAME;
import static dev.langchain4j.agentic.internal.AgentUtil.agentInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

public class LoopAgentServiceImpl<T> extends AbstractServiceBuilder<T, LoopAgentService<T>> implements LoopAgentService<T> {

    private int maxIterations = Integer.MAX_VALUE;
    private BiPredicate<AgenticScope, Integer> exitCondition = (scope, loopCounter) -> false;
    private String exitConditionDescription;
    private boolean testExitAtLoopEnd = false;

    public LoopAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
        configureLoop(agentServiceClass);
    }

    @Override
    public T build() {
        return build(() -> new LoopPlanner(maxIterations, testExitAtLoopEnd, exitCondition, exitConditionDescription));
    }

    public static LoopAgentServiceImpl<UntypedAgent> builder() {
        return new LoopAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> LoopAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new LoopAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false, dev.langchain4j.agentic.declarative.LoopAgent.class));
    }

    @Override
    public LoopAgentServiceImpl<T> maxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(Predicate<AgenticScope> exitCondition) {
        return exitCondition((scope, loopCounter) -> exitCondition.test(scope));
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(BiPredicate<AgenticScope, Integer> exitCondition) {
        return exitCondition("<unknown>", exitCondition);
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(String exitConditionDescription, Predicate<AgenticScope> exitCondition) {
        return exitCondition(exitConditionDescription, (scope, loopCounter) -> exitCondition.test(scope));
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(String exitConditionDescription, BiPredicate<AgenticScope, Integer> exitCondition) {
        this.exitCondition = exitCondition;
        this.exitConditionDescription = exitConditionDescription;
        return this;
    }

    @Override
    public LoopAgentServiceImpl<T> testExitAtLoopEnd(boolean testExitAtLoopEnd) {
        this.testExitAtLoopEnd = testExitAtLoopEnd;
        return this;
    }

    @Override
    public String serviceType() {
        return "Loop";
    }

    private void configureLoop(Class<T> agentServiceClass) {
        configureOutput(agentServiceClass, this);
        buildAgentFeatures(agentServiceClass, this);

        predicateMethod(agentServiceClass, method -> method.isAnnotationPresent(ExitCondition.class))
                .map(method -> {
                    testExitAtLoopEnd(
                            method.getAnnotation(ExitCondition.class).testExitAtLoopEnd());
                    return method;
                })
                .ifPresent(method -> this.exitCondition(
                        method.getAnnotation(ExitCondition.class).description(), loopExitConditionPredicate(method)));
    }

    private static BiPredicate<AgenticScope, Integer> loopExitConditionPredicate(Method predicateMethod) {
        List<AgentArgument> agentArguments = argumentsFromMethod(predicateMethod);
        return (agenticScope, loopCounter) -> {
            try {
                Object[] args = agentInvocationArguments(
                        agenticScope,
                        agentArguments,
                        Map.of(AGENTIC_SCOPE_ARG_NAME, agenticScope, LOOP_COUNTER_ARG_NAME, loopCounter))
                        .positionalArgs();
                return (boolean) predicateMethod.invoke(null, args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking exit predicate method: " + predicateMethod.getName(), e);
            }
        };
    }
}
