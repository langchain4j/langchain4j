package dev.langchain4j.agentic.workflow.impl;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.buildAgentFeatures;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.configureOutput;
import static dev.langchain4j.agentic.declarative.DeclarativeUtil.predicateHelper;
import static dev.langchain4j.agentic.internal.AgentUtil.AGENTIC_SCOPE_ARG_NAME;
import static dev.langchain4j.agentic.internal.AgentUtil.LOOP_COUNTER_ARG_NAME;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.internal.AgentHelpers;
import dev.langchain4j.agentic.internal.HelperMethod;
import dev.langchain4j.agentic.internal.ResolvedParameters;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class LoopAgentServiceImpl<T> extends AbstractServiceBuilder<T, LoopAgentService<T>>
        implements LoopAgentService<T> {

    protected int maxIterations = Integer.MAX_VALUE;
    protected BiPredicate<AgenticScope, Integer> exitCondition = (scope, loopCounter) -> false;
    protected String exitConditionDescription;
    protected boolean testExitAtLoopEnd = false;

    public LoopAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
        configureLoop(AgentHelpers.staticOnly(agentServiceClass));
    }

    @Override
    protected void applyHelperObjects(List<Object> helperObjects) {
        configureLoop(AgentHelpers.instanceOnly(agentServiceClass, helperObjects));
    }

    @Override
    public T build() {
        if (subagents.isEmpty()) {
            throw new AgenticSystemConfigurationException("A loop agent requires at least one subagent.");
        }
        if (maxIterations < 1) {
            throw new AgenticSystemConfigurationException(
                    "A loop agent requires maxIterations to be at least 1, but was " + maxIterations + ".");
        }
        return build(() -> new LoopPlanner(maxIterations, testExitAtLoopEnd, exitCondition, exitConditionDescription));
    }

    public static LoopAgentServiceImpl<UntypedAgent> builder() {
        return new LoopAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> LoopAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new LoopAgentServiceImpl<>(
                agentServiceClass,
                validateAgentClass(agentServiceClass, false, dev.langchain4j.agentic.declarative.LoopAgent.class));
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
    public LoopAgentServiceImpl<T> exitCondition(
            String exitConditionDescription, Predicate<AgenticScope> exitCondition) {
        return exitCondition(exitConditionDescription, (scope, loopCounter) -> exitCondition.test(scope));
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(
            String exitConditionDescription, BiPredicate<AgenticScope, Integer> exitCondition) {
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

    private void configureLoop(AgentHelpers helpers) {
        configureOutput(helpers, this);
        buildAgentFeatures(helpers, this);

        predicateHelper(helpers, ExitCondition.class)
                .map(helper -> {
                    testExitAtLoopEnd(
                            helper.method().getAnnotation(ExitCondition.class).testExitAtLoopEnd());
                    return helper;
                })
                .ifPresent(helper -> this.exitCondition(
                        helper.method().getAnnotation(ExitCondition.class).description(),
                        loopExitConditionPredicate(helpers.agentType(), helper)));
    }

    private static BiPredicate<AgenticScope, Integer> loopExitConditionPredicate(
            Class<?> agentServiceClass, HelperMethod predicateHelper) {
        ResolvedParameters parameters = ResolvedParameters.of(agentServiceClass, predicateHelper);
        Method predicateMethod = predicateHelper.method();
        return (agenticScope, loopCounter) -> {
            try {
                Object[] args = parameters
                        .invocationArguments(
                                agenticScope,
                                Map.of(AGENTIC_SCOPE_ARG_NAME, agenticScope, LOOP_COUNTER_ARG_NAME, loopCounter),
                                true)
                        .positionalArgs();
                return (boolean) predicateMethod.invoke(predicateHelper.target(), args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking exit predicate method: " + predicateMethod.getName(), e);
            }
        };
    }
}
